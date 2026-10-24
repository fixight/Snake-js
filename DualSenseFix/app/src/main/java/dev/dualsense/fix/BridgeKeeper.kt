package dev.dualsense.fix

import android.app.*
import android.content.Intent
import android.os.*

class BridgeKeeper: Service() {
    private val handler=Handler(Looper.getMainLooper())
    private var lastNotification=""
    private fun notification(state:RuntimeState):Notification {
        val stop=PendingIntent.getService(this,1,Intent(this,BridgeKeeper::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"bridge").setContentTitle(state.title).setContentText(state.detail)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).setOngoing(state.busy).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Остановить",stop).build()).build()
    }
    private val tick=object:Runnable {
        override fun run() {
            try {
                val b=BridgeConnection.bridge ?: throw IllegalStateException("Связь с shell-службой потеряна")
                b.heartbeat()
                val state=RuntimeState.parse(b.state());BridgeConnection.lastState=state
                if(!state.busy){stopSelf();return}
                val key=state.title+state.detail
                if(key!=lastNotification){getSystemService(NotificationManager::class.java).notify(1,notification(state));lastNotification=key}
            }catch(e:Exception){BridgeConnection.lost(e.message ?: e.toString());stopSelf();return}
            handler.postDelayed(this,500)
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("bridge","DualSense bridge",NotificationManager.IMPORTANCE_LOW))
        startForeground(1,notification(BridgeConnection.lastState))
        BridgeConnection.keeping=true;handler.post(tick)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { if(intent?.action=="stop")stopSelf();return START_NOT_STICKY }
    override fun onTaskRemoved(rootIntent:Intent?){BridgeConnection.close(this);stopSelf()}
    override fun onDestroy() {
        handler.removeCallbacks(tick);BridgeConnection.keeping=false
        // Keep an authorized shell connection for another Start; stop the native worker.
        try { BridgeConnection.bridge?.let { it.stop();BridgeConnection.lastState=RuntimeState.parse(it.state()) } }catch(_:Exception){}
        super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
}
