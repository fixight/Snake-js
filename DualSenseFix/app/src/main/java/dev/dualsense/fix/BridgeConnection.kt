package dev.dualsense.fix

import android.content.*
import android.os.IBinder
import rikka.shizuku.Shizuku

object BridgeConnection {
    @Volatile var bridge: IBridge? = null
    var androidEvents = ""
    var bound = false
    @Volatile var keeping = false
    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context, BridgeService::class.java))
        .daemon(false).processNameSuffix("bridge").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private val connection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { bridge = IBridge.Stub.asInterface(service) }
        override fun onServiceDisconnected(name: ComponentName) { bridge = null; bound = false }
    }
    fun bind(context: Context) { if (!bound) { Shizuku.bindUserService(args(context), connection); bound = true } }
    fun close(context: Context) {
        try { bridge?.stop() } catch (_: Exception) {}
        try { if (bound) Shizuku.unbindUserService(args(context), connection, true) } catch (_: Exception) {}
        bridge = null; bound = false
    }
}
