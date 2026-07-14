package org.openauto.companion.net.api

import prodigy.api.v1.Api
import prodigy.api.v1.Companion as CompanionProto

object ApiReports {
    fun timeReport(unixTimeMs: Long, timezoneId: String? = null): Api.ApiMessage {
        require(unixTimeMs > 0) { "Unix time must be positive" }
        val report = CompanionProto.TimeReport.newBuilder()
            .setUnixTimeMs(unixTimeMs)
            .apply {
                if (!timezoneId.isNullOrBlank()) setTimezoneId(timezoneId)
            }
            .build()

        return Api.ApiMessage.newBuilder()
            .setRequestId(0)
            .setTimeReport(report)
            .build()
    }

    fun gpsReport(
        latitude: Double,
        longitude: Double,
        speedMps: Double,
        bearingDeg: Double,
        accuracyM: Double,
        ageMs: Int,
        altitudeM: Double? = null
    ): Api.ApiMessage {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "GPS latitude must be finite and within -90..90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "GPS longitude must be finite and within -180..180"
        }
        require(speedMps.isFinite() && speedMps >= 0.0) {
            "GPS speed must be finite and non-negative"
        }
        require(bearingDeg.isFinite() && bearingDeg >= 0.0 && bearingDeg < 360.0) {
            "GPS bearing must be finite and within 0..<360"
        }
        require(accuracyM.isFinite() && accuracyM >= 0.0) {
            "GPS accuracy must be finite and non-negative"
        }
        require(ageMs >= 0) { "GPS age must be non-negative" }
        require(altitudeM == null || altitudeM.isFinite()) {
            "GPS altitude must be finite when supplied"
        }
        val report = CompanionProto.GpsReport.newBuilder()
            .setLatitude(latitude)
            .setLongitude(longitude)
            .setSpeedMps(speedMps)
            .setBearingDeg(bearingDeg)
            .setAccuracyM(accuracyM)
            .setAgeMs(ageMs)
            .apply {
                if (altitudeM != null) setAltitudeM(altitudeM)
            }
            .build()

        return Api.ApiMessage.newBuilder()
            .setRequestId(0)
            .setGpsReport(report)
            .build()
    }

    fun batteryReport(percent: Int, charging: Boolean): Api.ApiMessage {
        require(percent in 0..100) { "Battery percent must be 0-100" }
        return Api.ApiMessage.newBuilder()
            .setRequestId(0)
            .setBatteryReport(
                CompanionProto.BatteryReport.newBuilder()
                    .setPercent(percent)
                    .setCharging(charging)
                    .build()
            )
            .build()
    }

    fun connectivityReport(
        internetAvailable: Boolean,
        socks5Active: Boolean,
        socks5Port: Int,
        socks5Password: String? = null
    ): Api.ApiMessage {
        require(socks5Port in 0..65535) { "SOCKS5 port must be 0-65535" }
        if (socks5Active) {
            require(socks5Port in 1..65535) { "Active SOCKS5 port must be nonzero" }
        } else {
            require(socks5Port == 0) { "Inactive SOCKS5 port must be zero" }
            require(socks5Password == null) { "Inactive SOCKS5 report must omit password" }
        }
        val report = CompanionProto.ConnectivityReport.newBuilder()
            .setInternetAvailable(internetAvailable)
            .setSocks5Active(socks5Active)
            .setSocks5Port(socks5Port)
            .apply {
                if (socks5Password != null) setSocks5Password(socks5Password)
            }
            .build()

        return Api.ApiMessage.newBuilder()
            .setRequestId(0)
            .setConnectivityReport(report)
            .build()
    }
}
