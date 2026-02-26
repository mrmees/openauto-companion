package org.openauto.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriParserTest {
    @Test
    fun parse_validUriWithEndpointFields() {
        val parsed = PairingUriParser.parse(
            "openauto://pair?pin=123456&ssid=CarAP&host=10.0.0.1&port=8080"
        )
        requireNotNull(parsed)
        assertEquals("123456", parsed.pin)
        assertEquals("CarAP", parsed.ssid)
        assertEquals("10.0.0.1", parsed.host)
        assertEquals(8080, parsed.port)
    }

    @Test
    fun parse_validUriWithoutEndpointFields() {
        val parsed = PairingUriParser.parse("openauto://pair?pin=123456&ssid=CarAP")
        requireNotNull(parsed)
        assertEquals("123456", parsed.pin)
        assertEquals("CarAP", parsed.ssid)
        assertNull(parsed.host)
        assertNull(parsed.port)
    }

    @Test
    fun parse_rejectsInvalidSchemeOrPath() {
        assertNull(PairingUriParser.parse("https://pair?pin=123456&ssid=CarAP"))
        assertNull(PairingUriParser.parse("openauto://wrong?pin=123456&ssid=CarAP"))
    }

    @Test
    fun parse_rejectsInvalidPort() {
        assertNull(PairingUriParser.parse("openauto://pair?pin=123456&ssid=CarAP&port=abc"))
        assertNull(PairingUriParser.parse("openauto://pair?pin=123456&ssid=CarAP&port=0"))
        assertNull(PairingUriParser.parse("openauto://pair?pin=123456&ssid=CarAP&port=70000"))
    }
}
