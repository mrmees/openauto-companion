package org.openauto.companion.service

import org.openauto.companion.net.api.ApiReportPublisher

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val accuracyM: Double? = null,
    val altitudeM: Double? = null,
    val elapsedRealtimeNanos: Long
)

class LocationReportMapper(
    private val minIntervalMs: Long = 1_000L
) {
    private var lastAcceptedAtNanos: Long? = null

    init {
        require(minIntervalMs >= 0L) { "Minimum interval must be non-negative" }
    }

    fun map(
        sample: LocationSample?,
        nowElapsedRealtimeNanos: Long
    ): ApiReportPublisher.GpsSnapshot? {
        sample ?: return null
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0) return null
        if (!sample.longitude.isFinite() || sample.longitude !in -180.0..180.0) return null

        val previous = lastAcceptedAtNanos
        val minimumNanos = millisecondsToNanos(minIntervalMs)
        if (previous != null && elapsedNanos(previous, nowElapsedRealtimeNanos) < minimumNanos) {
            return null
        }

        val ageMs = (elapsedNanos(sample.elapsedRealtimeNanos, nowElapsedRealtimeNanos) /
            NANOS_PER_MILLISECOND)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val speed = sample.speedMps
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: 0.0
        val bearing = sample.bearingDeg
            ?.takeIf { it.isFinite() }
            ?.let(::normalizeBearing)
            ?: 0.0
        val accuracy = sample.accuracyM
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: 0.0
        val altitude = sample.altitudeM?.takeIf { it.isFinite() }

        lastAcceptedAtNanos = nowElapsedRealtimeNanos
        return ApiReportPublisher.GpsSnapshot(
            latitude = sample.latitude,
            longitude = sample.longitude,
            speedMps = speed,
            bearingDeg = bearing,
            accuracyM = accuracy,
            ageMs = ageMs,
            capturedAtElapsedRealtimeMs = nowElapsedRealtimeNanos / NANOS_PER_MILLISECOND,
            altitudeM = altitude
        )
    }

    private fun normalizeBearing(value: Double): Double {
        val remainder = value % 360.0
        return if (remainder < 0.0) remainder + 360.0 else remainder
    }

    private fun elapsedNanos(earlier: Long, later: Long): Long {
        if (later <= earlier) return 0L
        val elapsed = later - earlier
        return if (elapsed < 0L) Long.MAX_VALUE else elapsed
    }

    private fun millisecondsToNanos(milliseconds: Long): Long {
        if (milliseconds > Long.MAX_VALUE / NANOS_PER_MILLISECOND) return Long.MAX_VALUE
        return milliseconds * NANOS_PER_MILLISECOND
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
