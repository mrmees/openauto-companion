package org.openauto.companion.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketException

class PiConnectionParsingTest {
    @Test
    fun decodeHexKey_parsesValidHex() {
        val decoded = decodeHexKey("00ff7f")
        requireNotNull(decoded)
        assertArrayEquals(byteArrayOf(0x00, 0xff.toByte(), 0x7f), decoded)
    }

    @Test
    fun decodeHexKey_rejectsOddLength() {
        assertNull(decodeHexKey("abc"))
    }

    @Test
    fun decodeHexKey_rejectsNonHexCharacters() {
        assertNull(decodeHexKey("zz11"))
    }

    @Test
    fun shouldFallbackToUnboundSocket_returnsTrueForEpermBindingError() {
        val err = SocketException("Binding socket to network 226 failed: EPERM (Operation not permitted)")
        assertTrue(shouldFallbackToUnboundSocket(err))
    }

    @Test
    fun shouldFallbackToUnboundSocket_returnsFalseForOtherErrors() {
        val err = SocketException("Connection timed out")
        assertFalse(shouldFallbackToUnboundSocket(err))
    }
}
