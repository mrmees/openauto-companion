package org.openauto.companion.net.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiCryptoTest {
    @Test
    fun derivePairingSecret_matchesSharedSecureCodeVector() {
        val salt = "0123456789abcdef".toByteArray()

        val secret = ApiCrypto.derivePairingSecret(
            pairingCode = "ABCDEFGHIJKLMNOPQRSTUVWX",
            salt = salt
        )

        assertEquals(
            "9786e3d8dd45530435cc5b09d71e93b76dccf0e3e402ae7af5bdb6400a5c1472",
            ApiCrypto.toHex(secret)
        )
    }

    @Test
    fun hmacSha256_usesServerNonceBytes() {
        val secret =
            "9786e3d8dd45530435cc5b09d71e93b76dccf0e3e402ae7af5bdb6400a5c1472".hexToBytes()
        val serverNonce =
            "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f".hexToBytes()

        val proof = ApiCrypto.hmacSha256(secret, serverNonce)

        assertEquals(
            "b55022f1f823e77b5d0117e45a905e3bc5cc87b7ef0a25090d01fa85191711d6",
            ApiCrypto.toHex(proof)
        )
    }

    @Test
    fun pairingCode_normalizesOnlyBase32WithVisualSeparators() {
        assertEquals(
            "ABCDEFGHIJKLMNOPQRSTUVWX",
            PairingCode.normalize("abcd-efgh-ijkl-mnop-qrst-uvwx")
        )
        assertEquals(
            "ABCD-EFGH-IJKL-MNOP-QRST-UVWX",
            PairingCode.format("ABCDEFGHIJKLMNOPQRSTUVWX")
        )
        assertNull(PairingCode.normalize("ABCDEFGHIJKLMNOPQRSTUVX0"))
        assertNull(PairingCode.normalize("ABCDEFGHIJKLMNOPQRSTUVW"))
        assertNull(PairingCode.normalize("\u0131BCDEFGHIJKLMNOPQRSTUVWX"))
        assertNull(PairingCode.normalize("\u017FBCDEFGHIJKLMNOPQRSTUVWX"))
    }

    @Test
    fun pairingCode_formatsPartialManualInputAndRejectsInvalidCharactersOrOverflow() {
        assertEquals("ABCD-EFGH-IJKL", PairingCode.formatInput("abcd efgh-ijkl"))
        assertEquals(
            "ABCD-EFGH-IJKL-MNOP-QRST-UVWX",
            PairingCode.formatInput("abcdefghijklmnopqrstuvwx")
        )
        assertNull(PairingCode.formatInput("ABCD-EFGH-IJKL-MNOP-QRST-UVWX2"))
        assertNull(PairingCode.formatInput("ABCD-EFGH-IJKL-MNOP-QRST-UVX0"))
        assertNull(PairingCode.formatInput("\u0131BCD"))
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
