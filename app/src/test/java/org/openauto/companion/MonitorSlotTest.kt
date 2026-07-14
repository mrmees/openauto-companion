package org.openauto.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MonitorSlotTest {
    @Test
    fun startIfAbsentReusesTheExistingMonitor() {
        val slot = MonitorSlot<FakeMonitor>()
        val first = FakeMonitor()
        val second = FakeMonitor()

        slot.startIfAbsent(first)
        slot.startIfAbsent(second)

        assertEquals(1, first.startCount)
        assertEquals(0, first.stopCount)
        assertEquals(0, second.startCount)
        assertEquals(0, second.stopCount)
        assertSame(first, slot.current)
    }

    @Test
    fun stopCurrentAlsoStopsTheCompanionService() {
        val slot = MonitorSlot<FakeMonitor>()
        val monitor = FakeMonitor()
        slot.startIfAbsent(monitor)

        slot.stopCurrent()

        assertEquals(1, monitor.stopCount)
        assertNull(slot.current)
    }

    private class FakeMonitor : MonitorLifecycle {
        var startCount = 0
        var stopCount = 0

        override fun start() {
            startCount += 1
        }

        override fun stop() {
            stopCount += 1
        }
    }
}
