package org.openauto.companion.net

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun parseDisplayFromAck_validDisplay() {
        val ack = JSONObject("""{"type":"hello_ack","accepted":true,"session_key":"aa","display":{"width":1024,"height":600}}""")
        val result = parseDisplayFromAck(ack)
        assertNotNull(result)
        assertEquals(1024, result!!.first)
        assertEquals(600, result.second)
    }

    @Test
    fun parseDisplayFromAck_missingDisplay() {
        val ack = JSONObject("""{"type":"hello_ack","accepted":true,"session_key":"aa"}""")
        assertNull(parseDisplayFromAck(ack))
    }

    @Test
    fun parseDisplayFromAck_zeroValues() {
        val ack = JSONObject("""{"type":"hello_ack","accepted":true,"session_key":"aa","display":{"width":0,"height":0}}""")
        assertNull(parseDisplayFromAck(ack))
    }
}
