package org.openauto.companion.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import java.net.InetAddress

class WifiNetworkResolver(context: Context) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION")
    fun resolve(ssid: String, host: String? = null): Network? {
        val target = ssid.trim()
        if (target.isBlank()) return null

        val wifiNetworks = connectivityManager.allNetworks.filter { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        for (network in wifiNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
            val wifiInfo = capabilities.transportInfo as? WifiInfo ?: continue
            if (normalizeSsid(wifiInfo.ssid) == target) return network
        }

        val currentSsid = normalizeSsid(wifiManager.connectionInfo?.ssid)
        if (currentSsid != null && currentSsid != target) return null
        resolveByDirectRoute(wifiNetworks, host)?.let { return it }

        if (currentSsid != target) return null
        val active = connectivityManager.activeNetwork ?: return null
        val activeCapabilities = connectivityManager.getNetworkCapabilities(active) ?: return null
        return active.takeIf {
            activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }

    private fun resolveByDirectRoute(networks: List<Network>, host: String?): Network? {
        val address = host
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
            ?: return null

        return networks.firstOrNull { network ->
            val routes = connectivityManager.getLinkProperties(network)?.routes
                ?: return@firstOrNull false
            routes.any { route -> !route.hasGateway() && route.matches(address) }
        }
    }

    private fun normalizeSsid(value: String?): String? = value
        ?.removeSurrounding("\"")
        ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
}
