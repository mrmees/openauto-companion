package org.openauto.companion.data

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.openauto.companion.net.api.ApiCrypto
import org.openauto.companion.net.api.PairingCode

object VehicleStorageMigration {
    const val CURRENT_VERSION = 2
    val LEGACY_KEYS = setOf("shared_secret", "target_ssid", "socks5_enabled")

    data class Plan(
        val vehiclesJson: String,
        val keysToRemove: Set<String>,
        val targetVersion: Int
    )

    fun plan(storedVersion: Int, rawVehiclesJson: String): Plan? {
        if (storedVersion >= CURRENT_VERSION) return null
        return Plan(
            vehiclesJson = Vehicle.listToJson(activeVehicles(rawVehiclesJson, storedVersion)),
            keysToRemove = LEGACY_KEYS,
            targetVersion = CURRENT_VERSION
        )
    }

    fun activeVehicles(
        rawVehiclesJson: String,
        storedVersion: Int = 0
    ): List<Vehicle> {
        if (rawVehiclesJson.isBlank()) return emptyList()
        val array = try {
            JSONArray(rawVehiclesJson)
        } catch (_: Exception) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val vehicle = if (storedVersion >= 1) {
                    parseCurrentVehicle(json)
                } else {
                    parseTransitionalVehicle(json)
                } ?: continue
                if (isValidCurrent(vehicle)) add(vehicle)
            }
        }
    }

    fun isValidCurrent(vehicle: Vehicle): Boolean =
        vehicle.apiClientId.isNotBlank() &&
            ApiCrypto.decodeSecretHex(vehicle.apiSecretHex) != null &&
            vehicle.apiCredentialGeneration == PairingCode.CREDENTIAL_GENERATION

    private fun parseCurrentVehicle(json: JSONObject): Vehicle? = try {
        Vehicle.fromJson(json)
    } catch (_: Exception) {
        null
    }

    private fun parseTransitionalVehicle(json: JSONObject): Vehicle? {
        return try {
            if (json.optString("api_mode") != "external_api_v1") return null
            val ssid = json.getString("ssid").trim()
            if (ssid.isBlank()) return null
            Vehicle(
                id = json.optString("id", "").ifBlank { stableIdForSsid(ssid) },
                ssid = ssid,
                name = json.optString("name", ssid).ifBlank { ssid },
                apiClientId = json.getString("api_client_id").trim(),
                apiSecretHex = json.getString("api_secret_hex").trim(),
                apiCredentialGeneration = json.optInt(
                    "api_credential_generation",
                    Vehicle.LEGACY_API_CREDENTIAL_GENERATION
                ),
                serverId = json.optString("server_id", "").trim().ifBlank { null },
                apiTcpPort = json.optionalInt("api_tcp_port") ?: Vehicle.DEFAULT_API_TCP_PORT,
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
