package org.openauto.companion.net.api

import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ApiCrypto {
    const val SECRET_SIZE_BYTES = 32

    fun derivePairingSecret(pairingCode: String, salt: ByteArray): ByteArray {
        require(PairingCode.normalize(pairingCode) == pairingCode) {
            "Pairing code must be canonical 120-bit Base32"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(pairingCode.toByteArray(Charsets.UTF_8))
        digest.update(salt)
        return digest.digest()
    }

    fun hmacSha256(secret: ByteArray, nonce: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(nonce)
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun decodeSecretHex(hex: String): ByteArray? {
        if (hex.length != SECRET_SIZE_BYTES * 2) return null
        return try {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            null
        }
    }
}

object PairingCode {
    const val CANONICAL_LENGTH = 24
    const val CREDENTIAL_GENERATION = 2
    private val PATTERN = Regex("[A-Z2-7]{$CANONICAL_LENGTH}")

    fun normalize(raw: String): String? {
        val canonical = canonicalCharacters(raw) ?: return null
        return canonical.takeIf(PATTERN::matches)
    }

    fun format(canonical: String): String {
        require(normalize(canonical) == canonical) { "Pairing code must be canonical" }
        return canonical.chunked(4).joinToString("-")
    }

    fun formatInput(raw: String): String? {
        val canonical = canonicalCharacters(raw) ?: return null
        if (canonical.length > CANONICAL_LENGTH) return null
        return canonical.chunked(4).joinToString("-")
    }

    private fun canonicalCharacters(raw: String): String? {
        if (raw.any {
                it != '-' && it != ' ' &&
                    it.uppercaseChar() !in 'A'..'Z' && it !in '2'..'7'
            }
        ) {
            return null
        }
        return raw
            .filterNot { it == '-' || it == ' ' }
            .uppercase(Locale.US)
    }
}
