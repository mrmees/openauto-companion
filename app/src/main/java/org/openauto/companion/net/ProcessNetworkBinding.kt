package org.openauto.companion.net

import android.net.ConnectivityManager
import android.net.Network

interface ProcessNetworkBinder {
    fun current(): Network?
    fun bind(network: Network?): Boolean
}

class AndroidProcessNetworkBinder(
    private val connectivityManager: ConnectivityManager
) : ProcessNetworkBinder {
    override fun current(): Network? = connectivityManager.boundNetworkForProcess

    override fun bind(network: Network?): Boolean =
        connectivityManager.bindProcessToNetwork(network)
}

class ProcessNetworkBinding(
    private val binder: ProcessNetworkBinder
) {
    fun bindForScope(network: Network?): Result {
        if (network == null) return Result.NoNetwork

        val previous = binder.current()
        return try {
            if (binder.bind(network)) {
                Result.Bound(Binding(binder, previous))
            } else {
                Result.Failed(cause = null)
            }
        } catch (e: RuntimeException) {
            Result.Failed(cause = e)
        }
    }

    class Binding internal constructor(
        private val binder: ProcessNetworkBinder,
        private val previous: Network?
    ) {
        fun restore(): Boolean = binder.bind(previous)
    }

    sealed class Result {
        data class Bound(val binding: Binding) : Result()
        data object NoNetwork : Result()
        data class Failed(val cause: Throwable?) : Result()
    }
}
