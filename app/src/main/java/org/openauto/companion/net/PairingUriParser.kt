package org.openauto.companion.net

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PairingPayload(
    val ssid: String,
    val pin: String,
    val host: String?,
    val port: Int?
)

object PairingUriParser {
    fun parse(raw: String): PairingPayload? {
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return null
        }

        if (uri.scheme != "openauto" || uri.host != "pair") return null

        val params = parseQuery(uri.rawQuery ?: return null)
        val pin = params["pin"]?.trim()
        val ssid = params["ssid"]?.trim()

        if (pin.isNullOrEmpty() || !pin.matches(Regex("\\d{6}"))) return null
        if (ssid.isNullOrEmpty()) return null

        val host = params["host"]?.trim()?.ifBlank { null }
        val portRaw = params["port"]?.trim()
        val port = when {
            portRaw.isNullOrBlank() -> null
            else -> portRaw.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        }

        return PairingPayload(
            ssid = ssid,
            pin = pin,
            host = host,
            port = port
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&')
            .mapNotNull { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = token.substring(0, idx)
                val value = token.substring(idx + 1)
                decode(key) to decode(value)
            }
            .toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
}
