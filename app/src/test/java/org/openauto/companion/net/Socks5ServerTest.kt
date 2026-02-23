package org.openauto.companion.net

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
}
