package org.openauto.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.openauto.companion.data.Vehicle
import org.openauto.companion.service.WifiMonitor

class CompanionApp : Application() {
    var wifiMonitor: WifiMonitor? = null
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startWifiMonitor(vehicles: List<Vehicle>) {
        wifiMonitor?.stop()
        wifiMonitor = WifiMonitor(this, vehicles).also { it.start() }
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
