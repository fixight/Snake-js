#include "../app/src/main/cpp/mapping.h"
#include <cassert>
#include <iostream>
using namespace dsfix;
input_event event(int type,int code,int value){input_event e{};e.type=type;e.code=code;e.value=value;return e;}
int main(){
 Mapping m; input_absinfo range{};range.minimum=0;range.maximum=255;
 auto e=m.convert(event(EV_ABS,ABS_Z,255),0,range);assert(e.type==EV_ABS&&e.code==ABS_RX&&e.value==32767);
 e=m.convert(event(EV_ABS,ABS_RZ,0),0,range);assert(e.code==ABS_RY&&e.value==-32767);
 e=m.convert(event(EV_ABS,ABS_RX,255),0,range);assert(e.code==ABS_Z&&e.value==255);
 e=m.convert(event(EV_ABS,ABS_RY,0),0,range);assert(e.code==ABS_RZ&&e.value==0);
 e=m.convert(event(EV_ABS,ABS_X,128),0,range);assert(e.value==0);
 e=m.convert(event(EV_KEY,BTN_C,1),0,range);assert(e.code==BTN_EAST&&e.value==1);
 e=m.convert(event(EV_KEY,BTN_C,0),0,range);assert(e.code==BTN_EAST&&e.value==0);
 e=m.convert(event(EV_KEY,304,1),0,range);assert(e.type==EV_MSC); // Unknown buttons never guessed.
 m.parse("k 304 0\nk 306 1\nk 307 2\nk 544 13\na 2 3\na 3 2\n");
 assert(m.convert(event(EV_KEY,307,1),0,range).code==307);
 assert(m.convert(event(EV_KEY,307,1),1,range).code==308);
 e=m.convert(event(EV_KEY,544,1),0,range);assert(e.type==EV_ABS&&e.code==ABS_HAT0Y&&e.value==-1);
 e=m.convert(event(EV_KEY,544,0),0,range);assert(e.value==0);
 auto syn=event(EV_SYN,SYN_REPORT,0);e=m.convert(syn,0,range);assert(e.type==EV_SYN&&e.code==SYN_REPORT);
 m.keys[312]=6; e=m.convert(event(EV_KEY,312,1),0,range); assert(e.type==EV_ABS&&e.code==ABS_Z&&e.value==255);
 e=m.convert(event(EV_KEY,312,1),1,range);assert(e.type==EV_KEY&&e.code==312);
 input_event sequence[]={event(EV_ABS,2,255),event(EV_KEY,306,1),event(EV_SYN,SYN_REPORT,0),event(EV_KEY,306,0),event(EV_SYN,SYN_REPORT,0)};
 int reports=0;for(auto frame:sequence){auto mapped=m.convert(frame,0,range);if(mapped.type==EV_SYN)++reports;}assert(reports==2);
 Mapping n;n.parse(m.dump());assert(n.dump()==m.dump());
 bool failed=false;try{n.parse("a 0 3\na 1 3\n");}catch(...){failed=true;}assert(failed);
 failed=false;try{n.parse("k 1 99\n");}catch(...){failed=true;}assert(failed);
 assert(scale(-10,0,255,2)==0&&scale(300,0,255,2)==255);
 assert(scale(5,5,5,0)==0);
 std::cout<<"PASS: axis remap, normalization, deadzone, Circle, unknown keys, profiles, D-pad, SYN_REPORT, serialization, invalid mappings\n";
}
