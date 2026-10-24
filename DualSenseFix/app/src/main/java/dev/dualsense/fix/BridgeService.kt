package dev.dualsense.fix

import android.content.Context
import android.os.Process

class BridgeService(context: Context) : IBridge.Stub() {
    init { System.load(context.applicationInfo.nativeLibraryDir + "/libbridge.so") }
    override fun info() = "UserService uid=${Process.myUid()} pid=${Process.myPid()} (требуется uid=2000)"
    override fun start(profile: Int, grab: Boolean, diagnostic: Boolean, mapping: String) {
        check(Process.myUid() == 2000) { "Требуется Shizuku в ADB shell режиме" }
        nativeStart(profile, grab, diagnostic, mapping)
    }
    override fun stop() = nativeStop()
    override fun snapshot() = nativeSnapshot()
    override fun state() = nativeState()
    private external fun nativeState(): String
    override fun replaceMapping(mapping: String) = nativeReplaceMapping(mapping)
    private external fun nativeReplaceMapping(mapping: String)
    override fun mappings() = nativeMappings()
    override fun learn(type: Int, target: Int) = nativeLearn(type, target)
    override fun testButton() = nativeTest()
    override fun heartbeat() = nativeHeartbeat()
    override fun inputReport(): String {
        val p = ProcessBuilder("/system/bin/dumpsys", "input").redirectErrorStream(true).start()
        return p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }
    override fun destroy() { nativeStop(); kotlin.system.exitProcess(0) }
    private external fun nativeStart(profile: Int, grab: Boolean, diagnostic: Boolean, mapping: String)
    private external fun nativeStop()
    private external fun nativeSnapshot(): String
    private external fun nativeMappings(): String
    private external fun nativeLearn(type: Int, target: Int)
    private external fun nativeTest()
    private external fun nativeHeartbeat()
}
