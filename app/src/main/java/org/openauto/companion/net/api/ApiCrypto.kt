package org.openauto.companion.net.api

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ApiCrypto {
    const val SECRET_SIZE_BYTES = 32

    fun derivePairingSecret(pin: String, salt: ByteArray): ByteArray {
        require(pin.matches(Regex("\\d{6}"))) { "PIN must be exactly 6 digits" }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(pin.toByteArray(Charsets.UTF_8))
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
