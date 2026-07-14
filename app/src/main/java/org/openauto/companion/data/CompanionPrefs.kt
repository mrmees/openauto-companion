package org.openauto.companion.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class CompanionPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateStorageIfNeeded()
    }

    var vehicles: List<Vehicle>
        get() = VehicleStorageMigration.activeVehicles(
            rawVehiclesJson = prefs.getString(KEY_VEHICLES, "").orEmpty(),
            storedVersion = prefs.getInt(KEY_STORAGE_VERSION, 0)
        )
        set(value) {
            val valid = value.filter(VehicleStorageMigration::isValidV1)
            prefs.edit().putString(KEY_VEHICLES, Vehicle.listToJson(valid)).apply()
        }

    val isPaired: Boolean get() = vehicles.isNotEmpty()

    fun findBySsid(ssid: String): Vehicle? = vehicles.find { it.ssid == ssid }

    fun isDuplicateVehicle(vehicle: Vehicle): Boolean =
        vehicles.any { it.id == vehicle.id || it.ssid == vehicle.ssid }

    fun addVehicle(vehicle: Vehicle): Boolean {
        if (!VehicleStorageMigration.isValidV1(vehicle)) return false
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

    private fun migrateStorageIfNeeded() {
        val storedVersion = prefs.getInt(KEY_STORAGE_VERSION, 0)
        val plan = VehicleStorageMigration.plan(
            storedVersion = storedVersion,
            rawVehiclesJson = prefs.getString(KEY_VEHICLES, "").orEmpty()
        ) ?: return

        val editor = prefs.edit()
            .putString(KEY_VEHICLES, plan.vehiclesJson)
            .putInt(KEY_STORAGE_VERSION, plan.targetVersion)
        plan.keysToRemove.forEach(editor::remove)
        if (!editor.commit()) {
            Log.w(TAG, "Vehicle storage migration commit failed; it will retry next launch")
        }
    }

    private companion object {
        const val TAG = "CompanionPrefs"
        const val PREFS_NAME = "companion"
        const val KEY_VEHICLES = "vehicles_json"
        const val KEY_STORAGE_VERSION = "vehicle_storage_version"
    }
}
