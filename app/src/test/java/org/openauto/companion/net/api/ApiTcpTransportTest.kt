package org.openauto.companion.net.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread

class ApiTcpTransportTest {
    @Test
    fun setReadTimeoutMillis_updatesConnectedSocket() = runTest {
        val server = ServerSocket(0)
        val accepted = FutureTask {
            server.use { it.accept().use { } }
        }
        thread(name = "api-tcp-timeout-test-server", start = true) { accepted.run() }
        val socket = Socket()

        ApiTcpTransport(
            host = "127.0.0.1",
            port = server.localPort,
            socketFactory = { socket }
        ).use { transport ->
            transport.connect()

            transport.setReadTimeoutMillis(4_321)

            assertEquals(4_321, socket.soTimeout)
        }
        accepted.get()
    }

    @Test
    fun sendAndReceive_usesLengthPrefixedApiMessages() = runTest {
        val server = ServerSocket(0)
        val expectedRequest = Api.ApiMessage.newBuilder()
            .setRequestId(11)
            .setPing(Common.Ping.getDefaultInstance())
            .build()
        val response = Api.ApiMessage.newBuilder()
            .setRequestId(11)
            .setPong(Common.Pong.getDefaultInstance())
            .build()
        val serverTask = FutureTask {
            server.use {
                it.accept().use { socket ->
                    val received = readTcpMessage(socket)
                    socket.getOutputStream().write(ApiFrameCodec.encodeTcpFrame(response))
                    socket.getOutputStream().flush()
                    received
                }
            }
        }
        thread(name = "api-tcp-test-server", start = true) { serverTask.run() }

        ApiTcpTransport(host = "127.0.0.1", port = server.localPort).use { transport ->
            transport.connect()
            transport.send(expectedRequest)

            val actualResponse = transport.receive()

            assertEquals(response, actualResponse)
            assertEquals(expectedRequest, serverTask.get())
        }
    }

    @Test
    fun receive_rejectsAdvertisedOversizedFramesBeforeParsing() = runTest {
        val server = ServerSocket(0)
        val serverTask = FutureTask {
            server.use {
                it.accept().use { socket ->
                    val tooLarge = ApiFrameCodec.MAX_FRAME_SIZE_BYTES + 1
                    socket.getOutputStream().write(
                        byteArrayOf(
                            ((tooLarge ushr 24) and 0xff).toByte(),
                            ((tooLarge ushr 16) and 0xff).toByte(),
                            ((tooLarge ushr 8) and 0xff).toByte(),
                            (tooLarge and 0xff).toByte()
                        )
                    )
                    socket.getOutputStream().flush()
                }
            }
        }
        thread(name = "api-tcp-oversized-test-server", start = true) { serverTask.run() }

        ApiTcpTransport(host = "127.0.0.1", port = server.localPort).use { transport ->
            transport.connect()

            expectIllegalArgument { transport.receive() }
        }
        serverTask.get()
    }

    private fun readTcpMessage(socket: Socket): Api.ApiMessage {
        val input = socket.getInputStream()
        val prefix = input.readExactly(Int.SIZE_BYTES)
        val length = ((prefix[0].toInt() and 0xff) shl 24) or
            ((prefix[1].toInt() and 0xff) shl 16) or
            ((prefix[2].toInt() and 0xff) shl 8) or
            (prefix[3].toInt() and 0xff)
        return ApiFrameCodec.parse(input.readExactly(length))
    }

    private fun InputStream.readExactly(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(bytes, offset, size - offset)
            if (read < 0) throw IllegalStateException("Unexpected EOF")
            offset += read
        }
        return bytes
    }

    private suspend fun expectIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
