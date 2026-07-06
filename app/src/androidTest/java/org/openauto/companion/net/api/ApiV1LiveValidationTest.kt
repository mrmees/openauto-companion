package org.openauto.companion.net.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

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

        wifiNetwork.socketFactory.createSocket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        }
    }

    @Test
    fun tcpEndpointRespondsToKnownClientAuthOverWifiNetwork() = runBlocking {
        assumeLiveApiV1Enabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wifiNetwork = requireWifiNetwork(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val transport = ApiTcpTransport(
            host = HOST,
            port = ApiTcpTransport.DEFAULT_PORT,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            socketFactory = { wifiNetwork.socketFactory.createSocket() as Socket }
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient(
                clientName = "Companion live validation",
                clientId = "live-validation-invalid-client",
                secret = ByteArray(ApiCrypto.SECRET_SIZE_BYTES)
            ),
            scope = scope
        )

        try {
            val result = withTimeout(HANDSHAKE_TIMEOUT_MS) { client.connect() }
            when (result) {
                is ApiSessionClient.ConnectResult.Ready -> {
                    assertEquals(1, result.serverHello.apiVersionMajor)
                    assertEquals(0, result.serverHello.apiVersionMinor)
                }
                is ApiSessionClient.ConnectResult.Terminal -> {
                    assertFalse(
                        "The v1 TCP socket closed before returning an auth/error frame",
                        result.reason.contains("Connection closed during handshake")
                    )
                    assertFalse("Terminal reason should not be blank", result.reason.isBlank())
                }
            }
        } finally {
            client.close()
            scope.cancel()
        }
    }

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

    companion object {
        private const val HOST = "10.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }
}
