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
    fun persistNewPairing_createsCompleteV1Vehicle() {
        val secret = ByteArray(32) { it.toByte() }
        var saved: List<Vehicle>? = null
        val store = store(load = { emptyList() }, save = { saved = it })

        val vehicle = store.persistNewPairing(
            ssid = "ProdigyAP",
            displayName = "My Car",
            host = "10.0.0.42",
            ready = readyWithCredentials(
                clientId = "client-123",
                secret = secret,
                serverId = "server-uuid-1"
            )
        )

        assertEquals(saved!!.single(), vehicle)
        assertEquals("ProdigyAP", vehicle!!.ssid)
        assertEquals("My Car", vehicle.name)
        assertEquals("client-123", vehicle.apiClientId)
        assertEquals(ApiCrypto.toHex(secret), vehicle.apiSecretHex)
        assertEquals("server-uuid-1", vehicle.serverId)
        assertEquals("10.0.0.42", vehicle.settingsHost)
    }

    @Test
    fun persistNewPairing_usesSsidAsBlankDisplayNameAndOmitsMissingServerId() {
        var saved: List<Vehicle>? = null
        val store = store(load = { emptyList() }, save = { saved = it })

        val vehicle = store.persistNewPairing(
            ssid = "ProdigyAP",
            displayName = "  ",
            host = "10.0.0.1",
            ready = readyWithCredentials("client", ByteArray(32))
        )

        assertEquals("ProdigyAP", vehicle!!.name)
        assertNull(vehicle.serverId)
        assertEquals(vehicle, saved!!.single())
    }

    @Test
    fun persistNewPairing_rejectsIncompleteCredentialsAndDuplicateSsid() {
        val existing = existingVehicle(ssid = "ExistingAP")
        var saved = false
        val store = store(
            load = { listOf(existing) },
            save = { saved = true }
        )
        val noCredentials = ApiSessionClient.ConnectResult.Ready(serverHello(), null)

        assertNull(
            store.persistNewPairing(
                "NewAP",
                "New",
                "10.0.0.1",
                noCredentials
            )
        )
        assertNull(
            store.persistNewPairing(
                "NewAP",
                "New",
                "10.0.0.1",
                readyWithCredentials("", ByteArray(32))
            )
        )
        assertNull(
            store.persistNewPairing(
                "NewAP",
                "New",
                "10.0.0.1",
                readyWithCredentials("client", ByteArray(31))
            )
        )
        assertNull(
            store.persistNewPairing(
                "ExistingAP",
                "Existing",
                "10.0.0.1",
                readyWithCredentials("client", ByteArray(32))
            )
        )
        assertFalse(saved)
    }

    @Test
    fun containsSsidUsesTrimmedExactWifiIdentity() {
        val store = store(
            load = { listOf(existingVehicle(ssid = "ProdigyAP")) },
            save = {}
        )

        assertTrue(store.containsSsid(" ProdigyAP "))
        assertFalse(store.containsSsid("prodigyap"))
    }

    @Test
    fun persistSystemStatus_updatesDisplayDimensionsForMatchedVehicle() {
        val initial = existingVehicle(id = "veh-1", ssid = "CarAP")
        var saved: List<Vehicle>? = null
        val store = store(load = { listOf(initial) }, save = { saved = it })

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
    fun persistSystemStatus_returnsFalseWhenDimensionsAreIncompleteInvalidOrVehicleMissing() {
        val initial = existingVehicle(id = "veh-1", ssid = "CarAP")
        var saved = false
        val store = store(load = { listOf(initial) }, save = { saved = true })
        val missingHeight = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(1024)
            .build()
        val zeroWidth = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(0)
            .setDisplayHeight(600)
            .build()
        val valid = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(1024)
            .setDisplayHeight(600)
            .build()

        assertFalse(store.persistSystemStatus("veh-1", missingHeight))
        assertFalse(store.persistSystemStatus("veh-1", zeroWidth))
        assertFalse(store.persistSystemStatus("unknown", valid))
        assertFalse(saved)
    }

    private fun store(
        load: () -> List<Vehicle>,
        save: (List<Vehicle>) -> Unit
    ) = ApiPairingCredentialStore(loadVehicles = load, saveVehicles = save)

    private fun existingVehicle(
        id: String = "existing",
        ssid: String
    ) = Vehicle(
        id = id,
        ssid = ssid,
        apiClientId = "client-$id",
        apiSecretHex = "ab".repeat(32)
    )

    private fun readyWithCredentials(
        clientId: String,
        secret: ByteArray,
        serverId: String? = null
    ) = ApiSessionClient.ConnectResult.Ready(
        serverHello = serverHello(serverId),
        pairedCredentials = ApiHandshake.PairedCredentials(clientId, secret)
    )

    private fun serverHello(serverId: String? = null): Api.ServerHello =
        Api.ServerHello.newBuilder()
            .setApiVersionMajor(1)
            .setApiVersionMinor(1)
            .setServerName("Prodigy")
            .setAppVersion("test")
            .setSessionId("session")
            .setCapabilities(Api.Capabilities.getDefaultInstance())
            .apply { if (serverId != null) setServerId(serverId) }
            .build()
}
