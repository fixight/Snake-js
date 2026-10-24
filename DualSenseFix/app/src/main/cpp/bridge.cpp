#include "mapping.h"
#include <jni.h>
#include <linux/uinput.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <poll.h>
#include <dirent.h>
#include <atomic>
#include <chrono>
#include <thread>
#include <mutex>
#include <deque>
#include <fstream>
#include <cstring>
#include <vector>

namespace {
using namespace dsfix;
std::mutex mu;
std::deque<std::string> logs;
std::thread worker;
std::atomic<bool> running{false}, test{false};
std::atomic<long long> lease{0};
Mapping mapping;
int learnType=0,learnTarget=0;
std::map<int,int> currentAxes, baseline;
std::atomic<bool> diagnosticMode{false};
std::string status="Stopped", device="Disconnected";
long long now() { return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
void log(const std::string& s) {std::lock_guard<std::mutex> l(mu); logs.push_back(s); if(logs.size()>180) logs.pop_front();}
void state(const std::string& s) {std::lock_guard<std::mutex> l(mu);status=s;}
struct Fd {int fd=-1; explicit Fd(int n=-1):fd(n){} ~Fd(){if(fd>=0)close(fd);} Fd(const Fd&)=delete;};
struct Output : Fd { bool created=false; ~Output(){if(created)ioctl(fd,UI_DEV_DESTROY);} };
void check(bool ok,const char* text) {if(!ok)throw std::runtime_error(std::string(text)+": "+strerror(errno));}
void emit(int fd, const input_event& event) {
    const char* p=reinterpret_cast<const char*>(&event);size_t left=sizeof(event);
    while(left) {ssize_t n=write(fd,p,left); if(n<0&&errno==EINTR)continue;check(n>0,"uinput write");left-=n;p+=n;}
}
void send(int fd,int type,int code,int value) {input_event e{};e.type=type;e.code=code;e.value=value;emit(fd,e);}
std::map<std::string,std::string> readLayout(const std::string& path) {
    std::ifstream f(path);std::string line;std::map<std::string,std::string> out;
    while(std::getline(f,line)) {std::istringstream s(line);std::string t,c,v;if(s>>t>>c>>v && (t=="key"||t=="axis")) {
        try {out[t+std::to_string(std::stoi(c,nullptr,0))]=v;}catch(const std::exception&){}
    }}return out;
}
void verifyLayout(int profile) {
    std::string file=profile==1?"Vendor_054c_Product_0ce6.kl":"Vendor_045e_Product_028e.kl";
    std::map<std::string,std::string> layout;
    for(const char* dir:{"/odm/usr/keylayout/","/vendor/usr/keylayout/","/system/usr/keylayout/"}) {
        auto m=readLayout(std::string(dir)+file); if(!m.empty()){layout=m;log("Layout preflight: "+std::string(dir)+file);break;}
    }
    check(!layout.empty(),"No known output keylayout; use diagnostics");
    const char* names[]={"BUTTON_A","BUTTON_B","BUTTON_X","BUTTON_Y","BUTTON_L1","BUTTON_R1","BUTTON_L2","BUTTON_R2","BUTTON_SELECT","BUTTON_START","BUTTON_MODE","BUTTON_THUMBL","BUTTON_THUMBR"};
    
    for(int i=0;i<13;i++) {
        if(profile==0 && (i==6||i==7))continue;
        std::string k="key"+std::to_string(button(i,profile));
        std::string actual=layout[k];
        if(actual!=names[i])throw std::runtime_error("Output keylayout mismatch: "+k+"="+actual);
    }
    const int axes[]={0,1,2,3,4,5,16,17};const char* values[]={"X","Y","LTRIGGER","Z","RZ","RTRIGGER","HAT_X","HAT_Y"};
    for(int i=0;i<8;i++)if(layout["axis"+std::to_string(axes[i])]!=values[i])throw std::runtime_error("Output axis keylayout mismatch");
}
void create(Output& o,int profile) {
    verifyLayout(profile);
    o.fd=open("/dev/uinput",O_WRONLY|O_CLOEXEC);check(o.fd>=0,"open /dev/uinput");
    check(ioctl(o.fd,UI_SET_EVBIT,EV_KEY)>=0,"EV_KEY");
    check(ioctl(o.fd,UI_SET_EVBIT,EV_ABS)>=0,"EV_ABS");
    for(int i=0;i<13;i++)if(profile==1 || (i!=6&&i!=7))check(ioctl(o.fd,UI_SET_KEYBIT,button(i,profile))>=0,"key capability");
    uinput_user_dev d{};
    snprintf(d.name,sizeof(d.name),"DSFIX Virtual %s",profile==1?"DualSense":"Xbox");
    d.id.bustype=BUS_USB;d.id.vendor=profile==1?0x054c:0x045e;d.id.product=profile==1?0x0ce6:0x028e;d.id.version=0x100;
    for(int a:{0,1,2,3,4,5,16,17}) {
        check(ioctl(o.fd,UI_SET_ABSBIT,a)>=0,"axis capability");
        d.absmin[a]=a>=16?-1:((a==2||a==5)?0:-32767);
        d.absmax[a]=a>=16?1:((a==2||a==5)?255:32767);
        d.absflat[a]=a<6&&a!=2&&a!=5?4096:0;
    }
    const char* phys="DSFIX-bridge";check(ioctl(o.fd,UI_SET_PHYS,phys)>=0,"UI_SET_PHYS");
    check(write(o.fd,&d,sizeof(d))==sizeof(d),"uinput setup");
    check(ioctl(o.fd,UI_DEV_CREATE)>=0,"UI_DEV_CREATE");o.created=true;
    for(int a:{0,1,2,3,4,5,16,17})send(o.fd,EV_ABS,a,0);
    send(o.fd,EV_SYN,SYN_REPORT,0);
    log("Virtual device created. Confirm KeyLayoutFile/Sources/Motion Ranges with Input Report.");
}
bool bit(const unsigned long* data,int n){return data[n/(8*sizeof(long))]&(1UL<<(n%(8*sizeof(long))));}
int discover(std::string& path) {
    DIR* d=opendir("/dev/input");if(!d){log("opendir /dev/input: "+std::string(strerror(errno)));return -1;}
    int result=-1;
    while(auto* ent=readdir(d)) {
        if(strncmp(ent->d_name,"event",5))continue;
        std::string p="/dev/input/"+std::string(ent->d_name);Fd fd(open(p.c_str(),O_RDONLY|O_NONBLOCK|O_CLOEXEC));if(fd.fd<0)continue;
        input_id id{};char name[256]{};ioctl(fd.fd,EVIOCGID,&id);ioctl(fd.fd,EVIOCGNAME(sizeof(name)),name);
        if(id.vendor!=0x054c||id.product!=0x0ce6||strncmp(name,"DSFIX",5)==0)continue;
        unsigned long keys[(KEY_MAX+8*sizeof(long))/(8*sizeof(long))]{};
        input_absinfo x{},y{};
        if(ioctl(fd.fd,EVIOCGBIT(EV_KEY,sizeof(keys)),keys)<0 || !bit(keys,BTN_GAMEPAD) || ioctl(fd.fd,EVIOCGABS(ABS_X),&x)<0 || ioctl(fd.fd,EVIOCGABS(ABS_Y),&y)<0) continue;
        path=p;result=fd.fd;fd.fd=-1;
        log("Physical "+p+" name="+name+" bus="+std::to_string(id.bustype)+" VID=054c PID=0ce6");break;
    }
    closedir(d);return result;
}
void neutral(int fd,int profile){for(int i=0;i<13;i++)send(fd,EV_KEY,button(i,profile),0);for(int a:{0,1,2,3,4,5,16,17})send(fd,EV_ABS,a,0);send(fd,EV_SYN,SYN_REPORT,0);}
void syncState(int source,int target,int profile,const std::map<int,input_absinfo>& ranges) {
    unsigned long bits[(KEY_MAX+8*sizeof(long))/(8*sizeof(long))]{};
    check(ioctl(source,EVIOCGKEY(sizeof(bits)),bits)>=0,"EVIOCGKEY");
    Mapping m;{std::lock_guard<std::mutex> l(mu);m=mapping;}
    for(auto [k,s]:m.keys) { input_event event{}; event.type=EV_KEY;event.code=k;event.value=bit(bits,k)?1:0;input_absinfo r{};auto v=m.convert(event,profile,r);emit(target,v); }
    for(auto [a,b]:m.axes) {auto r=ranges.find(a);if(r==ranges.end())continue;input_absinfo current{};check(ioctl(source,EVIOCGABS(a),&current)>=0,"axis state");send(target,EV_ABS,b,scale(current.value,current.minimum,current.maximum,b));}
    send(target,EV_SYN,SYN_REPORT,0);
}
void run(int profile,bool grab,bool diagnostic) {
    try {
        Output out;
        // Never forward physical input without successful exclusive grab.
        diagnosticMode=diagnostic;
        if(!diagnostic && grab)create(out,profile);
        else {diagnostic=true;diagnosticMode=true;log("Safe diagnostics: no grab, no forwarding.");}
        while(running && now()-lease<5000) {
            std::string path;Fd physical(discover(path));
            if(physical.fd<0) {state("Waiting for DualSense; virtual="+std::string(out.created?"ready":"off"));
                for(int i=0;i<10&&running&&now()-lease<5000;i++) {
                    if(test.exchange(false)&&out.created){send(out.fd,EV_KEY,button(0,profile),1);send(out.fd,EV_SYN,SYN_REPORT,0);send(out.fd,EV_KEY,button(0,profile),0);send(out.fd,EV_SYN,SYN_REPORT,0);log("Synthetic A/Cross sent");}
                    poll(nullptr,0,100);
                }continue;}
            {std::lock_guard<std::mutex> l(mu);device=path;}
            std::map<int,input_absinfo> ranges;std::map<int,int> previous;
            for(int i=0;i<=ABS_MAX;i++){input_absinfo r{};if(ioctl(physical.fd,EVIOCGABS(i),&r)>=0){ranges[i]=r;previous[i]=r.value;{std::lock_guard<std::mutex> l(mu);currentAxes[i]=r.value;}log("ABS "+std::to_string(i)+" min="+std::to_string(r.minimum)+" max="+std::to_string(r.maximum)+" current="+std::to_string(r.value));}}
            bool grabbed=false;
            if(!diagnostic) {
                if(ioctl(physical.fd,EVIOCGRAB,1)<0){log("EVIOCGRAB denied: "+std::string(strerror(errno))+". Diagnostics only; forwarding disabled.");diagnostic=true;diagnosticMode=true;if(out.created){ioctl(out.fd,UI_DEV_DESTROY);out.created=false;}}
                else {grabbed=true;log("EVIOCGRAB acquired");syncState(physical.fd,out.fd,profile,ranges);}
            }
            state(diagnostic?"Diagnostics: physical input remains enabled":"Bridge active; physical grabbed; virtual ready");
            // physical FD closure releases EVIOCGRAB even after exceptions/process death.
            bool dropped=false;
            while(running&&now()-lease<5000) {
                if(test.exchange(false)&&out.created){send(out.fd,EV_KEY,button(0,profile),1);send(out.fd,EV_SYN,SYN_REPORT,0);send(out.fd,EV_KEY,button(0,profile),0);send(out.fd,EV_SYN,SYN_REPORT,0);log("Synthetic A/Cross sent");}
                pollfd p{physical.fd,POLLIN,0};int ready=poll(&p,1,100);if(ready<0&&errno==EINTR)continue;check(ready>=0,"poll");if(!ready)continue;if(p.revents&(POLLHUP|POLLERR|POLLNVAL))break;
                input_event events[64];ssize_t n=read(physical.fd,events,sizeof(events));if(n<0&&(errno==EAGAIN||errno==EINTR))continue;if(n<=0)break;check(n%sizeof(input_event)==0,"partial input_event");
                for(size_t i=0;i<size_t(n)/sizeof(input_event);i++) {
                    auto e=events[i];
                    if(e.type==EV_SYN&&e.code==SYN_DROPPED){dropped=true;log("SYN_DROPPED; resync at next frame");continue;}
                    if(dropped){if(e.type==EV_SYN&&e.code==SYN_REPORT){dropped=false;if(out.created)syncState(physical.fd,out.fd,profile,ranges);}continue;}
                    if(e.type==EV_KEY||e.type==EV_ABS) {
                        log("ev type="+std::to_string(e.type)+" code="+std::to_string(e.code)+" value="+std::to_string(e.value));
                        std::lock_guard<std::mutex> l(mu);
                        if(e.type==EV_ABS)currentAxes[e.code]=e.value;
                        if(learnType==e.type&&((e.type==EV_KEY&&e.value==1)||(e.type==EV_ABS&&ranges.count(e.code)&&std::abs(e.value-baseline[e.code])>=std::max(1,(ranges[e.code].maximum-ranges[e.code].minimum)/5)))) {
                            auto& m=e.type==EV_KEY?mapping.keys:mapping.axes;
                            for(auto it=m.begin();it!=m.end();)if(it->second==learnTarget)it=m.erase(it);else ++it;
                            m[e.code]=learnTarget;learnType=0;
                            logs.push_back("LEARNED code="+std::to_string(e.code)+" target="+std::to_string(learnTarget));
                        }
                    }
                    if(out.created) {
                        Mapping m;{std::lock_guard<std::mutex> l(mu);m=mapping;}
                        auto r=ranges.find(e.code);input_absinfo range{};if(r!=ranges.end())range=r->second;
                        auto converted=m.convert(e,profile,range);
                        if(converted.type==EV_KEY||converted.type==EV_ABS||(converted.type==EV_SYN&&converted.code==SYN_REPORT))emit(out.fd,converted);
                    }
                }
            }
            if(grabbed)ioctl(physical.fd,EVIOCGRAB,0);
            if(out.created)neutral(out.fd,profile);
            {std::lock_guard<std::mutex> l(mu);device="Disconnected";}
            log("Physical released/disconnected");
        }
        if(running)log("UI heartbeat lost: stopped by 5s watchdog");
    } catch(const std::exception& e){log(std::string("ERROR: ")+e.what());}
    state("Stopped; physical released; virtual destroyed");running=false;
}
void stop(){running=false;if(worker.joinable())worker.join();}
}
extern "C" JNIEXPORT void JNICALL Java_dev_dualsense_fix_BridgeService_nativeStart(JNIEnv* env,jobject,jint profile,jboolean grab,jboolean diagnostic,jstring config) {
    stop();const char* raw=env->GetStringUTFChars(config,nullptr);std::string text(raw);env->ReleaseStringUTFChars(config,raw);
    try {Mapping next;next.parse(text);{std::lock_guard<std::mutex> l(mu);mapping=next;learnType=0;logs.clear();}lease=now();test=false;running=true;worker=std::thread(run,profile==1?1:0,bool(grab),bool(diagnostic));}
    catch(const std::exception& e){log(std::string("ERROR: ")+e.what());}
}
extern "C" JNIEXPORT void JNICALL Java_dev_dualsense_fix_BridgeService_nativeStop(JNIEnv*,jobject){stop();}
extern "C" JNIEXPORT void JNICALL Java_dev_dualsense_fix_BridgeService_nativeHeartbeat(JNIEnv*,jobject){lease=now();}
extern "C" JNIEXPORT void JNICALL Java_dev_dualsense_fix_BridgeService_nativeTest(JNIEnv*,jobject){test=true;}
extern "C" JNIEXPORT void JNICALL Java_dev_dualsense_fix_BridgeService_nativeLearn(JNIEnv*,jobject,jint type,jint target){std::lock_guard<std::mutex> l(mu);if(running&&diagnosticMode&&((type==EV_KEY&&target>=0&&target<17)||(type==EV_ABS&&axisAllowed(target)))){learnType=type;learnTarget=target;baseline=currentAxes;}}
extern "C" JNIEXPORT jstring JNICALL Java_dev_dualsense_fix_BridgeService_nativeMappings(JNIEnv* e,jobject){std::lock_guard<std::mutex> l(mu);return e->NewStringUTF(mapping.dump().c_str());}
extern "C" JNIEXPORT jstring JNICALL Java_dev_dualsense_fix_BridgeService_nativeSnapshot(JNIEnv* e,jobject){std::lock_guard<std::mutex> l(mu);std::string s=status+"\nDualSense: "+device+"\n";for(const auto& l:logs)s+=l+"\n";return e->NewStringUTF(s.c_str());}
