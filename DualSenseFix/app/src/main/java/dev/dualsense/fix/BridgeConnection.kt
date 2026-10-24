package dev.dualsense.fix

import android.content.*
import android.os.*
import rikka.shizuku.Shizuku

object BridgeConnection {
    @Volatile var bridge: IBridge? = null
    var androidEvents = ""
    @Volatile var bound = false
    @Volatile var connecting = false
    @Volatile var error = ""
    @Volatile var lastState = RuntimeState()
    @Volatile var keeping = false
    private val handler=Handler(Looper.getMainLooper())
    private var attempt=0
    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context, BridgeService::class.java))
        .daemon(false).processNameSuffix("bridge").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private val connection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            try {
                val b=IBridge.Stub.asInterface(service)
                check(b.info().contains("uid=2000 ")) { "Нужен Shizuku в ADB-режиме (uid 2000)" }
                bridge=b;connecting=false;error=""
            } catch(e:Exception) { bridge=null;connecting=false;error=e.message ?: e.toString() }
        }
        override fun onServiceDisconnected(name: ComponentName) { lost("Shell-служба отключилась. Подключите Shizuku заново.") }
        override fun onNullBinding(name: ComponentName) { lost("Shizuku не вернул shell-службу.") }
        override fun onBindingDied(name: ComponentName) { lost("Соединение с shell-службой завершено.") }
    }
    fun lost(reason:String) { bridge=null;bound=false;connecting=false;error=reason;++attempt }
    fun bind(context: Context) {
        if(connecting||bridge!=null)return
        // Dispose of a previous failed binding before retrying.
        if(bound)try { Shizuku.unbindUserService(args(context),connection,true) }catch(_:Exception){}
        error="";connecting=true;bound=true;val ticket=++attempt
        try { Shizuku.bindUserService(args(context),connection) }
        catch(e:Exception){lost(e.message ?: e.toString());return}
        handler.postDelayed({
            if(ticket==attempt&&connecting) {
                try { Shizuku.unbindUserService(args(context),connection,true) }catch(_:Exception){}
                lost("Shell-служба не ответила за 12 секунд. Проверьте Shizuku и повторите подключение.")
            }
        },12000)
    }
    fun close(context: Context) {
        ++attempt;connecting=false
        try { bridge?.stop() } catch (_: Exception) {}
        try { if (bound) Shizuku.unbindUserService(args(context), connection, true) } catch (_: Exception) {}
        bridge = null; bound = false
    }
}
