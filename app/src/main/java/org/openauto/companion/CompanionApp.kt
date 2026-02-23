package org.openauto.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Companion Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when sharing data with OpenAuto Prodigy"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "companion_service"
    }
}
