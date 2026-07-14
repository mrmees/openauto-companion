package org.openauto.companion.net

import java.net.ConnectException
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class Socks5ServerTest {
    @Test
    fun isPrivateAddress_blocksRfc1918() {
        assertTrue(Socks5Server.isBlockedAddress("10.0.0.1"))
        assertTrue(Socks5Server.isBlockedAddress("172.16.0.1"))
        assertTrue(Socks5Server.isBlockedAddress("172.31.255.255"))
        assertTrue(Socks5Server.isBlockedAddress("192.168.1.1"))
        assertTrue(Socks5Server.isBlockedAddress("127.0.0.1"))
        assertTrue(Socks5Server.isBlockedAddress("169.254.1.1"))
    }

    @Test
    fun isPrivateAddress_allowsPublic() {
        assertFalse(Socks5Server.isBlockedAddress("8.8.8.8"))
        assertFalse(Socks5Server.isBlockedAddress("1.1.1.1"))
        assertFalse(Socks5Server.isBlockedAddress("142.250.80.46"))
    }

    @Test
    fun parseAuthRequest_validCredentials() {
        // RFC 1929: VER(1) ULEN(1) UNAME(ULEN) PLEN(1) PASSWD(PLEN)
        val user = "prodigy"
        val pass = "secret123"
        val bytes = byteArrayOf(
            0x01,                                  // version
            user.length.toByte(), *user.toByteArray(),
            pass.length.toByte(), *pass.toByteArray()
        )
        val (u, p) = Socks5Server.parseAuthRequest(bytes)
        assertEquals("prodigy", u)
        assertEquals("secret123", p)
    }

    @Test
    fun credentialsAccepted_allowsAnyUsernameWithCorrectPassword() {
        val server = Socks5Server(password = "secret123")

        assertTrue(server.credentialsAccepted("oap", "secret123"))
        assertTrue(server.credentialsAccepted("prodigy", "secret123"))
        assertTrue(server.credentialsAccepted("anything", "secret123"))
    }

    @Test
    fun credentialsAccepted_rejectsWrongPassword() {
        val server = Socks5Server(password = "secret123")

        assertFalse(server.credentialsAccepted("oap", "wrong"))
    }

    @Test
    fun startAndStop_areIdempotentWithoutDoubleBinding() {
        val server = Socks5Server(port = 0, password = "secret123", onStarted = {})

        try {
            server.start()
            val firstPort = server.listeningPort
            server.start()

            assertTrue(firstPort > 0)
            assertEquals(firstPort, server.listeningPort)
            assertTrue(server.isActive)
        } finally {
            server.stop()
            server.stop()
        }
    }

    @Test
    fun failedUpstreamConnectClosesTheUpstreamSocket() {
        val upstreamClosed = CountDownLatch(1)
        val upstream = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                throw ConnectException("test failure")
            }

            override fun close() {
                super.close()
                upstreamClosed.countDown()
            }
        }
        val server = Socks5Server(
            port = 0,
            password = "secret123",
            upstreamSocketFactory = { upstream },
            onStarted = {}
        )

        try {
            server.start()
            Socket("127.0.0.1", server.listeningPort).use { client ->
                client.soTimeout = 2_000
                val input = client.getInputStream()
                val output = client.getOutputStream()
                output.write(byteArrayOf(0x05, 0x01, 0x02))
                output.flush()
                assertArrayEquals(byteArrayOf(0x05, 0x02), input.readNBytes(2))

                val user = "oap".toByteArray()
                val pass = "secret123".toByteArray()
                output.write(byteArrayOf(0x01, user.size.toByte(), *user, pass.size.toByte(), *pass))
                output.flush()
                assertArrayEquals(byteArrayOf(0x01, 0x00), input.readNBytes(2))

                output.write(
                    byteArrayOf(
                        0x05, 0x01, 0x00, 0x01,
                        8, 8, 8, 8,
                        0x00, 0x50
                    )
                )
                output.flush()
                val reply = input.readNBytes(10)
                assertEquals(10, reply.size)
                assertEquals(0x05, reply[1].toInt() and 0xff)
            }

            assertTrue(upstreamClosed.await(1, TimeUnit.SECONDS))
        } finally {
            server.stop()
        }
    }
}
