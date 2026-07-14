package org.openauto.companion.net.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import prodigy.api.v1.Api

class ApiReportsTest {
    @Test
    fun timeReport_usesRequestIdZeroAndSetsTimezoneWhenSupplied() {
        val message = ApiReports.timeReport(
            unixTimeMs = 1_765_000_000_000L,
            timezoneId = "America/Chicago"
        )

        assertEquals(0L, message.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.TIME_REPORT, message.payloadCase)
        assertEquals(1_765_000_000_000L, message.timeReport.unixTimeMs)
        assertTrue(message.timeReport.hasTimezoneId())
        assertEquals("America/Chicago", message.timeReport.timezoneId)
    }

    @Test
    fun timeReport_omitsTimezoneWhenMissingOrBlank() {
        val missing = ApiReports.timeReport(unixTimeMs = 1_765_000_000_000L)
        val blank = ApiReports.timeReport(
            unixTimeMs = 1_765_000_000_000L,
            timezoneId = "   "
        )

        assertFalse(missing.timeReport.hasTimezoneId())
        assertFalse(blank.timeReport.hasTimezoneId())
    }

    @Test
    fun timeReport_rejectsNonpositiveUnixTime() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiReports.timeReport(unixTimeMs = 0)
        }
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
    fun gpsReport_rejectsMalformedCoordinatesAndMotionFields() {
        fun valid(
            latitude: Double = 41.881,
            longitude: Double = -87.623,
            speedMps: Double = 1.0,
            bearingDeg: Double = 90.0,
            accuracyM: Double = 2.0,
            altitudeM: Double? = null
        ) = ApiReports.gpsReport(
            latitude = latitude,
            longitude = longitude,
            speedMps = speedMps,
            bearingDeg = bearingDeg,
            accuracyM = accuracyM,
            ageMs = 0,
            altitudeM = altitudeM
        )

        listOf<() -> Unit>(
            { valid(latitude = Double.NaN) },
            { valid(latitude = 91.0) },
            { valid(longitude = -181.0) },
            { valid(speedMps = -1.0) },
            { valid(speedMps = Double.POSITIVE_INFINITY) },
            { valid(bearingDeg = 360.0) },
            { valid(accuracyM = -0.1) },
            { valid(altitudeM = Double.NaN) }
        ).forEach { call ->
            assertThrows(IllegalArgumentException::class.java) { call() }
        }
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

    @Test
    fun connectivityReport_enforcesActiveAndInactivePortShape() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiReports.connectivityReport(true, socks5Active = true, socks5Port = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiReports.connectivityReport(false, socks5Active = false, socks5Port = 1080)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiReports.connectivityReport(
                internetAvailable = false,
                socks5Active = false,
                socks5Port = 0,
                socks5Password = "should-not-be-sent"
            )
        }
    }
}
