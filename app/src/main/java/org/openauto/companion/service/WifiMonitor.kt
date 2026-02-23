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
import org.openauto.companion.data.Vehicle

class WifiMonitor(
    private val context: Context,
    private val vehicles: List<Vehicle>
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private var registered = false
    private var wifiNetwork: Network? = null
    private var activeVehicle: Vehicle? = null
    private val ssidMap: Map<String, Vehicle> = vehicles.associateBy { it.ssid }

    private val networkCallback = object : ConnectivityManager.NetworkCallback(
        FLAG_INCLUDE_LOCATION_INFO
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifiInfo = caps.transportInfo as? WifiInfo ?: return
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return
            if (ssid == "<unknown ssid>") return

            val vehicle = ssidMap[ssid] ?: return
            if (activeVehicle?.ssid == ssid) return // already connected to this one

            Log.i(TAG, "Matched vehicle '${vehicle.name}' on SSID: $ssid")
            wifiNetwork = network
            activeVehicle = vehicle
            startCompanionService(vehicle)
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "WiFi network lost")
            wifiNetwork = null
            activeVehicle = null
            stopCompanionService()
        }
    }

    fun start() {
        if (registered) return
        if (vehicles.isEmpty()) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
        Log.i(TAG, "WiFi monitor started, watching for ${vehicles.size} vehicle(s): ${vehicles.map { it.ssid }}")
        checkCurrentNetwork()
    }

    private fun checkCurrentNetwork() {
        try {
            @Suppress("DEPRECATION")
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val wifiInfo = caps.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                Log.i(TAG, "Network check: transport=WiFi, ssid=$ssid")
                val vehicle = ssid?.let { ssidMap[it] }
                if (vehicle != null) {
                    Log.i(TAG, "Already connected to vehicle '${vehicle.name}' via ConnectivityManager")
                    wifiNetwork = network
                    activeVehicle = vehicle
                    startCompanionService(vehicle)
                    return
                }
                if (wifiNetwork == null) wifiNetwork = network
            }
        } catch (e: Exception) {
            Log.w(TAG, "ConnectivityManager network scan failed", e)
        }

        // Fallback: WifiManager.connectionInfo
        try {
            val wifiManager = context.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            Log.i(TAG, "WifiManager fallback: ssid=$ssid")
            val vehicle = ssid?.let { ssidMap[it] }
            if (vehicle != null) {
                Log.i(TAG, "Already connected to vehicle '${vehicle.name}' via WifiManager")
                activeVehicle = vehicle
                startCompanionService(vehicle)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager fallback failed", e)
        }

        Log.i(TAG, "No paired vehicle SSID found in current networks")
    }

    fun stop() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        registered = false
    }

    fun getWifiNetwork(): Network? = wifiNetwork

    private fun startCompanionService(vehicle: Vehicle) {
        val intent = Intent(context, CompanionService::class.java).apply {
            putExtra("shared_secret", vehicle.sharedSecret)
            putExtra("vehicle_name", vehicle.name)
            putExtra("vehicle_id", vehicle.id)
            putExtra("socks5_enabled", vehicle.socks5Enabled)
            putExtra("audio_keep_alive", vehicle.audioKeepAlive)
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
