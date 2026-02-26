package org.openauto.companion.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleIdentityTest {
    @Test
    fun resolvesVehicleId_whenProvided() {
        val actual = VehicleIdentity.resolve("id-123", "OpenAutoProdigy")
        assertEquals("id-123", actual)
    }

    @Test
    fun resolvesVehicleSsid_whenVehicleIdMissing() {
        val actual = VehicleIdentity.resolve("", "OpenAutoProdigy")
        assertEquals("OpenAutoProdigy", actual)
    }

    @Test
    fun resolvesTrimmedValues() {
        val actual = VehicleIdentity.resolve("   ", "  OpenAutoProdigy  ")
        assertEquals("OpenAutoProdigy", actual)
    }

    @Test
    fun returnsBlank_whenIdAndSsidMissing() {
        val actual = VehicleIdentity.resolve("   ", "   ")
        assertEquals("", actual)
    }

    @Test
    fun matchesByIdOrSsid() {
        assertTrue(VehicleIdentity.matches("id-123", "OpenAutoProdigy", "id-123"))
        assertTrue(VehicleIdentity.matches("id-123", "OpenAutoProdigy", "OpenAutoProdigy"))
        assertFalse(VehicleIdentity.matches("id-123", "OpenAutoProdigy", "other"))
    }
}
