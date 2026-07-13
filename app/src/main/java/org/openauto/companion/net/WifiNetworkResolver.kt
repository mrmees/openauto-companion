package org.openauto.companion.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

class WifiNetworkResolver(context: Context) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION")
    fun resolve(ssid: String): Network? {
        val target = ssid.trim()
        if (target.isBlank()) return null

        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val wifiInfo = capabilities.transportInfo as? WifiInfo ?: continue
            if (normalizeSsid(wifiInfo.ssid) == target) return network
        }

        val currentSsid = normalizeSsid(wifiManager.connectionInfo?.ssid)
        if (currentSsid != target) return null
        val active = connectivityManager.activeNetwork ?: return null
        val activeCapabilities = connectivityManager.getNetworkCapabilities(active) ?: return null
        return active.takeIf {
            activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }

    private fun normalizeSsid(value: String?): String? = value
        ?.removeSurrounding("\"")
        ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
}
