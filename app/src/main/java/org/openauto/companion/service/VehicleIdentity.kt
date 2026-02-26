package org.openauto.companion.service

object VehicleIdentity {
    fun resolve(vehicleId: String?, vehicleSsid: String?): String {
        val normalizedVehicleId = vehicleId?.trim().orEmpty()
        return normalizedVehicleId.ifBlank { vehicleSsid?.trim().orEmpty() }
    }

    fun matches(vehicleId: String, vehicleSsid: String, key: String): Boolean {
        return vehicleId == key || vehicleSsid == key
    }
}
