package org.openauto.companion.net.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import prodigy.api.v1.Api
import java.net.ProtocolException
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiWebSocketTransport(
    private val url: String = "ws://$DEFAULT_HOST:$DEFAULT_PORT",
    private val client: OkHttpClient = OkHttpClient()
) : ApiTransport {
    private val incoming = LinkedBlockingQueue<Incoming>()
    private var webSocket: WebSocket? = null

    override suspend fun connect() {
        close()
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url).build()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@ApiWebSocketTransport.webSocket = webSocket
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    try {
                        incoming.offer(Incoming.Message(ApiFrameCodec.parse(bytes.toByteArray())))
                    } catch (error: Throwable) {
                        incoming.offer(Incoming.Failure(error))
                        webSocket.close(CLOSE_PROTOCOL_ERROR, "Invalid protobuf frame")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val error = ProtocolException("WebSocket text frames are not valid for External API v1")
                    incoming.offer(Incoming.Failure(error))
                    webSocket.close(CLOSE_PROTOCOL_ERROR, "Text frames are not supported")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    incoming.offer(Incoming.Closed)
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    incoming.offer(Incoming.Closed)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(t)
                    } else {
                        incoming.offer(Incoming.Failure(t))
                    }
                }
            }
            val socket = client.newWebSocket(request, listener)
            continuation.invokeOnCancellation {
                socket.cancel()
            }
        }
    }

    override suspend fun send(message: Api.ApiMessage) {
        withContext(Dispatchers.IO) {
            val activeSocket = checkNotNull(webSocket) { "Transport is not connected" }
            val sent = activeSocket.send(ApiFrameCodec.serialize(message).toByteString())
            check(sent) { "WebSocket send failed" }
        }
    }

    override suspend fun receive(): Api.ApiMessage? =
        withContext(Dispatchers.IO) {
            when (val event = incoming.take()) {
                Incoming.Closed -> null
                is Incoming.Failure -> throw event.error
                is Incoming.Message -> event.message
            }
        }

    override fun close() {
        val socket = webSocket
        webSocket = null
        if (socket != null) {
            socket.close(CLOSE_NORMAL, "Client closing")
            incoming.offer(Incoming.Closed)
        }
    }

    private sealed class Incoming {
        data class Message(val message: Api.ApiMessage) : Incoming()
        data class Failure(val error: Throwable) : Incoming()
        data object Closed : Incoming()
    }

    companion object {
        const val DEFAULT_HOST = "10.0.0.1"
        const val DEFAULT_PORT = 9811
        private const val CLOSE_NORMAL = 1000
        private const val CLOSE_PROTOCOL_ERROR = 1002
    }
}
