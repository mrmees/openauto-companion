package org.openauto.companion.net.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import prodigy.api.v1.Api

class ApiReportPublisherTest {
    private var elapsedRealtimeMs = 1_000L

    @Test
    fun readyReplay_ordersConnectivityTimeBatteryThenGps() = runTest {
        val publisher = publisher()
        publisher.updateBattery(ApiReportPublisher.BatterySnapshot(82, charging = true))
        publisher.updateGps(sampleGps(ageMs = 120))
        publisher.updateConnectivity(
            ApiReportPublisher.ConnectivitySnapshot(
                internetAvailable = true,
                socks5Active = true,
                socks5Port = 1080,
                socks5Password = "proxy-pass"
            )
        )
        val sent = Channel<Api.ApiMessage>(Channel.UNLIMITED)

        val job = backgroundScope.launch {
            publisher.runReadySession { sent.send(it) }
        }

        assertEquals(
            listOf(
                Api.ApiMessage.PayloadCase.CONNECTIVITY_REPORT,
                Api.ApiMessage.PayloadCase.TIME_REPORT,
                Api.ApiMessage.PayloadCase.BATTERY_REPORT,
                Api.ApiMessage.PayloadCase.GPS_REPORT
            ),
            receive(sent, 4).map { it.payloadCase }
        )
        job.cancelAndJoin()
    }

    @Test
    fun readyReplay_withoutGpsDoesNotCreateFakeFix() = runTest {
        val publisher = publisher()
        val sent = Channel<Api.ApiMessage>(Channel.UNLIMITED)

        val job = backgroundScope.launch {
            publisher.runReadySession { sent.send(it) }
        }

        assertEquals(
            listOf(
                Api.ApiMessage.PayloadCase.CONNECTIVITY_REPORT,
                Api.ApiMessage.PayloadCase.TIME_REPORT
            ),
            receive(sent, 2).map { it.payloadCase }
        )
        assertNull(withTimeoutOrNull(10) { sent.receive() })
        job.cancelAndJoin()
    }

    @Test
    fun identicalBatteryAndConnectivityUpdatesAreSuppressedWithinSession() = runTest {
        val publisher = publisher()
        val battery = ApiReportPublisher.BatterySnapshot(50, charging = false)
        val connectivity = ApiReportPublisher.ConnectivitySnapshot(
            internetAvailable = true,
            socks5Active = false,
            socks5Port = 0,
            socks5Password = null
        )
        publisher.updateBattery(battery)
        publisher.updateConnectivity(connectivity)
        val sent = Channel<Api.ApiMessage>(Channel.UNLIMITED)
        val job = backgroundScope.launch {
            publisher.runReadySession { sent.send(it) }
        }
        receive(sent, 3)

        publisher.updateBattery(battery)
        publisher.updateConnectivity(connectivity)

        assertNull(withTimeoutOrNull(10) { sent.receive() })
        job.cancelAndJoin()
    }

    @Test
    fun updatesConflateIndependentlyWhileWriterIsBusy() = runTest {
        val publisher = publisher()
        val firstBatteryStarted = CompletableDeferred<Unit>()
        val releaseFirstBattery = CompletableDeferred<Unit>()
        val sent = Channel<Api.ApiMessage>(Channel.UNLIMITED)
        val job = backgroundScope.launch {
            publisher.runReadySession { message ->
                if (message.payloadCase == Api.ApiMessage.PayloadCase.BATTERY_REPORT &&
                    message.batteryReport.percent == 1
                ) {
                    firstBatteryStarted.complete(Unit)
                    releaseFirstBattery.await()
                }
                sent.send(message)
            }
        }
        receive(sent, 2)

        publisher.updateBattery(ApiReportPublisher.BatterySnapshot(1, charging = false))
        firstBatteryStarted.await()
        publisher.updateBattery(ApiReportPublisher.BatterySnapshot(2, charging = false))
        publisher.updateBattery(ApiReportPublisher.BatterySnapshot(3, charging = true))
        publisher.updateConnectivity(
            ApiReportPublisher.ConnectivitySnapshot(true, true, 1080, "old")
        )
        publisher.updateConnectivity(
            ApiReportPublisher.ConnectivitySnapshot(true, true, 1080, "new")
        )
        releaseFirstBattery.complete(Unit)

        val updates = receive(sent, 3)
        assertEquals(1, updates[0].batteryReport.percent)
        assertEquals("new", updates[1].connectivityReport.socks5Password)
        assertEquals(3, updates[2].batteryReport.percent)
        assertTrue(updates[2].batteryReport.charging)
        job.cancelAndJoin()
    }

    @Test
    fun newReadySessionReplaysLatestSnapshotsEvenWhenUnchanged() = runTest {
        val publisher = publisher()
        publisher.updateBattery(ApiReportPublisher.BatterySnapshot(90, charging = true))

        suspend fun collectOneSession(): List<Api.ApiMessage> {
            val sent = Channel<Api.ApiMessage>(Channel.UNLIMITED)
            val job = backgroundScope.launch {
                publisher.runReadySession { sent.send(it) }
            }
            val result = receive(sent, 3)
            job.cancelAndJoin()
            return result
        }

        val first = collectOneSession()
        val second = collectOneSession()

        assertEquals(first.map { it.payloadCase }, second.map { it.payloadCase })
        assertEquals(90, second.last().batteryReport.percent)
    }

    @Test
    fun replayComputesGpsAgeAtSendTime() = runTest {
        val publisher = publisher()
        publisher.updateGps(sampleGps(ageMs = 120))
        elapsedRealtimeMs += 30 * 60 * 1_000L
        val sent = Channel<Api.ApiMessage>(Channel.UNLIMITED)
        val job = backgroundScope.launch {
            publisher.runReadySession { sent.send(it) }
        }

        val gps = receive(sent, 3).last().gpsReport

        assertEquals(30 * 60 * 1_000 + 120, gps.ageMs)
        job.cancelAndJoin()
    }

    @Test
    fun writerFailureEscapesReadySession() = runTest {
        val publisher = publisher()
        val error = IllegalStateException("send failed")

        try {
            publisher.runReadySession { throw error }
            throw AssertionError("Expected writer failure")
        } catch (actual: IllegalStateException) {
            assertSame(error, actual)
        }
    }

    private fun publisher() = ApiReportPublisher(
        currentTimeMs = { 1_765_000_000_000L },
        timezoneId = { "America/Chicago" },
        elapsedRealtimeMs = { elapsedRealtimeMs }
    )

    private fun sampleGps(ageMs: Int) = ApiReportPublisher.GpsSnapshot(
        latitude = 41.881,
        longitude = -87.623,
        speedMps = 12.5,
        bearingDeg = 180.0,
        accuracyM = 3.0,
        ageMs = ageMs,
        capturedAtElapsedRealtimeMs = elapsedRealtimeMs,
        altitudeM = 181.2
    )

    private suspend fun receive(
        channel: Channel<Api.ApiMessage>,
        count: Int
    ): List<Api.ApiMessage> =
        (0 until count).map { withTimeout(1_000) { channel.receive() } }
}
