package org.openauto.companion.net.api

import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common

class ApiHandshakeTest {
    private val clientSecret =
        "9b6b8bbdd36470ae0f82133563f749881a5453650e7062fd8ddb97a9b19d7778".hexToBytes()

    @Test
    fun knownClientFlow_usesServerNonceForAuthProofThenBecomesReady() {
        val handshake = ApiHandshake.knownClient(
            clientName = "Pixel 9",
            clientId = "client-123",
            secret = clientSecret
        )
        val nonce =
            "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f".hexToBytes()

        val hello = handshake.start()

        assertEquals(Api.ApiMessage.PayloadCase.CLIENT_HELLO, hello.payloadCase)
        assertEquals(1L, hello.requestId)
        assertEquals(1, hello.clientHello.requestedApiVersionMajor)
        assertEquals(1, hello.clientHello.requestedApiVersionMinor)
        assertEquals("Pixel 9", hello.clientHello.clientName)
        assertEquals(Api.ClientKind.CLIENT_KIND_COMPANION, hello.clientHello.clientKind)
        assertEquals("client-123", hello.clientHello.auth.clientId)
        assertFalse(hello.clientHello.auth.pairingRequest)

        val authResponse = handshake.handle(authRequired(nonce)).requireSend()

        assertEquals(Api.ApiMessage.PayloadCase.AUTH_RESPONSE, authResponse.payloadCase)
        assertEquals(hello.requestId, authResponse.requestId)
        assertEquals("client-123", authResponse.authResponse.clientId)
        assertArrayEquals(
            ApiCrypto.hmacSha256(clientSecret, nonce),
            authResponse.authResponse.proof.toByteArray()
        )

        val ready = handshake.handle(serverHello()).requireReady()

        assertEquals(ApiHandshake.State.READY, handshake.state)
        assertEquals("Prodigy", ready.serverHello.serverName)
        assertEquals(null, ready.pairedCredentials)
    }

    @Test
    fun pairingFlow_derivesSecretFromCodeAndSaltThenReturnsGrantedCredentials() {
        val code = "ABCDEFGHIJKLMNOPQRSTUVWX"
        val handshake = ApiHandshake.pairing(clientName = "Pixel 9", pin = code)
        val nonce =
            "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f".hexToBytes()
        val salt = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val expectedSecret = ApiCrypto.derivePairingSecret(code, salt)

        val hello = handshake.start()

        assertEquals(Api.ApiMessage.PayloadCase.CLIENT_HELLO, hello.payloadCase)
        assertEquals(1L, hello.requestId)
        assertTrue(hello.clientHello.auth.pairingRequest)
        assertEquals("", hello.clientHello.auth.clientId)

        val pairingResponse = handshake.handle(pairingChallenge(nonce, salt)).requireSend()

        assertEquals(Api.ApiMessage.PayloadCase.PAIRING_RESPONSE, pairingResponse.payloadCase)
        assertEquals(hello.requestId, pairingResponse.requestId)
        assertArrayEquals(
            ApiCrypto.hmacSha256(expectedSecret, nonce),
            pairingResponse.pairingResponse.proof.toByteArray()
        )

        val ready = handshake.handle(serverHello(grantedClientId = "client-new")).requireReady()

        assertEquals(ApiHandshake.State.READY, handshake.state)
        assertEquals("client-new", ready.pairedCredentials?.clientId)
        assertArrayEquals(expectedSecret, ready.pairedCredentials?.secret)
    }

    @Test
    fun pairingRejectsChallengeWithoutSecureCodeFormat() {
        val handshake = ApiHandshake.pairing(
            clientName = "Pixel 9",
            pin = "ABCDEFGHIJKLMNOPQRSTUVWX"
        )
        handshake.start()
        val challenge = Api.ApiMessage.newBuilder()
            .setPairingChallenge(
                Api.PairingChallenge.newBuilder()
                    .setNonce(ByteString.copyFrom(ByteArray(32)))
                    .setSalt(ByteString.copyFrom(ByteArray(16)))
            )
            .build()

        val terminal = handshake.handle(challenge)
        assertTrue(terminal is ApiHandshake.Result.Terminal)
        assertEquals(ApiHandshake.State.TERMINAL, handshake.state)
    }

    @Test
    fun authRejectIsTerminal() {
        val handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret)
        handshake.start()

        val terminal = handshake.handle(authReject("bad proof")).requireTerminal()

        assertEquals(ApiHandshake.State.TERMINAL, handshake.state)
        assertEquals("bad proof", terminal.reason)
    }

    @Test
    fun errorIsTerminal() {
        val handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret)
        handshake.start()

        val terminal = handshake.handle(
            error(
                message = "Pairing window closed",
                code = Common.ErrorCode.ERROR_CODE_PAIRING_WINDOW_CLOSED,
                requestId = 7
            )
        ).requireTerminal()

        assertEquals(ApiHandshake.State.TERMINAL, handshake.state)
        assertEquals("Pairing window closed", terminal.reason)
        assertEquals(Common.ErrorCode.ERROR_CODE_PAIRING_WINDOW_CLOSED, terminal.errorCode)
    }

    @Test
    fun reportsAreRejectedBeforeReady() {
        val handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret)

        expectIllegalState { handshake.requireReadyForReports() }

        handshake.start()
        expectIllegalState { handshake.requireReadyForReports() }

        handshake.handle(authRequired(ByteArray(32) { it.toByte() })).requireSend()
        handshake.handle(serverHello()).requireReady()

        handshake.requireReadyForReports()
    }

    private fun authRequired(nonce: ByteArray): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setAuthRequired(
                Api.AuthRequired.newBuilder()
                    .setNonce(ByteString.copyFrom(nonce))
                    .build()
            )
            .build()

    private fun pairingChallenge(nonce: ByteArray, salt: ByteArray): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setPairingChallenge(
                Api.PairingChallenge.newBuilder()
                    .setNonce(ByteString.copyFrom(nonce))
                    .setSalt(ByteString.copyFrom(salt))
                    .setSecretFormat(Api.PairingSecretFormat.PAIRING_SECRET_FORMAT_BASE32_120)
                    .build()
            )
            .build()

    private fun serverHello(grantedClientId: String? = null): Api.ApiMessage {
        val hello = Api.ServerHello.newBuilder()
            .setApiVersionMajor(1)
            .setApiVersionMinor(0)
            .setServerName("Prodigy")
            .setAppVersion("v1-test")
            .setSessionId("session-1")
            .setCapabilities(
                Api.Capabilities.newBuilder().setSecurePairingCode(true).build()
            )
            .apply {
                if (grantedClientId != null) setGrantedClientId(grantedClientId)
            }
            .build()
        return Api.ApiMessage.newBuilder()
            .setServerHello(hello)
            .build()
    }

    private fun authReject(reason: String): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setAuthReject(Api.AuthReject.newBuilder().setReason(reason).build())
            .build()

    private fun error(
        message: String,
        code: Common.ErrorCode = Common.ErrorCode.ERROR_CODE_AUTH_FAILED,
        requestId: Long = 0
    ): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setRequestId(requestId)
            .setError(
                Common.Error.newBuilder()
                    .setCode(code)
                    .setMessage(message)
                    .build()
            )
            .build()

    private fun ApiHandshake.Result.requireSend(): Api.ApiMessage =
        when (this) {
            is ApiHandshake.Result.Send -> message
            else -> fail("Expected Send, got $this") as Nothing
        }

    private fun ApiHandshake.Result.requireReady(): ApiHandshake.Result.Ready =
        when (this) {
            is ApiHandshake.Result.Ready -> this
            else -> fail("Expected Ready, got $this") as Nothing
        }

    private fun ApiHandshake.Result.requireTerminal(): ApiHandshake.Result.Terminal =
        when (this) {
            is ApiHandshake.Result.Terminal -> this
            else -> fail("Expected Terminal, got $this") as Nothing
        }

    private fun expectIllegalState(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }
}

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
