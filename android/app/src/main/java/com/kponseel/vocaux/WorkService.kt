package com.kponseel.vocaux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** Service au premier plan : Android ne tue pas l'app pendant une transcription ou un téléchargement. */
class WorkService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("work", "Transcription", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "work")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Vocaux")
            .setContentText("Transcription en cours…")
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (wake == null) wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocaux:work").apply { acquire(60 * 60 * 1000L) }
        return START_NOT_STICKY
    }

    override fun onDestroy() { wake?.release(); wake = null }

    companion object {
        fun start(ctx: Context) = runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, WorkService::class.java)) }
        fun stop(ctx: Context) = runCatching { ctx.stopService(Intent(ctx, WorkService::class.java)) }
    }
}
