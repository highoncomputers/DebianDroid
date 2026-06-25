package com.debiandroid.desktop.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.debiandroid.desktop.DebianDroidApp
import com.debiandroid.desktop.R
import com.debiandroid.desktop.proot.ProotRunner
import kotlinx.coroutines.*

class DesktopService : Service() {
    private lateinit var prootRunner: ProotRunner
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        prootRunner = ProotRunner(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
            ACTION_RESTART -> restart()
        }

        return START_STICKY
    }

    private fun start() {
        scope.launch {
            try {
                prootRunner.startDesktop()
                updateNotification("Debian Desktop is running")
            } catch (e: Exception) {
                updateNotification("Failed to start: ${e.message}")
            }
        }
    }

    private fun stop() {
        prootRunner.stop()
        updateNotification("Debian Desktop stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restart() {
        prootRunner.stop()
        delay(500)
        start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        prootRunner.cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = DebianDroidApp.CHANNEL_DESKTOP
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("DebianDroid")
            .setContentText("Desktop environment running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, DebianDroidApp.CHANNEL_DESKTOP)
            .setContentTitle("DebianDroid")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.debiandroid.desktop.action.START"
        const val ACTION_STOP = "com.debiandroid.desktop.action.STOP"
        const val ACTION_RESTART = "com.debiandroid.desktop.action.RESTART"
    }
}
