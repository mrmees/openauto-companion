package org.openauto.companion.data

import org.json.JSONArray
import org.json.JSONObject
import org.openauto.companion.net.api.ApiCrypto
import java.util.UUID

data class Vehicle(
    val id: String = UUID.randomUUID().toString().take(8),
    val ssid: String,
    val name: String = ssid,
    val apiClientId: String,
    val apiSecretHex: String,
    val serverId: String? = null,
    val apiTcpPort: Int = DEFAULT_API_TCP_PORT,
    val socks5Enabled: Boolean = true,
    val audioKeepAlive: Boolean = false,
    val settingsHost: String? = null,
    val settingsPort: Int? = null,
    val displayWidth: Int? = null,
    val displayHeight: Int? = null
) {
    init {
        require(ssid.isNotBlank()) { "Vehicle SSID is required" }
        require(apiClientId.isNotBlank()) { "API client id is required" }
        require(ApiCrypto.decodeSecretHex(apiSecretHex) != null) {
            "API secret must be 32-byte hexadecimal"
        }
        require(apiTcpPort in 1..65535) { "API TCP port must be valid" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ssid", ssid)
        put("name", name)
        put("api_client_id", apiClientId)
        put("api_secret_hex", apiSecretHex)
        if (!serverId.isNullOrBlank()) put("server_id", serverId)
        put("api_tcp_port", apiTcpPort)
        put("socks5_enabled", socks5Enabled)
        put("audio_keep_alive", audioKeepAlive)
        if (!settingsHost.isNullOrBlank()) put("settings_host", settingsHost)
        if (settingsPort != null) put("settings_port", settingsPort)
        if (displayWidth != null) put("display_width", displayWidth)
        if (displayHeight != null) put("display_height", displayHeight)
    }

    companion object {
        private fun stableIdForSsid(ssid: String): String =
            UUID.nameUUIDFromBytes(ssid.toByteArray(Charsets.UTF_8)).toString().take(8)

        fun fromJson(json: JSONObject): Vehicle {
            val ssid = json.getString("ssid").trim()
            return Vehicle(
                id = json.optString("id", "").ifBlank { stableIdForSsid(ssid) },
                ssid = ssid,
                name = json.optString("name", ssid).ifBlank { ssid },
                apiClientId = json.getString("api_client_id").trim(),
                apiSecretHex = json.getString("api_secret_hex").trim(),
                serverId = json.optString("server_id", "").trim().ifBlank { null },
                apiTcpPort = json.optionalInt("api_tcp_port") ?: DEFAULT_API_TCP_PORT,
                socks5Enabled = json.optBoolean("socks5_enabled", true),
                audioKeepAlive = json.optBoolean("audio_keep_alive", false),
                settingsHost = json.optString("settings_host", "").trim().ifBlank { null },
                settingsPort = json.optionalInt("settings_port"),
                displayWidth = json.optionalInt("display_width"),
                displayHeight = json.optionalInt("display_height")
            )
        }

        fun listToJson(vehicles: List<Vehicle>): String =
            JSONArray(vehicles.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<Vehicle> {
            if (json.isBlank()) return emptyList()
            val array = JSONArray(json)
            return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }

        private fun JSONObject.optionalInt(key: String): Int? =
            if (has(key) && !isNull(key)) getInt(key) else null

        const val MAX_VEHICLES = 20
        const val DEFAULT_API_TCP_PORT = 9810
    }
}
