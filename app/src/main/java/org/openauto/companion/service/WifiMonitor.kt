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
import org.openauto.companion.net.api.ApiCrypto

class WifiMonitor(
    private val context: Context,
    private val vehicles: List<Vehicle>
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private var registered = false
    private var wifiNetwork: Network? = null
    private var activeVehicle: Vehicle? = null
    private val runtimeVehicles = vehicles.filter(::hasValidRuntimeCredentials)
    private val ssidMap: Map<String, Vehicle> = runtimeVehicles.associateBy { it.ssid }

    private val networkCallback = object : ConnectivityManager.NetworkCallback(
        FLAG_INCLUDE_LOCATION_INFO
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifiInfo = caps.transportInfo as? WifiInfo ?: return
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return
            if (ssid == "<unknown ssid>") return

            val vehicle = ssidMap[ssid] ?: return
            if (activeVehicle?.ssid == ssid && wifiNetwork == network) return

            Log.i(TAG, "Matched vehicle '${vehicle.name}' on SSID: $ssid")
            wifiNetwork = network
            activeVehicle = vehicle
            startCompanionService(vehicle)
        }

        override fun onLost(network: Network) {
            if (network != wifiNetwork) return
            Log.i(TAG, "WiFi network lost")
            wifiNetwork = null
            activeVehicle = null
            stopCompanionService()
        }
    }

    fun start() {
        if (registered) return
        if (runtimeVehicles.isEmpty()) {
            Log.i(TAG, "WiFi monitor has no vehicles with valid External API v1 credentials")
            return
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
        Log.i(
            TAG,
            "WiFi monitor started, watching for ${runtimeVehicles.size} External API vehicle(s): " +
                runtimeVehicles.map { it.ssid }
        )
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
        if (registered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            registered = false
        }
        wifiNetwork = null
        activeVehicle = null
        stopCompanionService()
    }

    fun getWifiNetwork(): Network? = wifiNetwork

    private fun startCompanionService(vehicle: Vehicle) {
        val clientId = vehicle.apiClientId.trim()
        val secretHex = vehicle.apiSecretHex.trim()
        if (!hasValidRuntimeCredentials(vehicle)) {
            Log.w(TAG, "Refusing to start service for vehicle without valid API v1 credentials")
            return
        }
        val intent = Intent(context, CompanionService::class.java).apply {
            putExtra(CompanionService.EXTRA_VEHICLE_NAME, vehicle.name)
            putExtra(CompanionService.EXTRA_VEHICLE_ID, vehicle.id)
            putExtra(CompanionService.EXTRA_VEHICLE_SSID, vehicle.ssid)
            putExtra(CompanionService.EXTRA_API_CLIENT_ID, clientId)
            putExtra(CompanionService.EXTRA_API_SECRET_HEX, secretHex)
            putExtra(CompanionService.EXTRA_SERVER_ID, vehicle.serverId)
            putExtra(CompanionService.EXTRA_HEAD_UNIT_HOST, vehicle.settingsHost)
            putExtra(CompanionService.EXTRA_API_TCP_PORT, vehicle.apiTcpPort)
            putExtra(CompanionService.EXTRA_SOCKS5_ENABLED, vehicle.socks5Enabled)
            putExtra(CompanionService.EXTRA_AUDIO_KEEP_ALIVE, vehicle.audioKeepAlive)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun hasValidRuntimeCredentials(vehicle: Vehicle): Boolean =
        vehicle.apiClientId.isNotBlank() &&
            ApiCrypto.decodeSecretHex(vehicle.apiSecretHex) != null

    private fun stopCompanionService() {
        context.stopService(Intent(context, CompanionService::class.java))
    }

    companion object {
        private const val TAG = "WifiMonitor"
    }
}
