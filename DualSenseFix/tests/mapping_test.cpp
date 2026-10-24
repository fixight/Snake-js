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
 // Learn each HAT direction, including a reversed X axis.
 Mapping hats;hats.learnHat(16,-1,0,16);hats.learnHat(16,1,0,15);
 hats.learnHat(17,-1,0,13);hats.learnHat(17,1,0,14);
 assert(!hats.axes.count(16)&&!hats.axes.count(17));
 Mapping reloaded;reloaded.parse(hats.dump());assert(hats.dump()==reloaded.dump());
 const int cases[][4]={{16,-1,16,1},{16,1,16,-1},{17,-1,17,-1},{17,1,17,1}};
 for(const auto& c:cases){Remapper each;auto v=each.convert(reloaded,event(EV_ABS,c[0],c[1]),0,range);assert(v.type==EV_ABS&&v.code==c[2]&&v.value==c[3]);v=each.convert(reloaded,event(EV_ABS,c[0],0),0,range);assert(v.value==0);}
 Remapper remap;
 e=remap.convert(reloaded,event(EV_ABS,16,-1),0,range);assert(e.code==ABS_HAT0X&&e.value==1);
 e=remap.convert(reloaded,event(EV_ABS,17,-1),0,range);assert(e.code==ABS_HAT0Y&&e.value==-1);
 e=remap.convert(reloaded,event(EV_ABS,16,0),0,range);assert(e.code==ABS_HAT0X&&e.value==0);
 e=remap.convert(reloaded,event(EV_ABS,17,0),0,range);assert(e.code==ABS_HAT0Y&&e.value==0);
 Mapping digital;digital.learnKey(544,13);digital.learnKey(545,14);digital.learnKey(546,15);digital.learnKey(547,16);
 assert(!digital.axes.count(16)&&!digital.axes.count(17));
 Remapper d;
 e=d.convert(digital,event(EV_KEY,546,1),0,range);assert(e.code==ABS_HAT0X&&e.value==-1);
 e=d.convert(digital,event(EV_KEY,547,1),0,range);assert(e.value==0);
 e=d.convert(digital,event(EV_KEY,547,0),0,range);assert(e.value==-1); // Left remains held.
 e=d.convert(digital,event(EV_KEY,546,0),0,range);assert(e.value==0);
 // Mixed HAT/button sources also preserve a still-held direction.
 Mapping mixed;mixed.learnHat(16,-1,0,15);mixed.learnKey(547,16);Remapper mix;
 mix.convert(mixed,event(EV_ABS,16,-1),0,range);
 e=mix.convert(mixed,event(EV_KEY,547,1),0,range);assert(e.value==0);
 e=mix.convert(mixed,event(EV_ABS,16,0),0,range);assert(e.value==1);
 e=mix.convert(mixed,event(EV_KEY,547,0),0,range);assert(e.value==0);
 // Nonzero hardware neutral is serialized and released correctly.
 Mapping offset;offset.learnHat(16,2,1,16);Mapping offset2;offset2.parse(offset.dump());Remapper off;
 e=off.convert(offset2,event(EV_ABS,16,2),0,range);assert(e.value==1);
 e=off.convert(offset2,event(EV_ABS,16,1),0,range);assert(e.value==0);
 assert(hats.axes.at(2)==3&&hats.axes.at(5)==4); // D-pad calibration preserves fixed sticks.

 // Regression: editable Android text may contain whitespace-only lines or flattened records.
 Mapping clean;clean.parse("k 306 1\na 0 0\na 2 3\nh 16 1 0 16\n");
 for(const std::string& raw:std::vector<std::string>{
     "k 306 1\n   \t\r\na 0 0\na 2 3\nh 16 1 0 16\n",
     "k 306 1 a 0 0 a 2 3 h 16 1 0 16",
     "k 306 1\r\na 0 0\r\na 2 3\r\nh 16 1 0 16\r\n",
     R"(k 306 1\na 0 0\na 2 3\nh 16 1 0 16\n)",
     std::string("\xEF\xBB\xBF")+"k 306 1\na 0 0\na 2 3\nh 16 1 0 16\n",
     std::string(u8"к 306 1\nа 0 0\nа 2 3\nh 16 1 0 16\n"),
     "# layout\nk 306 1 # circle\na 0 0\na 2 3\nh 16 1 0 16\n"
 }) {Mapping restored;restored.parse(raw);assert(restored.dump()==clean.dump());}
 Mapping hex;hex.parse("k 0x132 1 a 0 0 a 0x02 0x03 h 0x10 1 0 16");assert(hex.dump()==clean.dump());
 Mapping unicode;unicode.parse(u8"k 306 1\na 0 0\na 2 3\nh 16 1 0 16\n");assert(unicode.dump()==clean.dump());
 auto saved=clean.dump();
 for(const char* bad:{"k 306\n","k 306 wrong\n","a 0 0 extra\n","k 999999999999999999999 1\n","h 16 1 0\n"}) {
     bool rejected=false;try{clean.parse(bad);}catch(const std::exception& ex){rejected=true;assert(std::string(ex.what()).find("Строка")!=std::string::npos);}
     assert(rejected);assert(clean.dump()==saved); // Failed edit does not partially overwrite existing assignments.
 }
 std::cout<<"PASS: axis remap, normalization, deadzone, Circle, unknown keys, profiles, D-pad, SYN_REPORT, serialization, invalid mappings, directional HAT calibration, D-pad release/diagonals, mixed sources, preserved sticks, editable text normalization, fail-closed edits\n";
}
