package org.openauto.companion.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class CompanionPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    init { migrateIfNeeded() }

    var vehicles: List<Vehicle>
        get() = Vehicle.listFromJson(prefs.getString("vehicles_json", "") ?: "")
        set(value) = prefs.edit().putString("vehicles_json", Vehicle.listToJson(value)).apply()

    val isPaired: Boolean get() = vehicles.isNotEmpty()

    fun findBySsid(ssid: String): Vehicle? = vehicles.find { it.ssid == ssid }

    fun isDuplicateVehicle(vehicle: Vehicle): Boolean =
        vehicles.any { it.id == vehicle.id || it.ssid == vehicle.ssid }

    fun addVehicle(vehicle: Vehicle): Boolean {
        val current = vehicles
        if (current.size >= Vehicle.MAX_VEHICLES) return false
        if (isDuplicateVehicle(vehicle)) return false
        vehicles = current + vehicle
        return true
    }

    fun removeVehicle(id: String) {
        vehicles = vehicles.filter { it.id != id && it.ssid != id }
    }

    fun updateVehicle(id: String, transform: (Vehicle) -> Vehicle) {
        vehicles = vehicles.map { if (it.id == id || it.ssid == id) transform(it) else it }
    }

    /** Migrate legacy single-vehicle prefs to vehicle list (idempotent). */
    private fun migrateIfNeeded() {
        if (prefs.contains("vehicles_json")) return
        val secret = prefs.getString("shared_secret", "") ?: ""
        if (secret.isEmpty()) return

        val ssid = prefs.getString("target_ssid", "OpenAutoProdigy") ?: "OpenAutoProdigy"
        val socks5 = prefs.getBoolean("socks5_enabled", true)
        val vehicle = Vehicle(ssid = ssid, sharedSecret = secret, socks5Enabled = socks5)
        vehicles = listOf(vehicle)
        Log.i("CompanionPrefs", "Migrated legacy prefs to vehicle: ssid=$ssid")

        // Clean up legacy keys
        prefs.edit()
            .remove("shared_secret")
            .remove("target_ssid")
            .remove("socks5_enabled")
            .apply()
    }
}
