package org.openauto.companion.data

import org.junit.Assert.*
import org.junit.Test

class VehicleSerializationTest {
    @Test
    fun roundTrip_singleVehicle() {
        val v = Vehicle(id = "abc123", ssid = "MiataAA-A3F2", name = "Miata",
            sharedSecret = "deadbeef", socks5Enabled = true)
        val json = Vehicle.listToJson(listOf(v))
        val result = Vehicle.listFromJson(json)
        assertEquals(1, result.size)
        assertEquals("abc123", result[0].id)
        assertEquals("MiataAA-A3F2", result[0].ssid)
        assertEquals("Miata", result[0].name)
        assertEquals("deadbeef", result[0].sharedSecret)
        assertTrue(result[0].socks5Enabled)
    }

    @Test
    fun roundTrip_multipleVehicles() {
        val vehicles = listOf(
            Vehicle(id = "a1", ssid = "Car1", sharedSecret = "s1"),
            Vehicle(id = "b2", ssid = "Car2", name = "Truck", sharedSecret = "s2", socks5Enabled = false)
        )
        val result = Vehicle.listFromJson(Vehicle.listToJson(vehicles))
        assertEquals(2, result.size)
        assertEquals("Car2", result[1].ssid)
        assertEquals("Truck", result[1].name)
        assertFalse(result[1].socks5Enabled)
    }

    @Test
    fun fromJson_defaultsNameToSsid() {
        val json = org.json.JSONObject().apply {
            put("ssid", "TestAP")
            put("shared_secret", "abc")
        }
        val v = Vehicle.fromJson(json)
        assertEquals("TestAP", v.name)
    }

    @Test
    fun emptyJson_returnsEmptyList() {
        assertEquals(emptyList<Vehicle>(), Vehicle.listFromJson(""))
    }
}
