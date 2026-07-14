package org.openauto.companion.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

internal class CellularUpstreamState<T>(
    private val onSelectedChanged: (T?) -> Unit
) {
    private val usable = linkedSetOf<T>()

    var selected: T? = null
        private set
        get() = synchronized(this) { field }

    fun update(network: T, usable: Boolean) {
        val (changed, current) = synchronized(this) {
            val previous = selected
            if (usable) {
                this.usable += network
                if (selected == null) selected = network
            } else {
                this.usable -= network
                if (selected == network) selected = this.usable.firstOrNull()
            }
            (previous != selected) to selected
        }
        if (changed) onSelectedChanged(current)
    }

    fun remove(network: T) {
        update(network, usable = false)
    }

    fun reset() {
        val changed = synchronized(this) {
            usable.clear()
            if (selected == null) {
                false
            } else {
                selected = null
                true
            }
        }
        if (changed) onSelectedChanged(null)
    }

}

class CellularUpstreamMonitor(
    private val connectivityManager: ConnectivityManager,
    onChanged: (Network?) -> Unit
) : AutoCloseable {
    private val state = CellularUpstreamState(onChanged)
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            state.update(
                network,
                isUsable(connectivityManager.getNetworkCapabilities(network))
            )
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            state.update(network, isUsable(capabilities))
        }

        override fun onLost(network: Network) {
            state.remove(network)
        }
    }

    val currentNetwork: Network?
        get() = state.selected

    val internetAvailable: Boolean
        get() = currentNetwork != null

    fun start() {
        if (started) return
        started = true
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.requestNetwork(request, callback)
        } catch (error: Exception) {
            started = false
            throw error
        }
    }

    override fun close() {
        if (!started) return
        started = false
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
        } finally {
            state.reset()
        }
    }

    private fun isUsable(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
