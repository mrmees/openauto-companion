package org.openauto.companion.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Vehicle(
    val id: String = UUID.randomUUID().toString().take(8),
    val ssid: String,
    val name: String = ssid,
    val sharedSecret: String,
    val socks5Enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ssid", ssid)
        put("name", name)
        put("shared_secret", sharedSecret)
        put("socks5_enabled", socks5Enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): Vehicle = Vehicle(
            id = json.optString("id", UUID.randomUUID().toString().take(8)),
            ssid = json.getString("ssid"),
            name = json.optString("name", json.getString("ssid")),
            sharedSecret = json.getString("shared_secret"),
            socks5Enabled = json.optBoolean("socks5_enabled", true)
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
