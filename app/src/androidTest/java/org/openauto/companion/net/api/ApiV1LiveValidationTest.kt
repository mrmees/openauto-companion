package org.openauto.companion.net.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openauto.companion.net.NetworkSocketFactory

@RunWith(AndroidJUnit4::class)
class ApiV1LiveValidationTest {
    @Test
    fun tcpPortAcceptsConnectionsOverWifiNetwork() {
        assumeLiveApiV1Enabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val host = arguments.getString("api_host") ?: HOST
        val port = arguments.getString("api_port")?.toIntOrNull()
            ?: ApiTcpTransport.DEFAULT_PORT
        val wifiNetwork = requireWifiNetwork(context)

        NetworkSocketFactory.forNetwork(wifiNetwork).invoke().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        }
    }

    @Test
    fun legacyTcpPortIsRefusedWhenExplicitlyRequested() {
        assumeLiveApiV1Enabled()
        assumeLegacyRefusalCheckEnabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val host = arguments.getString("api_host") ?: HOST
        val wifiNetwork = requireWifiNetwork(context)

        try {
            NetworkSocketFactory.forNetwork(wifiNetwork).invoke().use { socket ->
                socket.connect(InetSocketAddress(host, LEGACY_PORT), CONNECT_TIMEOUT_MS)
            }
            fail("Legacy companion TCP port unexpectedly accepted a connection")
        } catch (_: IOException) {
            // Refusal, reset, or routing failure all prove there is no accepting legacy listener.
        }
    }

    @Test
    fun tcpEndpointRespondsToKnownClientAuthOverWifiNetwork() = runBlocking {
        assumeLiveApiV1Enabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val host = arguments.getString("api_host") ?: HOST
        val wifiNetwork = requireWifiNetwork(context)
        val suppliedClientId = arguments.getString("api_client_id")?.trim().orEmpty()
        val suppliedSecretHex = arguments.getString("api_secret_hex")?.trim().orEmpty()
        val suppliedSecret = ApiCrypto.decodeSecretHex(suppliedSecretHex)
        val usingSuppliedCredentials =
            suppliedClientId.isNotBlank() || suppliedSecretHex.isNotBlank()
        if (usingSuppliedCredentials) {
            assertTrue(
                "Known-client validation requires both a client id and a valid 32-byte secret",
                suppliedClientId.isNotBlank() && suppliedSecret != null
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val transport = ApiTcpTransport(
            host = host,
            port = ApiTcpTransport.DEFAULT_PORT,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            socketFactory = NetworkSocketFactory.forNetwork(wifiNetwork)
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient(
                clientName = "Companion live validation",
                clientId = suppliedClientId.ifBlank { "live-validation-invalid-client" },
                secret = suppliedSecret ?: ByteArray(ApiCrypto.SECRET_SIZE_BYTES)
            ),
            scope = scope
        )

        try {
            val result = withTimeout(HANDSHAKE_TIMEOUT_MS) { client.connect() }
            if (usingSuppliedCredentials) {
                assertTrue(
                    "Supplied known-client credentials did not reach READY",
                    result is ApiSessionClient.ConnectResult.Ready
                )
            }
            when (result) {
                is ApiSessionClient.ConnectResult.Ready -> {
                    assertEquals(1, result.serverHello.apiVersionMajor)
                    assertTrue(
                        "Expected deployed head unit to advertise External API minor >= 1",
                        result.serverHello.apiVersionMinor >= 1
                    )
                    assertTrue(
                        "Expected v1.1 ServerHello.server_id on successful auth",
                        result.serverHello.hasServerId()
                    )
                    assertFalse(
                        "ServerHello.server_id should not be blank",
                        result.serverHello.serverId.isBlank()
                    )
                }
                is ApiSessionClient.ConnectResult.Rejected -> {
                    // Tolerate early close on invalid auth until head-unit terminal rejection frames are delivered.
                    assertFalse("Terminal reason should not be blank", result.reason.isBlank())
                }
                is ApiSessionClient.ConnectResult.Disconnected -> {
                    // Invalid auth may close before the terminal frame is flushed on current hardware.
                    assertFalse("Disconnect reason should not be blank", result.reason.isBlank())
                }
            }
        } finally {
            client.close()
            scope.cancel()
        }
    }

    @Suppress("DEPRECATION")
    private fun requireWifiNetwork(context: Context): Network {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val candidates = connectivityManager.allNetworks.filter { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        assertFalse("No Wi-Fi Network is visible to the app context", candidates.isEmpty())

        return candidates.firstOrNull { network ->
            connectivityManager.getLinkProperties(network)
                ?.linkAddresses
                ?.any { it.address is Inet4Address } == true
        } ?: candidates.first().also {
            assertNotNull("Wi-Fi Network should be available", it)
        }
    }

    private fun assumeLiveApiV1Enabled() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString("live_api_v1")
            ?.toBooleanStrictOrNull() ?: false
        assumeTrue("Live API v1 validation requires -e live_api_v1 true", enabled)
    }

    private fun assumeLegacyRefusalCheckEnabled() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString("live_expect_legacy_refused")
            ?.toBooleanStrictOrNull() ?: false
        assumeTrue(
            "Legacy refusal validation requires -e live_expect_legacy_refused true",
            enabled
        )
    }

    companion object {
        private const val HOST = "10.0.0.1"
        private const val LEGACY_PORT = 9876
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }
}
