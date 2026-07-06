package org.openauto.companion.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Vehicle(
    val id: String = UUID.randomUUID().toString().take(8),
    val ssid: String,
    val name: String = ssid,
    val sharedSecret: String,
    val apiClientId: String? = null,
    val apiSecretHex: String? = null,
    val apiMode: ApiMode = ApiMode.LEGACY,
    val socks5Enabled: Boolean = true,
    val audioKeepAlive: Boolean = false,
    val settingsHost: String? = null,
    val settingsPort: Int? = null,
    val displayWidth: Int? = null,
    val displayHeight: Int? = null
) {
    enum class ApiMode(val jsonValue: String) {
        LEGACY("legacy"),
        EXTERNAL_API_V1("external_api_v1");

        companion object {
            fun fromJson(value: String?): ApiMode =
                entries.firstOrNull { it.jsonValue == value } ?: LEGACY
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ssid", ssid)
        put("name", name)
        put("shared_secret", sharedSecret)
        if (!apiClientId.isNullOrBlank()) put("api_client_id", apiClientId)
        if (!apiSecretHex.isNullOrBlank()) put("api_secret_hex", apiSecretHex)
        if (apiMode != ApiMode.LEGACY) put("api_mode", apiMode.jsonValue)
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

        fun fromJson(json: JSONObject): Vehicle = Vehicle(
            id = json.optString("id", "").ifBlank { stableIdForSsid(json.getString("ssid")) },
            ssid = json.getString("ssid"),
            name = json.optString("name", json.getString("ssid")),
            sharedSecret = json.getString("shared_secret"),
            apiClientId = json.optString("api_client_id", "").ifBlank { null },
            apiSecretHex = json.optString("api_secret_hex", "").ifBlank { null },
            apiMode = ApiMode.fromJson(json.optString("api_mode", ApiMode.LEGACY.jsonValue)),
            socks5Enabled = json.optBoolean("socks5_enabled", true),
            audioKeepAlive = json.optBoolean("audio_keep_alive", false),
            settingsHost = json.optString("settings_host", "").ifBlank { null },
            settingsPort = if (json.has("settings_port")) json.optInt("settings_port") else null,
            displayWidth = if (json.has("display_width")) json.optInt("display_width") else null,
            displayHeight = if (json.has("display_height")) json.optInt("display_height") else null
        )

        fun listToJson(vehicles: List<Vehicle>): String =
            JSONArray(vehicles.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<Vehicle> {
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }

        const val MAX_VEHICLES = 20
    }
}
