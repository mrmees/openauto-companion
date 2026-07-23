package org.openauto.companion.net.api

import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.openauto.companion.data.Vehicle
import prodigy.api.v1.Common

data class ApiPairingDraft(
    val ssid: String,
    val displayName: String,
    val host: String = ApiTcpTransport.DEFAULT_HOST,
    val tcpPort: Int = ApiTcpTransport.DEFAULT_PORT
)

class ApiPairingCoordinator(
    private val credentialStore: ApiPairingCredentialStore,
    private val resolveSocketFactory: (ssid: String, host: String) -> (() -> Socket)?,
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
        PAIRING_WINDOW_CLOSED,
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
        val canonicalCode = PairingCode.normalize(pin) ?: return@coroutineScope failure(
            FailureKind.INVALID_INPUT,
            "Pairing code must contain 24 Base32 characters"
        )
        if (credentialStore.containsSsid(ssid)) return@coroutineScope failure(
            FailureKind.DUPLICATE_SSID,
            "This Wi-Fi network is already paired"
        )

        val host = draft.host.trim().ifBlank { ApiTcpTransport.DEFAULT_HOST }
        if (draft.tcpPort !in 1..65535) return@coroutineScope failure(
            FailureKind.INVALID_INPUT,
            "API TCP port is invalid"
        )

        val socketFactory = try {
            resolveSocketFactory(ssid, host)
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

        var client: ApiRuntimeClient? = null
        try {
            val transport = transportFactory(host, draft.tcpPort, socketFactory)
            client = clientFactory(
                transport,
                ApiHandshake.pairing(clientName = CLIENT_NAME, pin = canonicalCode),
                this
            )
            when (val connect = client.connect()) {
                is ApiSessionClient.ConnectResult.Rejected -> {
                    if (connect.isPairingWindowClosed()) {
                        failure(
                            FailureKind.PAIRING_WINDOW_CLOSED,
                            "Pairing window closed. Start a new pairing window and scan again."
                        )
                    } else {
                        failure(
                            FailureKind.REJECTED,
                            connect.reason.ifBlank { "Pairing was rejected" }
                        )
                    }
                }
                is ApiSessionClient.ConnectResult.Disconnected -> failure(
                    FailureKind.CONNECTION_FAILED,
                    connect.reason.ifBlank { "Connection closed during pairing" }
                )
                is ApiSessionClient.ConnectResult.Ready -> persistReady(
                    ssid = ssid,
                    displayName = draft.displayName,
                    host = host,
                    tcpPort = draft.tcpPort,
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
        tcpPort: Int,
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
                tcpPort = tcpPort,
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

    private fun ApiSessionClient.ConnectResult.Rejected.isPairingWindowClosed(): Boolean {
        if (errorCode == Common.ErrorCode.ERROR_CODE_PAIRING_WINDOW_CLOSED) {
            return true
        }
        return reason
            .uppercase()
            .replace(Regex("[^A-Z]+"), "_")
            .trim('_') == "PAIRING_WINDOW_CLOSED"
    }

    private companion object {
        const val CLIENT_NAME = "OpenAuto Companion"
    }
}
