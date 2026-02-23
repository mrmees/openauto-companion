package org.openauto.companion.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
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

    private var wifiNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback(
        FLAG_INCLUDE_LOCATION_INFO
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifiInfo = caps.transportInfo as? WifiInfo ?: return
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return

            if (ssid == targetSsid) {
                Log.i(TAG, "Connected to target SSID: $ssid")
                wifiNetwork = network
                startCompanionService()
            }
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "WiFi network lost")
            wifiNetwork = null
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

        // Check if already connected to target SSID (callback won't fire for existing networks)
        checkCurrentNetwork()
    }

    private fun checkCurrentNetwork() {
        // Try ConnectivityManager first (allNetworks + transportInfo)
        try {
            @Suppress("DEPRECATION")
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val wifiInfo = caps.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                Log.i(TAG, "Network check: transport=WiFi, ssid=$ssid, transportInfo=${caps.transportInfo?.javaClass?.simpleName}")
                if (ssid == targetSsid) {
                    Log.i(TAG, "Already connected to target SSID via ConnectivityManager")
                    wifiNetwork = network
                    startCompanionService()
                    return
                }
                // Even if SSID unknown, save the WiFi network for binding
                if (wifiNetwork == null) wifiNetwork = network
            }
        } catch (e: Exception) {
            Log.w(TAG, "ConnectivityManager network scan failed", e)
        }

        // Fallback: WifiManager.connectionInfo (deprecated but reliable for SSID)
        try {
            val wifiManager = context.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            Log.i(TAG, "WifiManager fallback: ssid=$ssid")
            if (ssid == targetSsid) {
                Log.i(TAG, "Already connected to target SSID via WifiManager")
                startCompanionService()
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager fallback failed", e)
        }

        Log.i(TAG, "Target SSID not found in current networks")
    }

    fun stop() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        registered = false
    }

    fun getWifiNetwork(): Network? = wifiNetwork

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
