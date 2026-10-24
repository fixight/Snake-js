#pragma once
#include <string>
#include <vector>
#include <sstream>
#include <stdexcept>
#include <climits>
#include <cctype>

namespace dsfix {
struct MapRecord { char type;int source,value,neutral=0,target=0,line=0; };
[[noreturn]] inline void formatError(int line,const std::string& why) {throw std::runtime_error("Строка "+std::to_string(line)+": "+why);}
inline std::string displayMapToken(const std::string& token) {
    for(unsigned char c:token)if(c<32||c>126)return "<неизвестные символы>";
    return token.substr(0,32);
}
inline std::string normalizeMapText(const std::string& raw) {
    std::string out;
    for(size_t i=0;i<raw.size();) {
        if(raw.compare(i,3,"\xEF\xBB\xBF")==0){if(i!=0)out+=' ';i+=3;continue;}
        if(raw.compare(i,2,"\xC2\xA0")==0){out+=' ';i+=2;continue;}
        if(raw.compare(i,3,"\xE2\x80\xAF")==0){out+=' ';i+=3;continue;}
        if(raw[i]=='\\'&&i+1<raw.size()&&(raw[i+1]=='n'||raw[i+1]=='r'||raw[i+1]=='t')) {out+=raw[i+1]=='t'?' ':'\n';i+=2;continue;}
        out+=raw[i++];
    }
    return out;
}
inline std::vector<MapRecord> parseMapRecords(const std::string& raw) {
    struct Token {std::string value;int line;};std::vector<Token> tokens;
    std::istringstream lines(normalizeMapText(raw));std::string line;int number=0;
    while(std::getline(lines,line)) {
        ++number;auto comment=line.find('#');if(comment!=std::string::npos)line.resize(comment);
        std::istringstream words(line);std::string word;while(words>>word)tokens.push_back({word,number});
    }
    std::vector<MapRecord> result;size_t at=0;
    while(at<tokens.size()) {
        auto t=tokens[at++];std::string kind=t.value;
        if(kind=="K"||kind=="к"||kind=="К")kind="k";
        if(kind=="A"||kind=="а"||kind=="А")kind="a";
        if(kind=="H")kind="h";
        if(kind!="k"&&kind!="a"&&kind!="h")formatError(t.line,"ожидалась запись k, a или h; получено '"+displayMapToken(kind)+"'");
        auto integer=[&]() {
            if(at>=tokens.size())formatError(t.line,"не хватает чисел в записи "+kind);
            auto token=tokens[at++];
            try {
                size_t used=0;const std::string& s=token.value;
                bool hex=s.size()>2&&s[0]=='0'&&(s[1]=='x'||s[1]=='X');
                long long n=std::stoll(s,&used,hex?16:10);
                if(used!=s.size()||n<INT_MIN||n>INT_MAX)throw std::out_of_range("integer");
                return int(n);
            }catch(const std::exception&){formatError(token.line,"ожидалось целое число; получено '"+displayMapToken(token.value)+"'");return 0;}
        };
        int source=integer(),value=integer();MapRecord record{kind[0],source,value,0,0,t.line};
        if(kind=="h"){record.neutral=integer();record.target=integer();}
        result.push_back(record);
    }
    return result;
}
}
