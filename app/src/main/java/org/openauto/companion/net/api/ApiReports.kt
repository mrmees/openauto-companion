package org.openauto.companion.net.api

import prodigy.api.v1.Api
import prodigy.api.v1.Companion as CompanionProto

object ApiReports {
    fun timeReport(unixTimeMs: Long): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setRequestId(0)
            .setTimeReport(
                CompanionProto.TimeReport.newBuilder()
                    .setUnixTimeMs(unixTimeMs)
                    .build()
            )
            .build()

    fun gpsReport(
        latitude: Double,
        longitude: Double,
        speedMps: Double,
        bearingDeg: Double,
        accuracyM: Double,
        ageMs: Int,
        altitudeM: Double? = null
    ): Api.ApiMessage {
        require(ageMs >= 0) { "GPS age must be non-negative" }
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
