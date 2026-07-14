package org.openauto.companion.service

import java.security.SecureRandom
import java.util.Base64

object ProxyPassword {
    private const val ENTROPY_BYTES = 16
    private val secureRandom = SecureRandom()

    fun generate(
        byteSource: (size: Int) -> ByteArray = { size ->
            ByteArray(size).also(secureRandom::nextBytes)
        }
    ): String {
        val entropy = byteSource(ENTROPY_BYTES)
        require(entropy.size == ENTROPY_BYTES) {
            "Proxy password source must return $ENTROPY_BYTES bytes"
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy)
    }
}
