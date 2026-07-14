package org.openauto.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriParserTest {
    @Test
    fun parse_validProdigyUriWithRequiredFieldsAndUnknownAdditions() {
        val parsed = PairingUriParser.parse(
            "prodigy://pair?host=10.0.0.1&tcp=19810&ws=19811&pin=123456" +
                "&ssid=OpenAutoProdigy-A3F2&future=value"
        )

        requireNotNull(parsed)
        assertEquals("10.0.0.1", parsed.host)
        assertEquals(19810, parsed.tcpPort)
        assertEquals(19811, parsed.webSocketPort)
        assertEquals("123456", parsed.pin)
        assertEquals("OpenAutoProdigy-A3F2", parsed.ssid)
    }

    @Test
    fun parse_decodesPercentEncodedSsid() {
        val parsed = PairingUriParser.parse(
            "prodigy://pair?host=10.0.0.1&tcp=9810&ws=9811&pin=123456" +
                "&ssid=My%20Car%20AP%2B5G%26more"
        )

        requireNotNull(parsed)
        assertEquals("My Car AP+5G&more", parsed.ssid)
    }

    @Test
    fun parse_rejectsWrongSchemeOrAuthority() {
        assertNull(
            PairingUriParser.parse(
                "openauto://pair?host=10.0.0.1&tcp=9810&ws=9811&pin=123456&ssid=Car"
            )
        )
        assertNull(
            PairingUriParser.parse(
                "prodigy://wrong?host=10.0.0.1&tcp=9810&ws=9811&pin=123456&ssid=Car"
            )
        )
    }

    @Test
    fun parse_rejectsMissingOrMalformedRequiredFields() {
        val valid = "prodigy://pair?host=10.0.0.1&tcp=9810&ws=9811&pin=123456&ssid=Car"

        listOf(
            valid.replace("host=10.0.0.1&", ""),
            valid.replace("tcp=9810&", ""),
            valid.replace("ws=9811&", ""),
            valid.replace("pin=123456&", ""),
            valid.replace("&ssid=Car", ""),
            valid.replace("host=10.0.0.1", "host=%20"),
            valid.replace("tcp=9810", "tcp=0"),
            valid.replace("tcp=9810", "tcp=70000"),
            valid.replace("ws=9811", "ws=abc"),
            valid.replace("pin=123456", "pin=12345"),
            valid.replace("ssid=Car", "ssid=%20"),
            valid.replace("ssid=Car", "ssid=%ZZ")
        ).forEach { assertNull(PairingUriParser.parse(it)) }
    }
}
