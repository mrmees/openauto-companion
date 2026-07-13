package org.openauto.companion.data

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.openauto.companion.net.api.ApiCrypto

object VehicleStorageMigration {
    const val CURRENT_VERSION = 1
    val LEGACY_KEYS = setOf("shared_secret", "target_ssid", "socks5_enabled")

    data class Plan(
        val vehiclesJson: String,
        val keysToRemove: Set<String>,
        val targetVersion: Int
    )

    fun plan(storedVersion: Int, rawVehiclesJson: String): Plan? {
        if (storedVersion >= CURRENT_VERSION) return null
        return Plan(
            vehiclesJson = Vehicle.listToJson(activeVehicles(rawVehiclesJson)),
            keysToRemove = LEGACY_KEYS,
            targetVersion = CURRENT_VERSION
        )
    }

    fun activeVehicles(rawVehiclesJson: String): List<Vehicle> {
        if (rawVehiclesJson.isBlank()) return emptyList()
        val array = try {
            JSONArray(rawVehiclesJson)
        } catch (_: Exception) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val vehicle = parseTransitionalVehicle(json) ?: continue
                if (isValidV1(vehicle)) add(vehicle)
            }
        }
    }

    fun isValidV1(vehicle: Vehicle): Boolean =
        vehicle.apiMode == Vehicle.ApiMode.EXTERNAL_API_V1 &&
            !vehicle.apiClientId.isNullOrBlank() &&
            ApiCrypto.decodeSecretHex(vehicle.apiSecretHex.orEmpty()) != null

    private fun parseTransitionalVehicle(json: JSONObject): Vehicle? {
        return try {
            val ssid = json.getString("ssid").trim()
            if (ssid.isBlank()) return null
            Vehicle(
                id = json.optString("id", "").ifBlank { stableIdForSsid(ssid) },
                ssid = ssid,
                name = json.optString("name", ssid).ifBlank { ssid },
                sharedSecret = json.optString("shared_secret", ""),
                apiClientId = json.optString("api_client_id", "").trim().ifBlank { null },
                apiSecretHex = json.optString("api_secret_hex", "").trim().ifBlank { null },
                apiMode = Vehicle.ApiMode.fromJson(
                    json.optString("api_mode", Vehicle.ApiMode.LEGACY.jsonValue)
                ),
                serverId = json.optString("server_id", "").trim().ifBlank { null },
                socks5Enabled = json.optBoolean("socks5_enabled", true),
                audioKeepAlive = json.optBoolean("audio_keep_alive", false),
                settingsHost = json.optString("settings_host", "").trim().ifBlank { null },
                settingsPort = json.optionalInt("settings_port"),
                displayWidth = json.optionalInt("display_width"),
                displayHeight = json.optionalInt("display_height")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun stableIdForSsid(ssid: String): String =
        UUID.nameUUIDFromBytes(ssid.toByteArray(Charsets.UTF_8)).toString().take(8)
}
