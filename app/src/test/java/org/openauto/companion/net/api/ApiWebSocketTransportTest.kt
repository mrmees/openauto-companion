package org.openauto.companion.net.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common
import java.net.ProtocolException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ApiWebSocketTransportTest {
    @Test
    fun sendAndReceive_usesOneBinaryMessagePerApiMessage() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        val receivedByServer = CompletableFuture<Api.ApiMessage>()
        val request = Api.ApiMessage.newBuilder()
            .setRequestId(3)
            .setPing(Common.Ping.getDefaultInstance())
            .build()
        val response = Api.ApiMessage.newBuilder()
            .setRequestId(3)
            .setPong(Common.Pong.getDefaultInstance())
            .build()

        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    receivedByServer.complete(ApiFrameCodec.parse(bytes.toByteArray()))
                    webSocket.send(ApiFrameCodec.serialize(response).toByteString())
                    webSocket.close(1000, "done")
                }
            })
        )
        server.start()

        try {
            ApiWebSocketTransport(url = server.wsUrl("/api"), client = client).use { transport ->
                transport.connect()
                transport.send(request)

                val actualResponse = withTimeout(5_000) { transport.receive() }

                assertEquals(response, actualResponse)
                assertEquals(request, receivedByServer.get(5, TimeUnit.SECONDS))
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test
    fun receive_treatsTextFramesAsProtocolErrors() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send("text is not valid for External API v1")
                    webSocket.close(1000, "done")
                }
            })
        )
        server.start()

        try {
            ApiWebSocketTransport(url = server.wsUrl("/api"), client = client).use { transport ->
                transport.connect()

                expectProtocolException {
                    withTimeout(5_000) { transport.receive() }
                }
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    private fun MockWebServer.wsUrl(path: String): String =
        url(path).toString().replace("http://", "ws://")

    private suspend fun expectProtocolException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ProtocolException")
        } catch (_: ProtocolException) {
            // Expected.
        }
    }
}
