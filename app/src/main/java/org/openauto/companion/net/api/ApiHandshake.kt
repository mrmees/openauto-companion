package org.openauto.companion.net.api

import com.google.protobuf.ByteString
import prodigy.api.v1.Api
import prodigy.api.v1.Common

class ApiHandshake private constructor(
    private val clientName: String,
    private val credentials: Credentials
) {
    enum class State {
        INITIAL,
        WAITING_FOR_SERVER,
        READY,
        TERMINAL
    }

    sealed class Result {
        data class Send(val message: Api.ApiMessage) : Result()
        data class Ready(
            val serverHello: Api.ServerHello,
            val pairedCredentials: PairedCredentials? = null
        ) : Result()
        data class Terminal(
            val reason: String,
            val errorCode: Common.ErrorCode? = null
        ) : Result()
    }

    data class PairedCredentials(
        val clientId: String,
        val secret: ByteArray
    )

    var state: State = State.INITIAL
        private set

    private var pendingPairingSecret: ByteArray? = null

    fun start(): Api.ApiMessage {
        check(state == State.INITIAL) { "Handshake already started" }
        state = State.WAITING_FOR_SERVER

        val auth = when (credentials) {
            is Credentials.KnownClient -> Api.AuthCredentials.newBuilder()
                .setClientId(credentials.clientId)
                .build()
            is Credentials.Pairing -> Api.AuthCredentials.newBuilder()
                .setPairingRequest(true)
                .build()
        }

        return Api.ApiMessage.newBuilder()
            .setRequestId(HANDSHAKE_REQUEST_ID)
            .setClientHello(
                Api.ClientHello.newBuilder()
                    .setRequestedApiVersionMajor(1)
                    .setRequestedApiVersionMinor(1)
                    .setClientName(clientName)
                    .setClientKind(Api.ClientKind.CLIENT_KIND_COMPANION)
                    .setAuth(auth)
                    .build()
            )
            .build()
    }

    fun handle(message: Api.ApiMessage): Result {
        if (state == State.TERMINAL) {
            return Result.Terminal("Handshake is already terminal")
        }

        return when (message.payloadCase) {
            Api.ApiMessage.PayloadCase.AUTH_REQUIRED -> handleAuthRequired(message.authRequired)
            Api.ApiMessage.PayloadCase.PAIRING_CHALLENGE -> handlePairingChallenge(message.pairingChallenge)
            Api.ApiMessage.PayloadCase.SERVER_HELLO -> handleServerHello(message.serverHello)
            Api.ApiMessage.PayloadCase.AUTH_REJECT -> terminal(message.authReject.reason)
            Api.ApiMessage.PayloadCase.ERROR -> terminal(
                reason = message.error.message,
                errorCode = message.error.code
            )
            else -> terminal("Unexpected handshake message: ${message.payloadCase}")
        }
    }

    fun requireReadyForReports() {
        check(state == State.READY) { "Reports may only be sent after ServerHello" }
    }

    private fun handleAuthRequired(authRequired: Api.AuthRequired): Result {
        val known = credentials as? Credentials.KnownClient
            ?: return terminal("AuthRequired received during pairing")
        val proof = ApiCrypto.hmacSha256(known.secret, authRequired.nonce.toByteArray())
        return Result.Send(
            Api.ApiMessage.newBuilder()
                .setRequestId(HANDSHAKE_REQUEST_ID)
                .setAuthResponse(
                    Api.AuthResponse.newBuilder()
                        .setClientId(known.clientId)
                        .setProof(ByteString.copyFrom(proof))
                        .build()
                )
                .build()
        )
    }

    private fun handlePairingChallenge(challenge: Api.PairingChallenge): Result {
        val pairing = credentials as? Credentials.Pairing
            ?: return terminal("PairingChallenge received for known client")
        if (challenge.secretFormat != Api.PairingSecretFormat.PAIRING_SECRET_FORMAT_BASE32_120) {
            return terminal("Unsupported pairing secret format: ${challenge.secretFormat}")
        }
        val secret = ApiCrypto.derivePairingSecret(pairing.code, challenge.salt.toByteArray())
        pendingPairingSecret = secret
        val proof = ApiCrypto.hmacSha256(secret, challenge.nonce.toByteArray())
        return Result.Send(
            Api.ApiMessage.newBuilder()
                .setRequestId(HANDSHAKE_REQUEST_ID)
                .setPairingResponse(
                    Api.PairingResponse.newBuilder()
                        .setProof(ByteString.copyFrom(proof))
                        .build()
                )
                .build()
        )
    }

    private fun handleServerHello(serverHello: Api.ServerHello): Result {
        state = State.READY
        val paired = if (credentials is Credentials.Pairing) {
            if (!serverHello.capabilities.hasSecurePairingCode() ||
                !serverHello.capabilities.securePairingCode
            ) {
                return terminal("Server did not confirm secure pairing support")
            }
            val clientId = if (serverHello.hasGrantedClientId()) serverHello.grantedClientId else ""
            if (clientId.isBlank()) return terminal("Pairing completed without granted client id")
            val secret = pendingPairingSecret
                ?: return terminal("Pairing completed without derived secret")
            PairedCredentials(clientId = clientId, secret = secret)
        } else {
            null
        }
        return Result.Ready(serverHello = serverHello, pairedCredentials = paired)
    }

    private fun terminal(
        reason: String,
        errorCode: Common.ErrorCode? = null
    ): Result.Terminal {
        state = State.TERMINAL
        return Result.Terminal(reason = reason, errorCode = errorCode)
    }

    private sealed class Credentials {
        data class KnownClient(val clientId: String, val secret: ByteArray) : Credentials()
        data class Pairing(val code: String) : Credentials()
    }

    companion object {
        private const val HANDSHAKE_REQUEST_ID = 1L

        fun knownClient(clientName: String, clientId: String, secret: ByteArray): ApiHandshake {
            require(clientId.isNotBlank()) { "clientId is required" }
            require(secret.size == ApiCrypto.SECRET_SIZE_BYTES) { "secret must be 32 bytes" }
            return ApiHandshake(
                clientName = clientName,
                credentials = Credentials.KnownClient(clientId = clientId, secret = secret)
            )
        }

        fun pairing(clientName: String, pairingCode: String): ApiHandshake {
            val canonical = requireNotNull(PairingCode.normalize(pairingCode)) {
                "pairing code must contain 24 Base32 characters"
            }
            return ApiHandshake(
                clientName = clientName,
                credentials = Credentials.Pairing(code = canonical)
            )
        }
    }
}
