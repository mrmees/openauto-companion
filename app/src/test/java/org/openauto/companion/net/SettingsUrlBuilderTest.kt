package org.openauto.companion.net

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUrlBuilderTest {
    @Test
    fun build_usesVehicleHostAndPortWhenPresent() {
        val url = SettingsUrlBuilder.build(host = "10.0.0.5", port = 8181)
        assertEquals("http://10.0.0.5:8181", url)
    }

    @Test
    fun build_fallsBackWhenHostMissing() {
        val url = SettingsUrlBuilder.build(host = null, port = 8080)
        assertEquals(SettingsUrlBuilder.FALLBACK_URL, url)
    }

    @Test
    fun build_fallsBackWhenPortMissing() {
        val url = SettingsUrlBuilder.build(host = "10.0.0.5", port = null)
        assertEquals(SettingsUrlBuilder.FALLBACK_URL, url)
    }

    @Test
    fun build_fallsBackWhenHostBlank() {
        val url = SettingsUrlBuilder.build(host = "   ", port = 8080)
        assertEquals(SettingsUrlBuilder.FALLBACK_URL, url)
    }
}
