package org.openauto.companion.net.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import prodigy.api.v1.Api
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

class ApiTcpTransport(
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val socketFactory: () -> Socket = { Socket() }
) : ApiTransport {
    private var socket: Socket? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            close()
            socket = socketFactory().also {
                it.connect(InetSocketAddress(host, port), connectTimeoutMs)
            }
        }
    }

    override suspend fun send(message: Api.ApiMessage) {
        withContext(Dispatchers.IO) {
            val activeSocket = requireSocket()
            activeSocket.getOutputStream().apply {
                write(ApiFrameCodec.encodeTcpFrame(message))
                flush()
            }
        }
    }

    override suspend fun receive(): Api.ApiMessage? =
        withContext(Dispatchers.IO) {
            val activeSocket = requireSocket()
            val input = activeSocket.getInputStream()
            val prefix = input.readExactlyOrNull(Int.SIZE_BYTES) ?: return@withContext null
            val length = ((prefix[0].toInt() and 0xff) shl 24) or
                ((prefix[1].toInt() and 0xff) shl 16) or
                ((prefix[2].toInt() and 0xff) shl 8) or
                (prefix[3].toInt() and 0xff)
            require(length >= 0) { "TCP frame length is negative" }
            require(length <= ApiFrameCodec.MAX_FRAME_SIZE_BYTES) { "Frame exceeds 256 KiB limit" }
            ApiFrameCodec.parse(input.readExactly(length))
        }

    override fun close() {
        try {
            socket?.close()
        } finally {
            socket = null
        }
    }

    private fun requireSocket(): Socket =
        checkNotNull(socket) { "Transport is not connected" }

    private fun InputStream.readExactlyOrNull(size: Int): ByteArray? {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(bytes, offset, size - offset)
            if (read < 0) {
                if (offset == 0) return null
                throw EOFException("Unexpected EOF while reading TCP frame")
            }
            offset += read
        }
        return bytes
    }

    private fun InputStream.readExactly(size: Int): ByteArray =
        readExactlyOrNull(size) ?: throw EOFException("Unexpected EOF while reading TCP frame")

    companion object {
        const val DEFAULT_HOST = "10.0.0.1"
        const val DEFAULT_PORT = 9810
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000
    }
}
