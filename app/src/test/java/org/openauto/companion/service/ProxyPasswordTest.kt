package org.openauto.companion.service

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyPasswordTest {
    @Test
    fun generateRequestsAndEncodesAtLeast128Bits() {
        var requestedBytes = 0
        val source = ByteArray(16) { it.toByte() }

        val password = ProxyPassword.generate { size ->
            requestedBytes = size
            source.copyOf()
        }

        assertEquals(16, requestedBytes)
        assertTrue(password.isNotBlank())
        assertArrayEquals(source, Base64.getUrlDecoder().decode(password))
    }

    @Test
    fun generatedPasswordIsIndependentFromApiCredentialText() {
        val apiSecret = "00112233445566778899aabbccddeeff"

        val password = ProxyPassword.generate { ByteArray(it) { index -> (index + 1).toByte() } }

        assertNotEquals(apiSecret, password)
        assertNotEquals(apiSecret.take(8), password)
    }
}
