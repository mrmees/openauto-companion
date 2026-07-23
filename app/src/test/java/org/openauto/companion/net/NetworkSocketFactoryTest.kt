package org.openauto.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

class NetworkSocketFactoryTest {
    @Test
    fun createRequiredBoundFactory_resolvesBoundFactoryForEverySocket() {
        val first = Socket()
        val second = Socket()
        val sockets = ArrayDeque(listOf(first, second))
        var resolutionCalls = 0

        val factory = NetworkSocketFactory.createRequiredBoundFactory {
            resolutionCalls += 1
            sockets.removeFirstOrNull()?.let { socket -> { socket } }
        }

        assertSame(first, factory())
        assertSame(second, factory())
        assertEquals(2, resolutionCalls)
    }

    @Test
    fun createRequiredBoundFactory_failsFastWhenNetworkIsUnavailable() {
        val factory = NetworkSocketFactory.createRequiredBoundFactory { null }

        val thrown = assertThrows(SocketException::class.java) { factory() }

        assertEquals("Matched Wi-Fi network is unavailable", thrown.message)
    }

    @Test
    fun createRequiredBoundFactory_doesNotFallbackWhenBindingIsDenied() {
        val error = SocketException(
            "Binding socket to network 226 failed: EPERM (Operation not permitted)"
        )
        val factory = NetworkSocketFactory.createRequiredBoundFactory {
            { throw error }
        }

        val thrown = assertThrows(SocketException::class.java) { factory() }

        assertSame(error, thrown)
    }

    @Test
    fun createWithFallback_returnsBoundSocketWithoutCreatingFallback() {
        val bound = Socket()
        var fallbackCalls = 0

        val result = NetworkSocketFactory.createWithFallback(
            boundFactory = { bound },
            unboundFactory = {
                fallbackCalls += 1
                Socket()
            }
        )

        assertSame(bound, result)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun createWithFallback_usesUnboundSocketOnceForEperm() {
        val fallback = Socket()
        var fallbackCalls = 0
        val error = SocketException(
            "Binding socket to network 226 failed: EPERM (Operation not permitted)"
        )

        val result = NetworkSocketFactory.createWithFallback(
            boundFactory = { throw error },
            unboundFactory = {
                fallbackCalls += 1
                fallback
            }
        )

        assertSame(fallback, result)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun createWithFallback_findsOperationNotPermittedInNestedCause() {
        val fallback = Socket()
        val error = IllegalStateException(
            "network binding failed",
            SocketException("Operation not permitted")
        )

        val result = NetworkSocketFactory.createWithFallback(
            boundFactory = { throw error },
            unboundFactory = { fallback }
        )

        assertSame(fallback, result)
    }

    @Test
    fun createWithFallback_rethrowsUnrelatedSocketErrorWithoutFallback() {
        val error = SocketTimeoutException("Connection timed out")
        var fallbackCalls = 0

        val thrown = assertThrows(SocketTimeoutException::class.java) {
            NetworkSocketFactory.createWithFallback(
                boundFactory = { throw error },
                unboundFactory = {
                    fallbackCalls += 1
                    Socket()
                }
            )
        }

        assertSame(error, thrown)
        assertEquals(0, fallbackCalls)
    }
}
