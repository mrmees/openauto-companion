package org.openauto.companion.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationReportMapperTest {
    @Test
    fun mapsRealCoordinatesAndOptionalMeasurements() {
        val mapper = LocationReportMapper()

        val snapshot = mapper.map(
            sample = LocationSample(
                latitude = 41.881,
                longitude = -87.623,
                speedMps = 12.5,
                bearingDeg = 725.0,
                accuracyM = 3.5,
                altitudeM = 181.2,
                elapsedRealtimeNanos = 5_000_000_000L
            ),
            nowElapsedRealtimeNanos = 5_125_000_000L
        )!!

        assertEquals(41.881, snapshot.latitude, 0.0)
        assertEquals(-87.623, snapshot.longitude, 0.0)
        assertEquals(12.5, snapshot.speedMps, 0.0)
        assertEquals(5.0, snapshot.bearingDeg, 0.0)
        assertEquals(3.5, snapshot.accuracyM, 0.0)
        assertEquals(125, snapshot.ageMs)
        assertEquals(5_125L, snapshot.capturedAtElapsedRealtimeMs)
        assertEquals(181.2, snapshot.altitudeM!!, 0.0)
    }

    @Test
    fun missingOrInvalidOptionalMeasurementsUseSafeDefaults() {
        val mapper = LocationReportMapper()

        val snapshot = mapper.map(
            sample = LocationSample(
                latitude = 1.0,
                longitude = 2.0,
                speedMps = Double.NaN,
                bearingDeg = Double.POSITIVE_INFINITY,
                accuracyM = -1.0,
                altitudeM = Double.NaN,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            nowElapsedRealtimeNanos = 1_000_000_000L
        )!!

        assertEquals(0.0, snapshot.speedMps, 0.0)
        assertEquals(0.0, snapshot.bearingDeg, 0.0)
        assertEquals(0.0, snapshot.accuracyM, 0.0)
        assertEquals(0, snapshot.ageMs)
        assertNull(snapshot.altitudeM)
    }

    @Test
    fun missingLocationAndInvalidCoordinatesProduceNoSnapshot() {
        val mapper = LocationReportMapper()

        assertNull(mapper.map(null, nowElapsedRealtimeNanos = 1_000_000_000L))
        assertNull(
            mapper.map(
                LocationSample(
                    latitude = 91.0,
                    longitude = 2.0,
                    elapsedRealtimeNanos = 1L
                ),
                nowElapsedRealtimeNanos = 1_000_000_000L
            )
        )
        assertNull(
            mapper.map(
                LocationSample(
                    latitude = 1.0,
                    longitude = Double.NaN,
                    elapsedRealtimeNanos = 1L
                ),
                nowElapsedRealtimeNanos = 1_000_000_000L
            )
        )
    }

    @Test
    fun throttlesAcceptedCallbacksToOnePerSecond() {
        val mapper = LocationReportMapper(minIntervalMs = 1_000L)
        val sample = LocationSample(
            latitude = 41.0,
            longitude = -87.0,
            elapsedRealtimeNanos = 1_000_000_000L
        )

        val first = mapper.map(sample, nowElapsedRealtimeNanos = 2_000_000_000L)
        val tooSoon = mapper.map(sample, nowElapsedRealtimeNanos = 2_999_999_999L)
        val next = mapper.map(sample, nowElapsedRealtimeNanos = 3_000_000_000L)

        assertEquals(1_000, first!!.ageMs)
        assertNull(tooSoon)
        assertEquals(2_000, next!!.ageMs)
    }

    @Test
    fun clampsVeryOldFixAgeToSupportedIntegerRange() {
        val mapper = LocationReportMapper()

        val snapshot = mapper.map(
            LocationSample(
                latitude = 0.0,
                longitude = 0.0,
                elapsedRealtimeNanos = 0L
            ),
            nowElapsedRealtimeNanos = Long.MAX_VALUE
        )!!

        assertEquals(Int.MAX_VALUE, snapshot.ageMs)
    }
}
