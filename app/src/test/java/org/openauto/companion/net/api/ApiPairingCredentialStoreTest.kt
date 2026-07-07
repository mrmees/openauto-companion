package org.openauto.companion.net.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openauto.companion.data.Vehicle
import prodigy.api.v1.Api
import prodigy.api.v1.System as SystemProto

class ApiPairingCredentialStoreTest {
    @Test
    fun persistPairingResult_updatesMatchedVehicleWithV1Credentials() {
        val secret = ByteArray(32) { it.toByte() }
        val initial = listOf(
            Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy-secret"),
            Vehicle(id = "veh-2", ssid = "OtherAP", sharedSecret = "other-secret")
        )
        var saved: List<Vehicle>? = null
        val store = ApiPairingCredentialStore(
            loadVehicles = { initial },
            saveVehicles = { saved = it }
        )

        val result = store.persistPairingResult(
            vehicleId = "veh-1",
            ready = readyWithCredentials(
                clientId = "client-123",
                secret = secret,
                serverId = "server-uuid-1"
            )
        )

        assertTrue(result)
        val updated = saved!!.first { it.id == "veh-1" }
        assertEquals("legacy-secret", updated.sharedSecret)
        assertEquals("client-123", updated.apiClientId)
        assertEquals(ApiCrypto.toHex(secret), updated.apiSecretHex)
        assertEquals(Vehicle.ApiMode.EXTERNAL_API_V1, updated.apiMode)
        assertEquals("server-uuid-1", updated.serverId)
        assertEquals(Vehicle.ApiMode.LEGACY, saved!!.first { it.id == "veh-2" }.apiMode)
    }

    @Test
    fun persistPairingResult_returnsFalseWhenReadyHasNoPairingCredentials() {
        var saved = false
        val store = ApiPairingCredentialStore(
            loadVehicles = { listOf(Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy")) },
            saveVehicles = { saved = true }
        )

        val result = store.persistPairingResult(
            vehicleId = "veh-1",
            ready = ApiSessionClient.ConnectResult.Ready(
                serverHello = serverHello(),
                pairedCredentials = null
            )
        )

        assertFalse(result)
        assertFalse(saved)
    }

    @Test
    fun persistPairingResult_returnsFalseWhenVehicleIsMissing() {
        var saved = false
        val store = ApiPairingCredentialStore(
            loadVehicles = { listOf(Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy")) },
            saveVehicles = { saved = true }
        )

        val result = store.persistPairingResult(
            vehicleId = "unknown",
            ready = readyWithCredentials("client-123", ByteArray(32) { it.toByte() })
        )

        assertFalse(result)
        assertFalse(saved)
    }

    @Test
    fun persistPairingResult_returnsFalseForInvalidSecretSize() {
        val original = Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy")
        var saved: List<Vehicle>? = null
        val store = ApiPairingCredentialStore(
            loadVehicles = { listOf(original) },
            saveVehicles = { saved = it }
        )

        val result = store.persistPairingResult(
            vehicleId = "veh-1",
            ready = readyWithCredentials("client-123", ByteArray(31) { it.toByte() })
        )

        assertFalse(result)
        assertNull(saved)
    }

    @Test
    fun persistSystemStatus_updatesDisplayDimensionsForMatchedVehicle() {
        val initial = Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy")
        var saved: List<Vehicle>? = null
        val store = ApiPairingCredentialStore(
            loadVehicles = { listOf(initial) },
            saveVehicles = { saved = it }
        )

        val result = store.persistSystemStatus(
            vehicleId = "veh-1",
            systemStatus = SystemProto.SystemStatus.newBuilder()
                .setDisplayWidth(1024)
                .setDisplayHeight(600)
                .build()
        )

        assertTrue(result)
        assertEquals(1024, saved!!.single().displayWidth)
        assertEquals(600, saved!!.single().displayHeight)
    }

    @Test
    fun persistSystemStatus_returnsFalseWhenDimensionsAreIncompleteOrInvalid() {
        val initial = Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy")
        var saved = false
        val store = ApiPairingCredentialStore(
            loadVehicles = { listOf(initial) },
            saveVehicles = { saved = true }
        )

        val missingHeight = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(1024)
            .build()
        val zeroWidth = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(0)
            .setDisplayHeight(600)
            .build()

        assertFalse(store.persistSystemStatus("veh-1", missingHeight))
        assertFalse(store.persistSystemStatus("veh-1", zeroWidth))
        assertFalse(saved)
    }

    @Test
    fun persistSystemStatus_returnsFalseWhenVehicleIsMissing() {
        var saved = false
        val store = ApiPairingCredentialStore(
            loadVehicles = {
                listOf(Vehicle(id = "veh-1", ssid = "CarAP", sharedSecret = "legacy"))
            },
            saveVehicles = { saved = true }
        )

        val result = store.persistSystemStatus(
            vehicleId = "unknown",
            systemStatus = SystemProto.SystemStatus.newBuilder()
                .setDisplayWidth(1024)
                .setDisplayHeight(600)
                .build()
        )

        assertFalse(result)
        assertFalse(saved)
    }

    private fun readyWithCredentials(
        clientId: String,
        secret: ByteArray,
        serverId: String? = null
    ): ApiSessionClient.ConnectResult.Ready =
        ApiSessionClient.ConnectResult.Ready(
            serverHello = serverHello(serverId = serverId),
            pairedCredentials = ApiHandshake.PairedCredentials(
                clientId = clientId,
                secret = secret
            )
        )

    private fun serverHello(serverId: String? = null): Api.ServerHello =
        Api.ServerHello.newBuilder()
            .setApiVersionMajor(1)
            .setApiVersionMinor(1)
            .setServerName("Prodigy")
            .setAppVersion("v1-test")
            .setSessionId("session-1")
            .setCapabilities(Api.Capabilities.getDefaultInstance())
            .apply {
                if (!serverId.isNullOrBlank()) setServerId(serverId)
            }
            .build()
}
