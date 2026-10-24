package dev.dualsense.fix

import android.app.*
import android.content.Intent
import android.os.*

class BridgeKeeper: Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object: Runnable {
        override fun run() {
            try { val b=BridgeConnection.bridge ?: throw IllegalStateException("Shell service lost"); b.heartbeat() }
            catch (_: Exception) { stopSelf(); return }
            handler.postDelayed(this, 500)
        }
    }
    override fun onCreate() {
        super.onCreate()
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bridge", "DualSense bridge", NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,1,Intent(this,BridgeKeeper::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1,Notification.Builder(this,"bridge").setContentTitle("DualSense Fix работает")
            .setContentText("Можно открыть PPSSPP. Stop освобождает контроллер.")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"Stop Bridge",stop).build()).build())
        BridgeConnection.keeping=true
        handler.post(tick)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="stop")stopSelf()
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() { handler.removeCallbacks(tick); BridgeConnection.keeping=false; BridgeConnection.close(this); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
