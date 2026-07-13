package org.openauto.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellularUpstreamStateTest {
    @Test
    fun usableNetworkBecomesSelectedAndCapabilityLossClearsIt() {
        val changes = mutableListOf<String?>()
        val state = CellularUpstreamState<String> { changes += it }

        state.update("cell-a", usable = true)
        state.update("cell-a", usable = false)

        assertNull(state.selected)
        assertEquals(listOf("cell-a", null), changes)
    }

    @Test
    fun losingNonSelectedNetworkLeavesSelectionAlone() {
        val changes = mutableListOf<String?>()
        val state = CellularUpstreamState<String> { changes += it }
        state.update("cell-a", usable = true)
        state.update("cell-b", usable = true)
        changes.clear()

        state.remove("cell-b")

        assertEquals("cell-a", state.selected)
        assertEquals(emptyList<String?>(), changes)
    }

    @Test
    fun losingSelectedNetworkPromotesRemainingUsableNetwork() {
        val changes = mutableListOf<String?>()
        val state = CellularUpstreamState<String> { changes += it }
        state.update("cell-a", usable = true)
        state.update("cell-b", usable = true)
        changes.clear()

        state.remove("cell-a")

        assertEquals("cell-b", state.selected)
        assertEquals(listOf("cell-b"), changes)
    }

    @Test
    fun resetClearsSelectionOnceAndIsIdempotent() {
        val changes = mutableListOf<String?>()
        val state = CellularUpstreamState<String> { changes += it }
        state.update("cell-a", usable = true)
        changes.clear()

        state.reset()
        state.reset()

        assertNull(state.selected)
        assertEquals(listOf<String?>(null), changes)
    }
}
