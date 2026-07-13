package org.openauto.companion.net.api

import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openauto.companion.data.Vehicle
import prodigy.api.v1.Api

class ApiPairingCoordinatorTest {
    @Test
    fun invalidInputAndDuplicateSsidFailBeforeNetworkOrTransportCreation() = runTest {
        val harness = Harness(
            initialVehicles = listOf(
                Vehicle(
                    ssid = "ExistingAP",
                    sharedSecret = "",
                    apiClientId = "existing",
                    apiSecretHex = ApiCrypto.toHex(ByteArray(32)),
                    apiMode = Vehicle.ApiMode.EXTERNAL_API_V1
                )
            )
        )

        val blankSsid = harness.coordinator.pair(
            ApiPairingDraft(ssid = "  ", displayName = "Car", host = "10.0.0.1"),
            pin = "123456"
        )
        val invalidPin = harness.coordinator.pair(
            ApiPairingDraft(ssid = "NewAP", displayName = "Car", host = "10.0.0.1"),
            pin = "12345"
        )
        val duplicate = harness.coordinator.pair(
            ApiPairingDraft(ssid = "ExistingAP", displayName = "Car", host = "10.0.0.1"),
            pin = "123456"
        )

        assertFailureKind(ApiPairingCoordinator.FailureKind.INVALID_INPUT, blankSsid)
        assertFailureKind(ApiPairingCoordinator.FailureKind.INVALID_INPUT, invalidPin)
        assertFailureKind(ApiPairingCoordinator.FailureKind.DUPLICATE_SSID, duplicate)
        assertEquals(0, harness.resolveCalls)
        assertEquals(0, harness.transportCalls)
    }

    @Test
    fun successfulReadySavesCompleteV1VehicleAndUsesResolvedTcpEndpoint() = runTest {
        val secret = ByteArray(32) { (it + 1).toByte() }
        val ready = readyResult(
            clientId = "client-123",
            secret = secret,
            serverId = "server-uuid-1"
        )
        val client = FakePairingClient(connectResult = ready)
        val harness = Harness(clientProvider = { client })

        val result = harness.coordinator.pair(
            draft = ApiPairingDraft(
                ssid = "ProdigyAP",
                displayName = "My Car",
                host = "10.0.0.42"
            ),
            pin = "123456"
        )

        assertTrue(result is ApiPairingCoordinator.Result.Success)
        val vehicle = (result as ApiPairingCoordinator.Result.Success).vehicle
        assertEquals("ProdigyAP", vehicle.ssid)
        assertEquals("My Car", vehicle.name)
        assertEquals("client-123", vehicle.apiClientId)
        assertEquals(ApiCrypto.toHex(secret), vehicle.apiSecretHex)
        assertEquals("server-uuid-1", vehicle.serverId)
        assertEquals("10.0.0.42", vehicle.settingsHost)
        assertEquals(Vehicle.ApiMode.EXTERNAL_API_V1, vehicle.apiMode)
        assertEquals(listOf(vehicle), harness.savedVehicles)
        assertEquals("10.0.0.42", harness.transportHost)
        assertEquals(ApiTcpTransport.DEFAULT_PORT, harness.transportPort)
        assertSame(harness.resolvedSocketFactory, harness.transportSocketFactory)
        assertTrue(client.closed)
    }

    @Test
    fun blankHostUsesDefaultApiHost() = runTest {
        val harness = Harness(
            clientProvider = {
                FakePairingClient(connectResult = readyResult("client", ByteArray(32)))
            }
        )

        harness.coordinator.pair(
            ApiPairingDraft(ssid = "ProdigyAP", displayName = "Car", host = "  "),
            pin = "123456"
        )

        assertEquals(ApiTcpTransport.DEFAULT_HOST, harness.transportHost)
        assertEquals(ApiTcpTransport.DEFAULT_HOST, harness.savedVehicles!!.single().settingsHost)
    }

    @Test
    fun incompleteReadyResultsSaveNothingAndAlwaysCloseClient() = runTest {
        val invalidReadyResults = listOf(
            ApiSessionClient.ConnectResult.Ready(serverHello(), pairedCredentials = null),
            readyResult(clientId = "", secret = ByteArray(32)),
            readyResult(clientId = "client", secret = ByteArray(31))
        )

        invalidReadyResults.forEach { ready ->
            val client = FakePairingClient(connectResult = ready)
            val harness = Harness(clientProvider = { client })

            val result = harness.coordinator.pair(draft(), pin = "123456")

            assertFailureKind(ApiPairingCoordinator.FailureKind.INVALID_READY, result)
            assertNull(harness.savedVehicles)
            assertTrue(client.closed)
        }
    }

    @Test
    fun rejectionEarlyCloseAndExceptionSaveNothingAndCloseClient() = runTest {
        val outcomes = listOf(
            FakePairingClient(
                connectResult = ApiSessionClient.ConnectResult.Rejected("wrong PIN")
            ) to ApiPairingCoordinator.FailureKind.REJECTED,
            FakePairingClient(
                connectResult = ApiSessionClient.ConnectResult.Disconnected("early EOF")
            ) to ApiPairingCoordinator.FailureKind.CONNECTION_FAILED,
            FakePairingClient(connectError = IllegalStateException("socket failed")) to
                ApiPairingCoordinator.FailureKind.CONNECTION_FAILED
        )

        outcomes.forEach { (client, expectedKind) ->
            val harness = Harness(clientProvider = { client })

            val result = harness.coordinator.pair(draft(), pin = "123456")

            assertFailureKind(expectedKind, result)
            assertNull(harness.savedVehicles)
            assertTrue(client.closed)
        }
    }

    @Test
    fun missingWifiFailsBeforeTransportCreation() = runTest {
        val harness = Harness(resolveSocketFactory = { null })

        val result = harness.coordinator.pair(draft(), pin = "123456")

        assertFailureKind(ApiPairingCoordinator.FailureKind.WIFI_NOT_FOUND, result)
        assertEquals(0, harness.transportCalls)
        assertNull(harness.savedVehicles)
    }

    @Test
    fun cancellationReturnsCancelledWithoutSavingAndClosesClient() = runTest {
        val client = FakePairingClient(connectError = CancellationException("cancelled"))
        val harness = Harness(clientProvider = { client })

        val result = harness.coordinator.pair(draft(), pin = "123456")

        assertEquals(ApiPairingCoordinator.Result.Cancelled, result)
        assertNull(harness.savedVehicles)
        assertTrue(client.closed)
    }

    private class Harness(
        initialVehicles: List<Vehicle> = emptyList(),
        resolveSocketFactory: ((String) -> (() -> Socket)?)? = null,
        private val clientProvider: () -> ApiRuntimeClient = {
            FakePairingClient(connectResult = readyResult("client", ByteArray(32)))
        }
    ) {
        val resolvedSocketFactory: () -> Socket = { Socket() }
        var savedVehicles: List<Vehicle>? = null
        var resolveCalls = 0
        var transportCalls = 0
        var transportHost: String? = null
        var transportPort: Int? = null
        var transportSocketFactory: (() -> Socket)? = null

        private val socketResolver: (String) -> (() -> Socket)? = { ssid ->
            resolveCalls += 1
            if (resolveSocketFactory == null) {
                resolvedSocketFactory
            } else {
                resolveSocketFactory(ssid)
            }
        }

        private val store = ApiPairingCredentialStore(
            loadVehicles = { savedVehicles ?: initialVehicles },
            saveVehicles = { savedVehicles = it }
        )

        val coordinator = ApiPairingCoordinator(
            credentialStore = store,
            resolveSocketFactory = socketResolver,
            transportFactory = { host, port, socketFactory ->
                transportCalls += 1
                transportHost = host
                transportPort = port
                transportSocketFactory = socketFactory
                FakeTransport
            },
            clientFactory = { _, _, _ -> clientProvider() }
        )
    }

    private class FakePairingClient(
        private val connectResult: ApiSessionClient.ConnectResult? = null,
        private val connectError: Throwable? = null
    ) : ApiRuntimeClient {
        override val incoming = Channel<Api.ApiMessage>(Channel.UNLIMITED).also { it.close() }
        var closed = false
            private set

        override suspend fun connect(): ApiSessionClient.ConnectResult {
            connectError?.let { throw it }
            return checkNotNull(connectResult)
        }

        override suspend fun sendReadyMessage(message: Api.ApiMessage) = Unit

        override suspend fun awaitClosed(): ApiSessionClient.ReadyClose =
            ApiSessionClient.ReadyClose.ClientClosed

        override fun close() {
            closed = true
        }
    }

    private object FakeTransport : ApiTransport {
        override suspend fun connect() = Unit
        override suspend fun send(message: Api.ApiMessage) = Unit
        override suspend fun receive(): Api.ApiMessage? = null
        override fun close() = Unit
    }

    private fun draft() = ApiPairingDraft(
        ssid = "ProdigyAP",
        displayName = "My Car",
        host = "10.0.0.1"
    )

    private fun assertFailureKind(
        expected: ApiPairingCoordinator.FailureKind,
        result: ApiPairingCoordinator.Result
    ) {
        assertTrue(result is ApiPairingCoordinator.Result.Failure)
        assertEquals(expected, (result as ApiPairingCoordinator.Result.Failure).kind)
    }

    private companion object {
        fun readyResult(
            clientId: String,
            secret: ByteArray,
            serverId: String? = null
        ) = ApiSessionClient.ConnectResult.Ready(
            serverHello = serverHello(serverId),
            pairedCredentials = ApiHandshake.PairedCredentials(clientId, secret)
        )

        fun serverHello(serverId: String? = null): Api.ServerHello =
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
}
