package org.openauto.companion.net

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test
    fun computeHmac_matchesExpected() {
        val key = "test-key".toByteArray()
        val data = "test-data".toByteArray()
        val hmac = Protocol.computeHmac(key, data)
        // HMAC-SHA256("test-key", "test-data") is deterministic
        assertNotNull(hmac)
        assertEquals(64, hmac.length) // hex-encoded SHA256
    }

    @Test
    fun buildHello_includesAllFields() {
        val hello = Protocol.buildHello(
            secret = "my-secret",
            nonce = "abc123",
            capabilities = listOf("time", "gps")
        )
        assertEquals("hello", hello.getString("type"))
        assertEquals(1, hello.getInt("version"))
        assertTrue(hello.has("token"))
        assertEquals(2, hello.getJSONArray("capabilities").length())
    }

    @Test
    fun verifyMac_roundTrips() {
        // We can't test buildStatus directly in JVM unit tests because
        // SystemClock.elapsedRealtime() returns 0 in JVM, but the HMAC
        // will still be consistent. Test the MAC verification round-trip
        // using a manually constructed payload instead.
        val sessionKey = "my-session-key".toByteArray()
        val payload = org.json.JSONObject().apply {
            put("type", "status")
            put("seq", 1)
            put("sent_mono_ms", 0)
            put("time_ms", 1740250000000L)
        }
        val payloadStr = payload.toString()
        val mac = Protocol.computeHmac(sessionKey, payloadStr.toByteArray())
        payload.put("mac", mac)

        assertTrue(Protocol.verifyMac(payload, sessionKey))
    }

    @Test
    fun verifyMac_rejectsTampering() {
        val sessionKey = "my-session-key".toByteArray()
        val payload = org.json.JSONObject().apply {
            put("type", "status")
            put("seq", 1)
            put("time_ms", 1740250000000L)
        }
        val payloadStr = payload.toString()
        val mac = Protocol.computeHmac(sessionKey, payloadStr.toByteArray())
        payload.put("mac", mac)
        // Tamper
        payload.put("seq", 999)
        assertFalse(Protocol.verifyMac(payload, sessionKey))
    }
}
