package org.openauto.companion.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.util.Log
import androidx.core.content.ContextCompat

class WifiMonitor(
    private val context: Context,
    private val targetSsid: String = "OpenAutoProdigy",
    private val sharedSecret: String
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback(
        FLAG_INCLUDE_LOCATION_INFO
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifiInfo = caps.transportInfo as? WifiInfo ?: return
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return

            if (ssid == targetSsid) {
                Log.i(TAG, "Connected to target SSID: $ssid")
                startCompanionService()
            }
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "WiFi network lost")
            stopCompanionService()
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
        Log.i(TAG, "WiFi monitor started, watching for SSID: $targetSsid")
    }

    fun stop() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        registered = false
    }

    private fun startCompanionService() {
        val intent = Intent(context, CompanionService::class.java).apply {
            putExtra("shared_secret", sharedSecret)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopCompanionService() {
        context.stopService(Intent(context, CompanionService::class.java))
    }

    companion object {
        private const val TAG = "WifiMonitor"
    }
}
