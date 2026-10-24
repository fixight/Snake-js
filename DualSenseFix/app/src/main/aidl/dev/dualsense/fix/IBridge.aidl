package dev.dualsense.fix;
interface IBridge {
    void destroy() = 16777114;
    String info() = 0;
    void start(int profile, boolean grab, boolean diagnostic, String mapping) = 1;
    void stop() = 2;
    String snapshot() = 3;
    String mappings() = 4;
    void learn(int type, int target) = 5;
    void testButton() = 6;
    void heartbeat() = 7;
    String inputReport() = 8;
    String state() = 9;
    void replaceMapping(String mapping) = 10;
}
