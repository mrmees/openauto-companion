package org.openauto.companion.net.api

import com.google.protobuf.ByteString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common

class ApiSessionClientTest {
    private val clientSecret =
        "9b6b8bbdd36470ae0f82133563f749881a5453650e7062fd8ddb97a9b19d7778".hexToBytes()

    @Test
    fun connect_knownClientSendsClientHelloFirstAndUsesServerNonce() = runTest {
        val nonce =
            "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f".hexToBytes()
        val transport = FakeTransport(
            listOf(authRequired(nonce), serverHello())
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )

        val result = client.connect()

        assertTrue(result is ApiSessionClient.ConnectResult.Ready)
        assertEquals(Api.ApiMessage.PayloadCase.CLIENT_HELLO, transport.sent[0].payloadCase)
        assertEquals("client-123", transport.sent[0].clientHello.auth.clientId)
        assertEquals(Api.ApiMessage.PayloadCase.AUTH_RESPONSE, transport.sent[1].payloadCase)
        assertArrayEquals(
            ApiCrypto.hmacSha256(clientSecret, nonce),
            transport.sent[1].authResponse.proof.toByteArray()
        )
        client.close()
    }

    @Test
    fun connect_pairingUsesChallengeSaltAndNonce() = runTest {
        val nonce =
            "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f".hexToBytes()
        val salt = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val expectedSecret = ApiCrypto.derivePairingSecret("123456", salt)
        val transport = FakeTransport(
            listOf(pairingChallenge(nonce, salt), serverHello(grantedClientId = "client-new"))
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.pairing("Pixel 9", "123456"),
            scope = backgroundScope
        )

        val result = client.connect()

        assertTrue(result is ApiSessionClient.ConnectResult.Ready)
        val ready = result as ApiSessionClient.ConnectResult.Ready
        assertEquals("client-new", ready.pairedCredentials?.clientId)
        assertArrayEquals(expectedSecret, ready.pairedCredentials?.secret)
        assertEquals(Api.ApiMessage.PayloadCase.CLIENT_HELLO, transport.sent[0].payloadCase)
        assertTrue(transport.sent[0].clientHello.auth.pairingRequest)
        assertEquals(Api.ApiMessage.PayloadCase.PAIRING_RESPONSE, transport.sent[1].payloadCase)
        assertArrayEquals(
            ApiCrypto.hmacSha256(expectedSecret, nonce),
            transport.sent[1].pairingResponse.proof.toByteArray()
        )
        client.close()
    }

    @Test
    fun connect_authRejectClosesTransportAndReturnsTerminal() = runTest {
        val transport = FakeTransport(listOf(authReject("bad proof")))
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )

        val result = client.connect()

        assertEquals(Api.ApiMessage.PayloadCase.CLIENT_HELLO, transport.sent.single().payloadCase)
        assertTrue(result is ApiSessionClient.ConnectResult.Rejected)
        assertEquals("bad proof", (result as ApiSessionClient.ConnectResult.Rejected).reason)
        assertTrue(transport.closed)
    }

    @Test
    fun connect_errorClosesTransportAndReturnsTerminal() = runTest {
        val transport = FakeTransport(listOf(error("unsupported version")))
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )

        val result = client.connect()

        assertTrue(result is ApiSessionClient.ConnectResult.Rejected)
        result as ApiSessionClient.ConnectResult.Rejected
        assertEquals("unsupported version", result.reason)
        assertEquals(Common.ErrorCode.ERROR_CODE_UNSUPPORTED_VERSION, result.errorCode)
        assertTrue(transport.closed)
    }

    @Test
    fun connect_handshakeEofReturnsDisconnectedInsteadOfRejected() = runTest {
        val transport = FakeTransport(emptyList())
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )

        val result = client.connect()

        assertTrue(result is ApiSessionClient.ConnectResult.Disconnected)
        assertTrue((result as ApiSessionClient.ConnectResult.Disconnected).reason.isNotBlank())
        assertTrue(transport.closed)
    }

    @Test
    fun connect_stalledHandshakeTimesOutAndClosesTransport() = runTest {
        val transport = FakeTransport(emptyList(), closeAfterInitial = false)
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope,
            handshakeTimeoutMs = 100
        )

        val result = client.connect()

        assertTrue(result is ApiSessionClient.ConnectResult.Disconnected)
        assertEquals("Handshake timed out", (result as ApiSessionClient.ConnectResult.Disconnected).reason)
        assertTrue(transport.closed)
    }

    @Test
    fun afterReady_readerLoopForwardsIncomingMessages() = runTest {
        val firstEvent = Api.ApiMessage.newBuilder()
            .setRequestId(1)
            .setPing(Common.Ping.getDefaultInstance())
            .build()
        val secondEvent = Api.ApiMessage.newBuilder()
            .setRequestId(2)
            .setPong(Common.Pong.getDefaultInstance())
            .build()
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32) { it.toByte() }), serverHello(), firstEvent, secondEvent)
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )

        val result = client.connect()

        assertTrue(result is ApiSessionClient.ConnectResult.Ready)
        assertEquals(firstEvent, withTimeout(1_000) { client.incoming.receive() })
        assertEquals(secondEvent, withTimeout(1_000) { client.incoming.receive() })
        client.close()
    }

    @Test
    fun afterReady_eofCompletesAwaitClosedAsPeerClosed() = runTest {
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32)), serverHello())
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )

        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)

        val closed = withTimeout(1_000) { client.awaitClosed() }
        assertTrue(closed is ApiSessionClient.ReadyClose.PeerClosed)
    }

    @Test
    fun afterReady_readerFailurePreservesCause() = runTest {
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32)), serverHello()),
            closeAfterInitial = false
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)
        val error = IllegalStateException("reader failed")

        transport.fail(error)

        val closed = withTimeout(1_000) { client.awaitClosed() }
        assertTrue(closed is ApiSessionClient.ReadyClose.Failed)
        assertSame(error, (closed as ApiSessionClient.ReadyClose.Failed).error)
    }

    @Test
    fun afterReady_terminalFrameCompletesAwaitClosedAsRejected() = runTest {
        val transport = FakeTransport(
            listOf(
                authRequired(ByteArray(32)),
                serverHello(),
                authReject("credential revoked")
            )
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)

        val closed = withTimeout(1_000) { client.awaitClosed() }

        assertEquals(
            ApiSessionClient.ReadyClose.Rejected("credential revoked"),
            closed
        )
        assertTrue(transport.closed)
    }

    @Test
    fun afterReady_requestScopedNonAuthErrorIsForwardedWithoutClosingSession() = runTest {
        val requestError = error(
            message = "topic unavailable",
            code = Common.ErrorCode.ERROR_CODE_UNAVAILABLE,
            requestId = 9
        )
        val ping = Api.ApiMessage.newBuilder()
            .setRequestId(10)
            .setPing(Common.Ping.getDefaultInstance())
            .build()
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32)), serverHello(), requestError, ping),
            closeAfterInitial = false
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)

        assertEquals(requestError, withTimeout(1_000) { client.incoming.receive() })
        assertEquals(ping, withTimeout(1_000) { client.incoming.receive() })
        assertTrue(!transport.closed)
        client.close()
    }

    @Test
    fun afterReady_connectionLevelNonAuthErrorIsRetryable() = runTest {
        val transport = FakeTransport(
            listOf(
                authRequired(ByteArray(32)),
                serverHello(),
                error(
                    message = "outbound queue overflow",
                    code = Common.ErrorCode.ERROR_CODE_INTERNAL,
                    requestId = 0
                )
            )
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)

        val closed = withTimeout(1_000) { client.awaitClosed() }

        assertEquals(ApiSessionClient.ReadyClose.PeerClosed("outbound queue overflow"), closed)
        assertTrue(transport.closed)
    }

    @Test
    fun afterReady_authErrorRequiresRepair() = runTest {
        val transport = FakeTransport(
            listOf(
                authRequired(ByteArray(32)),
                serverHello(),
                error(
                    message = "credentials revoked",
                    code = Common.ErrorCode.ERROR_CODE_AUTH_FAILED,
                    requestId = 0
                )
            )
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)

        val closed = withTimeout(1_000) { client.awaitClosed() }

        assertEquals(
            ApiSessionClient.ReadyClose.Rejected(
                "credentials revoked",
                Common.ErrorCode.ERROR_CODE_AUTH_FAILED
            ),
            closed
        )
    }

    @Test
    fun close_completesAwaitClosedOnceAsClientClosed() = runTest {
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32)), serverHello()),
            closeAfterInitial = false
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)

        client.close()
        client.close()

        assertEquals(ApiSessionClient.ReadyClose.ClientClosed, client.awaitClosed())
    }

    @Test
    fun sendReadyMessage_rejectsBeforeReadyAndSendsAfterReady() = runTest {
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32)), serverHello()),
            closeAfterInitial = false
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        val message = Api.ApiMessage.newBuilder()
            .setRequestId(7)
            .setPing(Common.Ping.getDefaultInstance())
            .build()

        try {
            client.sendReadyMessage(message)
            fail("Expected send before READY to fail")
        } catch (_: IllegalStateException) {
        }

        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)
        client.sendReadyMessage(message)

        assertEquals(message, transport.sent.last())
        client.close()
    }

    @Test
    fun connect_isSingleUseAfterClose() = runTest {
        val transport = FakeTransport(
            listOf(authRequired(ByteArray(32)), serverHello()),
            closeAfterInitial = false
        )
        val client = ApiSessionClient(
            transport = transport,
            handshake = ApiHandshake.knownClient("Pixel 9", "client-123", clientSecret),
            scope = backgroundScope
        )
        assertTrue(client.connect() is ApiSessionClient.ConnectResult.Ready)
        client.close()

        try {
            client.connect()
            fail("Expected a second connect to fail")
        } catch (_: IllegalStateException) {
        }
    }

    private class FakeTransport(
        initialIncoming: List<Api.ApiMessage>,
        closeAfterInitial: Boolean = true
    ) : ApiTransport {
        private val inbound = Channel<Api.ApiMessage>(Channel.UNLIMITED)
        val sent = mutableListOf<Api.ApiMessage>()
        var connected = false
            private set
        var closed = false
            private set

        init {
            initialIncoming.forEach { inbound.trySend(it) }
            if (closeAfterInitial) inbound.close()
        }

        override suspend fun connect() {
            connected = true
        }

        override suspend fun send(message: Api.ApiMessage) {
            check(connected) { "Fake transport not connected" }
            sent += message
        }

        override suspend fun receive(): Api.ApiMessage? {
            val result = inbound.receiveCatching()
            result.exceptionOrNull()?.let { throw it }
            return result.getOrNull()
        }

        override fun close() {
            closed = true
            inbound.close()
        }

        fun fail(error: Throwable) {
            inbound.close(error)
        }
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
                    .build()
            )
            .build()

    private fun serverHello(grantedClientId: String? = null): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setServerHello(
                Api.ServerHello.newBuilder()
                    .setApiVersionMajor(1)
                    .setApiVersionMinor(0)
                    .setServerName("Prodigy")
                    .setAppVersion("v1-test")
                    .setSessionId("session-1")
                    .setCapabilities(Api.Capabilities.getDefaultInstance())
                    .apply {
                        if (grantedClientId != null) setGrantedClientId(grantedClientId)
                    }
                    .build()
            )
            .build()

    private fun authReject(reason: String): Api.ApiMessage =
        Api.ApiMessage.newBuilder()
            .setAuthReject(Api.AuthReject.newBuilder().setReason(reason).build())
            .build()

    private fun error(
        message: String,
        code: Common.ErrorCode = Common.ErrorCode.ERROR_CODE_UNSUPPORTED_VERSION,
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
}

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
