package org.openauto.companion.net.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiCryptoTest {
    @Test
    fun derivePairingSecret_hashesPinBytesThenSalt() {
        val salt = "000102030405060708090a0b0c0d0e0f".hexToBytes()

        val secret = ApiCrypto.derivePairingSecret(pin = "123456", salt = salt)

        assertEquals(
            "9b6b8bbdd36470ae0f82133563f749881a5453650e7062fd8ddb97a9b19d7778",
            ApiCrypto.toHex(secret)
        )
    }

    @Test
    fun hmacSha256_usesServerNonceBytes() {
        val secret =
            "9b6b8bbdd36470ae0f82133563f749881a5453650e7062fd8ddb97a9b19d7778".hexToBytes()
        val serverNonce =
            "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f".hexToBytes()

        val proof = ApiCrypto.hmacSha256(secret, serverNonce)

        assertEquals(
            "bfe897455a79edbf71ffcaa736c495bd76ccbce899875b93bf43ae969f6eac85",
            ApiCrypto.toHex(proof)
        )
    }

    @Test
    fun decodeSecretHex_acceptsExactlyThirtyTwoBytes() {
        val hex = "aa".repeat(32)

        assertArrayEquals(ByteArray(32) { 0xaa.toByte() }, ApiCrypto.decodeSecretHex(hex))
    }

    @Test
    fun decodeSecretHex_rejectsInvalidSecrets() {
        assertNull(ApiCrypto.decodeSecretHex("aa".repeat(31)))
        assertNull(ApiCrypto.decodeSecretHex("aa".repeat(33)))
        assertNull(ApiCrypto.decodeSecretHex("zz" + "aa".repeat(31)))
    }
}

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
