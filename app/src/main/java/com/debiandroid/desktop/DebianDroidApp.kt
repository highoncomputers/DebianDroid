package com.debiandroid.desktop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DebianDroidApp : Application() {
    companion object {
        const val CHANNEL_DESKTOP = "desktop_service"
        const val CHANNEL_SETUP = "setup_progress"
        lateinit var instance: DebianDroidApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DESKTOP,
                    "Desktop Environment",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows when the Debian desktop is running"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SETUP,
                    "Setup Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows rootfs extraction and setup progress"
                }
            )
        }
    }
}
