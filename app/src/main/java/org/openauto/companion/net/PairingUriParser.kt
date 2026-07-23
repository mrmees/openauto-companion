package org.openauto.companion.net

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.openauto.companion.net.api.PairingCode

data class PairingPayload(
    val host: String,
    val tcpPort: Int,
    val webSocketPort: Int,
    val code: String,
    val ssid: String
) {
    // Transitional source alias removed by the UI task in this same wave.
    val pin: String get() = code
}

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
        val code = params["code"]?.let(PairingCode::normalize) ?: return null
        val ssid = params["ssid"]?.trim()?.takeIf { it.isNotBlank() } ?: return null

        return PairingPayload(
            host = host,
            tcpPort = tcpPort,
            webSocketPort = webSocketPort,
            code = code,
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
        // URI queries use RFC 3986 encoding: a literal '+' is data, not the
        // application/x-www-form-urlencoded spelling of a space.
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.toString())

}
