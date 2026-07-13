package org.openauto.companion.net.api

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import prodigy.api.v1.Api

class ApiReportPublisher(
    private val currentTimeMs: () -> Long,
    private val timezoneId: () -> String?
) {
    data class GpsSnapshot(
        val latitude: Double,
        val longitude: Double,
        val speedMps: Double,
        val bearingDeg: Double,
        val accuracyM: Double,
        val ageMs: Int,
        val altitudeM: Double? = null
    )

    data class BatterySnapshot(
        val percent: Int,
        val charging: Boolean
    )

    data class ConnectivitySnapshot(
        val internetAvailable: Boolean,
        val socks5Active: Boolean,
        val socks5Port: Int,
        val socks5Password: String?
    )

    private val lock = Any()
    private val sessionMutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private var gps: GpsSnapshot? = null
    private var gpsVersion = 0L
    private var battery: BatterySnapshot? = null
    private var batteryVersion = 0L
    private var connectivity = ConnectivitySnapshot(
        internetAvailable = false,
        socks5Active = false,
        socks5Port = 0,
        socks5Password = null
    )
    private var connectivityVersion = 0L
    private var timeVersion = 0L

    fun updateGps(snapshot: GpsSnapshot?) {
        synchronized(lock) {
            if (gps == snapshot) return
            gps = snapshot
            gpsVersion += 1
        }
        wake.trySend(Unit)
    }

    fun updateBattery(snapshot: BatterySnapshot?) {
        synchronized(lock) {
            if (battery == snapshot) return
            battery = snapshot
            batteryVersion += 1
        }
        wake.trySend(Unit)
    }

    fun updateConnectivity(snapshot: ConnectivitySnapshot) {
        synchronized(lock) {
            if (connectivity == snapshot) return
            connectivity = snapshot
            connectivityVersion += 1
        }
        wake.trySend(Unit)
    }

    fun notifyTimeChanged() {
        synchronized(lock) {
            timeVersion += 1
        }
        wake.trySend(Unit)
    }

    suspend fun runReadySession(send: suspend (Api.ApiMessage) -> Unit) {
        sessionMutex.withLock {
            var sentGpsVersion = -1L
            var sentBatteryVersion = -1L
            var sentConnectivityVersion = -1L
            var sentTimeVersion = -1L

            suspend fun sendConnectivityIfChanged() {
                val (snapshot, version) = synchronized(lock) {
                    connectivity to connectivityVersion
                }
                if (version == sentConnectivityVersion) return
                send(
                    ApiReports.connectivityReport(
                        internetAvailable = snapshot.internetAvailable,
                        socks5Active = snapshot.socks5Active,
                        socks5Port = snapshot.socks5Port,
                        socks5Password = snapshot.socks5Password
                    )
                )
                sentConnectivityVersion = version
            }

            suspend fun sendTimeIfChanged() {
                val version = synchronized(lock) { timeVersion }
                if (version == sentTimeVersion) return
                send(
                    ApiReports.timeReport(
                        unixTimeMs = currentTimeMs(),
                        timezoneId = timezoneId()
                    )
                )
                sentTimeVersion = version
            }

            suspend fun sendBatteryIfChanged() {
                val (snapshot, version) = synchronized(lock) {
                    battery to batteryVersion
                }
                if (version == sentBatteryVersion) return
                if (snapshot != null) {
                    send(
                        ApiReports.batteryReport(
                            percent = snapshot.percent,
                            charging = snapshot.charging
                        )
                    )
                }
                sentBatteryVersion = version
            }

            suspend fun sendGpsIfChanged() {
                val (snapshot, version) = synchronized(lock) {
                    gps to gpsVersion
                }
                if (version == sentGpsVersion) return
                if (snapshot != null) {
                    send(
                        ApiReports.gpsReport(
                            latitude = snapshot.latitude,
                            longitude = snapshot.longitude,
                            speedMps = snapshot.speedMps,
                            bearingDeg = snapshot.bearingDeg,
                            accuracyM = snapshot.accuracyM,
                            ageMs = snapshot.ageMs,
                            altitudeM = snapshot.altitudeM
                        )
                    )
                }
                sentGpsVersion = version
            }

            suspend fun sendPending() {
                sendConnectivityIfChanged()
                sendTimeIfChanged()
                sendBatteryIfChanged()
                sendGpsIfChanged()
            }

            sendPending()
            while (currentCoroutineContext().isActive) {
                wake.receive()
                sendPending()
            }
        }
    }
}
