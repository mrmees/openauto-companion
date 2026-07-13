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
