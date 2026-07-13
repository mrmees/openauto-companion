package org.openauto.companion.net.api

import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.openauto.companion.data.Vehicle

data class ApiPairingDraft(
    val ssid: String,
    val displayName: String,
    val host: String = ApiTcpTransport.DEFAULT_HOST
)

class ApiPairingCoordinator(
    private val credentialStore: ApiPairingCredentialStore,
    private val resolveSocketFactory: (ssid: String) -> (() -> Socket)?,
    private val transportFactory: (
        host: String,
        port: Int,
        socketFactory: () -> Socket
    ) -> ApiTransport = { host, port, socketFactory ->
        ApiTcpTransport(host = host, port = port, socketFactory = socketFactory)
    },
    private val clientFactory: (
        transport: ApiTransport,
        handshake: ApiHandshake,
        scope: CoroutineScope
    ) -> ApiRuntimeClient = { transport, handshake, scope ->
        ApiSessionClient(transport = transport, handshake = handshake, scope = scope)
    }
) {
    enum class FailureKind {
        INVALID_INPUT,
        DUPLICATE_SSID,
        WIFI_NOT_FOUND,
        REJECTED,
        CONNECTION_FAILED,
        INVALID_READY,
        SAVE_FAILED
    }

    sealed interface Result {
        data class Success(val vehicle: Vehicle) : Result
        data class Failure(val kind: FailureKind, val message: String) : Result
        data object Cancelled : Result
    }

    suspend fun pair(draft: ApiPairingDraft, pin: String): Result = coroutineScope {
        val ssid = draft.ssid.trim()
        if (ssid.isBlank()) return@coroutineScope failure(
            FailureKind.INVALID_INPUT,
            "Wi-Fi SSID is required"
        )
        if (!pin.matches(PIN_PATTERN)) return@coroutineScope failure(
            FailureKind.INVALID_INPUT,
            "PIN must be exactly 6 digits"
        )
        if (credentialStore.containsSsid(ssid)) return@coroutineScope failure(
            FailureKind.DUPLICATE_SSID,
            "This Wi-Fi network is already paired"
        )

        val socketFactory = try {
            resolveSocketFactory(ssid)
        } catch (_: CancellationException) {
            return@coroutineScope Result.Cancelled
        } catch (error: Exception) {
            return@coroutineScope failure(
                FailureKind.WIFI_NOT_FOUND,
                error.message ?: "Unable to inspect Wi-Fi networks"
            )
        } ?: return@coroutineScope failure(
            FailureKind.WIFI_NOT_FOUND,
            "Connect the phone to $ssid before pairing"
        )

        val host = draft.host.trim().ifBlank { ApiTcpTransport.DEFAULT_HOST }
        var client: ApiRuntimeClient? = null
        try {
            val transport = transportFactory(host, ApiTcpTransport.DEFAULT_PORT, socketFactory)
            client = clientFactory(
                transport,
                ApiHandshake.pairing(clientName = CLIENT_NAME, pin = pin),
                this
            )
            when (val connect = client.connect()) {
                is ApiSessionClient.ConnectResult.Rejected -> failure(
                    FailureKind.REJECTED,
                    connect.reason.ifBlank { "Pairing was rejected" }
                )
                is ApiSessionClient.ConnectResult.Disconnected -> failure(
                    FailureKind.CONNECTION_FAILED,
                    connect.reason.ifBlank { "Connection closed during pairing" }
                )
                is ApiSessionClient.ConnectResult.Ready -> persistReady(
                    ssid = ssid,
                    displayName = draft.displayName,
                    host = host,
                    ready = connect
                )
            }
        } catch (_: CancellationException) {
            Result.Cancelled
        } catch (error: Exception) {
            failure(
                FailureKind.CONNECTION_FAILED,
                error.message ?: "Pairing connection failed"
            )
        } finally {
            client?.close()
        }
    }

    private fun persistReady(
        ssid: String,
        displayName: String,
        host: String,
        ready: ApiSessionClient.ConnectResult.Ready
    ): Result {
        val credentials = ready.pairedCredentials
        if (credentials == null ||
            credentials.clientId.isBlank() ||
            credentials.secret.size != ApiCrypto.SECRET_SIZE_BYTES
        ) {
            return failure(
                FailureKind.INVALID_READY,
                "Pairing completed without valid credentials"
            )
        }

        return try {
            val vehicle = credentialStore.persistNewPairing(
                ssid = ssid,
                displayName = displayName,
                host = host,
                ready = ready
            ) ?: return failure(
                if (credentialStore.containsSsid(ssid)) {
                    FailureKind.DUPLICATE_SSID
                } else {
                    FailureKind.SAVE_FAILED
                },
                "Unable to save paired vehicle"
            )
            Result.Success(vehicle)
        } catch (error: Exception) {
            failure(
                FailureKind.SAVE_FAILED,
                error.message ?: "Unable to save paired vehicle"
            )
        }
    }

    private fun failure(kind: FailureKind, message: String): Result.Failure =
        Result.Failure(kind = kind, message = message)

    private companion object {
        val PIN_PATTERN = Regex("\\d{6}")
        const val CLIENT_NAME = "OpenAuto Companion"
    }
}
