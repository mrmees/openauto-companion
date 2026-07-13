package org.openauto.companion.net

import android.net.Network
import java.net.Socket

object NetworkSocketFactory {
    fun forNetwork(
        network: Network?,
        unboundFactory: () -> Socket = { Socket() },
        onFallback: (Throwable) -> Unit = {}
    ): () -> Socket = {
        if (network == null) {
            unboundFactory()
        } else {
            createWithFallback(
                boundFactory = { network.socketFactory.createSocket() as Socket },
                unboundFactory = unboundFactory,
                onFallback = onFallback
            )
        }
    }

    internal fun createWithFallback(
        boundFactory: () -> Socket,
        unboundFactory: () -> Socket,
        onFallback: (Throwable) -> Unit = {}
    ): Socket = try {
        boundFactory()
    } catch (error: Exception) {
        if (!isPermissionDenied(error)) throw error
        onFallback(error)
        unboundFactory()
    }

    private fun isPermissionDenied(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            val message = current.message.orEmpty()
            if ("EPERM" in message || "Operation not permitted" in message) return true
            current = current.cause
        }
        return false
    }
}
