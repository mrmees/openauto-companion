package org.openauto.companion.net

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PairingPayload(
    val host: String,
    val tcpPort: Int,
    val webSocketPort: Int,
    val pin: String,
    val ssid: String
)

object PairingUriParser {
    fun parse(raw: String): PairingPayload? {
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return null
        }

        if (uri.scheme != "prodigy" || uri.host != "pair") return null

        val params = try {
            parseQuery(uri.rawQuery ?: return null)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val host = params["host"]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val tcpPort = parsePort(params["tcp"]) ?: return null
        val webSocketPort = parsePort(params["ws"]) ?: return null
        val pin = params["pin"]?.trim()?.takeIf { it.matches(PIN_PATTERN) } ?: return null
        val ssid = params["ssid"]?.trim()?.takeIf { it.isNotBlank() } ?: return null

        return PairingPayload(
            host = host,
            tcpPort = tcpPort,
            webSocketPort = webSocketPort,
            pin = pin,
            ssid = ssid
        )
    }

    private fun parsePort(raw: String?): Int? =
        raw?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }

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

    private val PIN_PATTERN = Regex("[0-9]{6}")
}
