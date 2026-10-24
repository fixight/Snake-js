#pragma once
#include <linux/input.h>
#include <algorithm>
#include <map>
#include <vector>
#include <set>
#include <sstream>
#include <string>
#include <stdexcept>
#include "mapping_format.h"

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
struct HatRule { int source,value,neutral,target; };
inline int hatAxis(int semantic) { return semantic<15?ABS_HAT0Y:ABS_HAT0X; }
struct Mapping {
    std::vector<HatRule> hats;
    std::map<int,int> keys{{306,1}}; // Only Circle is reported by user; other buttons must be learned.
    std::map<int,int> axes{{0,0},{1,1},{2,3},{5,4},{3,2},{4,5},{16,16},{17,17}};
    std::string dump() const {
        std::ostringstream s;
        for(auto [a,b]: keys) s<<"k "<<a<<" "<<b<<"\n";
        for(auto [a,b]: axes) s<<"a "<<a<<" "<<b<<"\n";
        for(auto h:hats) s<<"h "<<h.source<<" "<<h.value<<" "<<h.neutral<<" "<<h.target<<"\n";
        return s.str();
    }
    void parse(const std::string& text) {
        auto records=parseMapRecords(text);if(records.empty())return;
        std::map<int,int> k,a;std::vector<HatRule> h;
        for(auto record:records) {
            char t=record.type;int from=record.source,to=record.value;
            if(t=='h') {
                int neutral=record.neutral,target=record.target;
                if(from<0||from>ABS_MAX||target<13||target>16||to==neutral)formatError(record.line,"недопустимое направление HAT");
                for(auto old:h)if(old.source==from&&(old.value==to||old.neutral!=neutral||hatAxis(old.target)!=hatAxis(target)))formatError(record.line,"конфликт направлений одной HAT-оси");
                h.push_back({from,to,neutral,target});
            } else if(t=='k'&&from>=0&&from<=KEY_MAX&&to>=0&&to<17)k[from]=to;
            else if(t=='a'&&from>=0&&from<=ABS_MAX&&axisAllowed(to))a[from]=to;
            else formatError(record.line,"код или назначение вне допустимого диапазона");
        }
        for(auto x=a.begin();x!=a.end();++x) for(auto y=std::next(x);y!=a.end();++y)
            if(x->second==y->second) throw std::runtime_error("Раскладка: две физические оси назначены на одну выходную ось");
        for(auto rule:h)for(auto axis:a)if(hatAxis(rule.target)==axis.second)throw std::runtime_error("Раскладка: HAT-направление конфликтует с обычным назначением оси");
        keys=k; axes=a;hats=h;
    }
    void removeTarget(int target) {
        for(auto i=keys.begin();i!=keys.end();)if(i->second==target)i=keys.erase(i);else ++i;
        hats.erase(std::remove_if(hats.begin(),hats.end(),[&](const HatRule& h){return h.target==target;}),hats.end());
        if(target>=13){for(auto i=axes.begin();i!=axes.end();)if(i->second==hatAxis(target))i=axes.erase(i);else ++i;}
    }
    void learnKey(int source,int target) { removeTarget(target);keys[source]=target; }
    void learnHat(int source,int value,int neutral,int target) {
        // One physical HAT axis represents one logical axis; discard an accidental earlier cross-axis assignment.
        hats.erase(std::remove_if(hats.begin(),hats.end(),[&](const HatRule& h){return h.source==source&&(h.value==value||hatAxis(h.target)!=hatAxis(target));}),hats.end());
        removeTarget(target);hats.push_back({source,value,neutral,target});
    }
    input_event convert(input_event e, int profile, const input_absinfo& range) const {
        if(e.type==EV_KEY) {
            auto i=keys.find(e.code); if(i==keys.end()) { e.type=EV_MSC; return e; }
            if(i->second>=13) { e.type=EV_ABS; e.code=i->second<15?ABS_HAT0Y:ABS_HAT0X; e.value=e.value==0?0:((i->second==13||i->second==15)?-1:1); }
            else if(profile==0 && (i->second==6 || i->second==7)) { e.type=EV_ABS;e.code=i->second==6?ABS_Z:ABS_RZ;e.value=e.value?255:0; }
            else e.code=button(i->second,profile);
        } else if(e.type==EV_ABS) {
            for(auto h:hats)if(h.source==e.code) {
                int axis=hatAxis(h.target),value=0;
                for(auto rule:hats)if(rule.source==e.code&&rule.value==e.value)value=(rule.target==13||rule.target==15)?-1:1;
                e.code=axis;e.value=value;return e;
            }
            auto i=axes.find(e.code); if(i==axes.end()) {e.type=EV_MSC; return e;}
            e.code=i->second; e.value=scale(e.value,range.minimum,range.maximum,e.code);
        }
        return e;
    }
};
}

namespace dsfix {
// Combine independent button/HAT sources so releasing one direction cannot cancel another held direction.
struct Remapper {
    std::map<int,int> directions;
    void reset() { directions.clear(); }
    input_event convert(const Mapping& m,input_event e,int profile,const input_absinfo& range) {
        int target=-1,axis=-1,source=e.code;
        if(e.type==EV_KEY) {
            auto k=m.keys.find(e.code);
            if(k!=m.keys.end()&&k->second>=13) {axis=hatAxis(k->second);if(e.value)target=k->second;}
        } else if(e.type==EV_ABS) {
            source=KEY_MAX+1+e.code;
            for(auto h:m.hats)if(h.source==e.code){axis=hatAxis(h.target);if(h.value==e.value)target=h.target;}
        }
        if(axis<0)return m.convert(e,profile,range);
        directions[source]=target;
        bool negative=false,positive=false;
        for(auto pair:directions)if(pair.second>=13&&hatAxis(pair.second)==axis) {
            if(pair.second==13||pair.second==15)negative=true;else positive=true;
        }
        e.type=EV_ABS;e.code=axis;e.value=int(positive)-int(negative);return e;
    }
};
}
