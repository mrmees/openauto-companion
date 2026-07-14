package org.openauto.companion.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSerializationTest {
    @Test
    fun roundTrip_preservesCompleteApiVehicleWithoutLegacyFields() {
        val vehicle = vehicle(
            id = "abc123",
            ssid = "MiataAA-A3F2",
            name = "Miata",
            serverId = "server-1",
            apiTcpPort = 19810,
            socks5Enabled = false,
            audioKeepAlive = true,
            settingsHost = "10.0.0.42",
            settingsPort = 8080,
            displayWidth = 1280,
            displayHeight = 720
        )

        val json = vehicle.toJson()
        val result = Vehicle.listFromJson(Vehicle.listToJson(listOf(vehicle))).single()

        assertFalse(json.has("shared_secret"))
        assertFalse(json.has("api_mode"))
        assertEquals(vehicle, result)
    }

    @Test
    fun roundTrip_multipleVehicles() {
        val vehicles = listOf(
            vehicle(id = "a1", ssid = "Car1"),
            vehicle(id = "b2", ssid = "Car2", name = "Truck", socks5Enabled = false)
        )

        val result = Vehicle.listFromJson(Vehicle.listToJson(vehicles))

        assertEquals(vehicles, result)
    }

    @Test
    fun fromJson_defaultsOptionalFieldsAndName() {
        val json = requiredJson(ssid = "TestAP")

        val vehicle = Vehicle.fromJson(json)

        assertEquals("TestAP", vehicle.name)
        assertTrue(vehicle.socks5Enabled)
        assertFalse(vehicle.audioKeepAlive)
        assertNull(vehicle.serverId)
        assertEquals(9810, vehicle.apiTcpPort)
        assertNull(vehicle.settingsHost)
        assertNull(vehicle.settingsPort)
        assertNull(vehicle.displayWidth)
        assertNull(vehicle.displayHeight)
    }

    @Test
    fun fromJson_generatesStableIdFromSsidWhenMissing() {
        val json = requiredJson(ssid = "BridgeAP")

        val first = Vehicle.fromJson(json).id
        val second = Vehicle.fromJson(json).id

        assertEquals(first, second)
        assertFalse(first.isBlank())
    }

    @Test
    fun fromJson_rejectsMissingOrInvalidRequiredApiCredentials() {
        val missingClient = requiredJson().apply { remove("api_client_id") }
        val blankClient = requiredJson().put("api_client_id", " ")
        val missingSecret = requiredJson().apply { remove("api_secret_hex") }
        val malformedSecret = requiredJson().put("api_secret_hex", "zz".repeat(32))
        val shortSecret = requiredJson().put("api_secret_hex", "aa".repeat(31))
        val invalidPort = requiredJson().put("api_tcp_port", 0)

        listOf(
            missingClient,
            blankClient,
            missingSecret,
            malformedSecret,
            shortSecret,
            invalidPort
        )
            .forEach { json ->
                assertThrows(Exception::class.java) { Vehicle.fromJson(json) }
            }
    }

    @Test
    fun emptyJson_returnsEmptyList() {
        assertEquals(emptyList<Vehicle>(), Vehicle.listFromJson(""))
    }

    private fun vehicle(
        id: String,
        ssid: String,
        name: String = ssid,
        serverId: String? = null,
        apiTcpPort: Int = 9810,
        socks5Enabled: Boolean = true,
        audioKeepAlive: Boolean = false,
        settingsHost: String? = null,
        settingsPort: Int? = null,
        displayWidth: Int? = null,
        displayHeight: Int? = null
    ) = Vehicle(
        id = id,
        ssid = ssid,
        name = name,
        apiClientId = "client-$id",
        apiSecretHex = "ab".repeat(32),
        serverId = serverId,
        apiTcpPort = apiTcpPort,
        socks5Enabled = socks5Enabled,
        audioKeepAlive = audioKeepAlive,
        settingsHost = settingsHost,
        settingsPort = settingsPort,
        displayWidth = displayWidth,
        displayHeight = displayHeight
    )

    private fun requiredJson(ssid: String = "ApiAP"): JSONObject = JSONObject()
        .put("ssid", ssid)
        .put("api_client_id", "client-123")
        .put("api_secret_hex", "ab".repeat(32))
}
