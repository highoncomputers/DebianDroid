package com.debiandroid.desktop.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.debiandroid.desktop.DebianDroidApp
import com.debiandroid.desktop.proot.RootfsManager
import com.debiandroid.desktop.proot.SetupPhase
import kotlinx.coroutines.*

class SetupService : Service() {
    private lateinit var rootfsManager: RootfsManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        rootfsManager = RootfsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Starting setup...", 0)
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            try {
                rootfsManager.setup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                updateNotification("Setup failed: ${e.message}", -1)
            }
        }

        // Monitor progress
        scope.launch {
            rootfsManager.progress.collect { progress ->
                val text = when (progress.phase) {
                    SetupPhase.DOWNLOADING -> "Downloading: ${(progress.progress * 100).toInt()}% at ${progress.speed}"
                    SetupPhase.EXTRACTING -> "Extracting: ${(progress.progress * 100).toInt()}%"
                    SetupPhase.CONFIGURING -> "Configuring..."
                    SetupPhase.COMPLETE -> "Setup complete!"
                    SetupPhase.ERROR -> "Error: ${progress.error}"
                }
                val prog = (progress.progress * 100).toInt()
                updateNotification(text, if (progress.phase == SetupPhase.ERROR) -1 else prog)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val channelId = DebianDroidApp.CHANNEL_SETUP
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Setting up DebianDroid")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, if (progress >= 0) progress else 0, progress < 0)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    companion object {
        const val NOTIFICATION_ID = 1002
    }
}
