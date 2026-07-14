package org.openauto.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.openauto.companion.data.Vehicle
import org.openauto.companion.service.WifiMonitor

class CompanionApp : Application() {
    private val wifiMonitorSlot = MonitorSlot<WifiMonitor>()
    val wifiMonitor: WifiMonitor?
        get() = wifiMonitorSlot.current

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startWifiMonitor(vehicles: List<Vehicle>) {
        // Activity recreation calls this path; replacing the callback owner must not
        // tear down the foreground runtime that already owns the live API session.
        wifiMonitorSlot.replace(WifiMonitor(this, vehicles))
    }

    fun stopWifiMonitor() {
        wifiMonitorSlot.stopCurrent()
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
