package org.openauto.companion.net.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import prodigy.api.v1.Api

class ApiReportsTest {
    @Test
    fun timeReport_usesRequestIdZeroAndV1UnixTimeOnly() {
        val message = ApiReports.timeReport(unixTimeMs = 1_765_000_000_000L)

        assertEquals(0L, message.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.TIME_REPORT, message.payloadCase)
        assertEquals(1_765_000_000_000L, message.timeReport.unixTimeMs)
    }

    @Test
    fun gpsReport_usesRequestIdZeroAndOptionalAltitudePresence() {
        val message = ApiReports.gpsReport(
            latitude = 41.881,
            longitude = -87.623,
            speedMps = 13.4,
            bearingDeg = 270.0,
            accuracyM = 4.5,
            ageMs = 250,
            altitudeM = 181.2
        )

        assertEquals(0L, message.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.GPS_REPORT, message.payloadCase)
        assertEquals(41.881, message.gpsReport.latitude, 0.000001)
        assertEquals(-87.623, message.gpsReport.longitude, 0.000001)
        assertEquals(13.4, message.gpsReport.speedMps, 0.000001)
        assertEquals(270.0, message.gpsReport.bearingDeg, 0.000001)
        assertEquals(4.5, message.gpsReport.accuracyM, 0.000001)
        assertEquals(250, message.gpsReport.ageMs)
        assertTrue(message.gpsReport.hasAltitudeM())
        assertEquals(181.2, message.gpsReport.altitudeM, 0.000001)
    }

    @Test
    fun batteryReport_usesRequestIdZero() {
        val message = ApiReports.batteryReport(percent = 88, charging = true)

        assertEquals(0L, message.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.BATTERY_REPORT, message.payloadCase)
        assertEquals(88, message.batteryReport.percent)
        assertTrue(message.batteryReport.charging)
    }

    @Test
    fun connectivityReport_setsSocksPasswordOnlyWhenSupplied() {
        val withPassword = ApiReports.connectivityReport(
            internetAvailable = true,
            socks5Active = true,
            socks5Port = 1080,
            socks5Password = "pass-1234"
        )
        val withoutPassword = ApiReports.connectivityReport(
            internetAvailable = false,
            socks5Active = false,
            socks5Port = 0,
            socks5Password = null
        )

        assertEquals(0L, withPassword.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.CONNECTIVITY_REPORT, withPassword.payloadCase)
        assertTrue(withPassword.connectivityReport.internetAvailable)
        assertTrue(withPassword.connectivityReport.socks5Active)
        assertEquals(1080, withPassword.connectivityReport.socks5Port)
        assertTrue(withPassword.connectivityReport.hasSocks5Password())
        assertEquals("pass-1234", withPassword.connectivityReport.socks5Password)

        assertFalse(withoutPassword.connectivityReport.hasSocks5Password())
    }
}
