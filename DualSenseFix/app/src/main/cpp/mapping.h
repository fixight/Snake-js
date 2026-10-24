#pragma once
#include <linux/input.h>
#include <algorithm>
#include <map>
#include <sstream>
#include <string>
#include <stdexcept>

namespace dsfix {
// Semantic button indexes: A,B,X,Y,L1,R1,L2,R2,Select,Start,Mode,L3,R3.
inline int button(int semantic, int profile) {
    static const int xbox[] = {304,305,307,308,310,311,312,313,314,315,316,317,318};
    static const int sony[] = {304,305,308,307,310,311,312,313,314,315,316,317,318};
    return (profile == 1 ? sony : xbox)[semantic];
}
inline bool axisAllowed(int c) { return c==0 || c==1 || c==2 || c==3 || c==4 || c==5 || c==16 || c==17; }
inline int scale(int value, int low, int high, int axis) {
    if (high <= low) return 0;
    value=std::clamp(value,low,high);
    if(axis==16 || axis==17) return value;
    long long range=high-low, x=value-low;
    if(axis==2 || axis==5) return int(x*255/range);
    int result=int(x*65534/range-32767);
    return std::abs(result)<4096 ? 0 : result;
}
struct Mapping {
    std::map<int,int> keys{{306,1}}; // Only Circle is reported by user; other buttons must be learned.
    std::map<int,int> axes{{0,0},{1,1},{2,3},{5,4},{3,2},{4,5},{16,16},{17,17}};
    std::string dump() const {
        std::ostringstream s;
        for(auto [a,b]: keys) s<<"k "<<a<<" "<<b<<"\n";
        for(auto [a,b]: axes) s<<"a "<<a<<" "<<b<<"\n";
        return s.str();
    }
    void parse(const std::string& text) {
        if(text.empty()) return;
        std::map<int,int> k,a; std::istringstream s(text); std::string line;
        while(std::getline(s,line)) {
            if(line.empty()) continue;
            std::istringstream row(line); char t; int from,to; std::string extra;
            if(!(row>>t>>from>>to) || row>>extra) throw std::runtime_error("Invalid mapping line");
            if(t=='k' && from>=0 && from<=KEY_MAX && to>=0 && to<17) k[from]=to;
            else if(t=='a' && from>=0 && from<=ABS_MAX && axisAllowed(to)) a[from]=to;
            else throw std::runtime_error("Invalid mapping range");
        }
        for(auto x=a.begin();x!=a.end();++x) for(auto y=std::next(x);y!=a.end();++y)
            if(x->second==y->second) throw std::runtime_error("Duplicate output axis");
        keys=k; axes=a;
    }
    input_event convert(input_event e, int profile, const input_absinfo& range) const {
        if(e.type==EV_KEY) {
            auto i=keys.find(e.code); if(i==keys.end()) { e.type=EV_MSC; return e; }
            if(i->second>=13) { e.type=EV_ABS; e.code=i->second<15?ABS_HAT0Y:ABS_HAT0X; e.value=e.value==0?0:((i->second==13||i->second==15)?-1:1); }
            else if(profile==0 && (i->second==6 || i->second==7)) { e.type=EV_ABS;e.code=i->second==6?ABS_Z:ABS_RZ;e.value=e.value?255:0; }
            else e.code=button(i->second,profile);
        } else if(e.type==EV_ABS) {
            auto i=axes.find(e.code); if(i==axes.end()) {e.type=EV_MSC; return e;}
            e.code=i->second; e.value=scale(e.value,range.minimum,range.maximum,e.code);
        }
        return e;
    }
};
}
