package org.openauto.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MonitorSlotTest {
    @Test
    fun replaceQuietlyStopsOnlyThePreviousMonitor() {
        val slot = MonitorSlot<FakeMonitor>()
        val first = FakeMonitor()
        val second = FakeMonitor()

        slot.replace(first)
        slot.replace(second)

        assertEquals(1, first.startCount)
        assertEquals(listOf(false), first.stopServiceRequests)
        assertEquals(1, second.startCount)
        assertEquals(emptyList<Boolean>(), second.stopServiceRequests)
        assertSame(second, slot.current)
    }

    @Test
    fun stopCurrentAlsoStopsTheCompanionService() {
        val slot = MonitorSlot<FakeMonitor>()
        val monitor = FakeMonitor()
        slot.replace(monitor)

        slot.stopCurrent()

        assertEquals(listOf(true), monitor.stopServiceRequests)
        assertNull(slot.current)
    }

    private class FakeMonitor : MonitorLifecycle {
        var startCount = 0
        val stopServiceRequests = mutableListOf<Boolean>()

        override fun start() {
            startCount += 1
        }

        override fun stop(stopService: Boolean) {
            stopServiceRequests += stopService
        }
    }
}
