package org.openauto.companion.net.api

import org.openauto.companion.data.Vehicle
import prodigy.api.v1.System as SystemProto

class ApiPairingCredentialStore(
    private val loadVehicles: () -> List<Vehicle>,
    private val saveVehicles: (List<Vehicle>) -> Unit
) {
    fun persistPairingResult(
        vehicleId: String,
        ready: ApiSessionClient.ConnectResult.Ready
    ): Boolean {
        val credentials = ready.pairedCredentials ?: return false
        if (credentials.clientId.isBlank()) return false
        if (credentials.secret.size != ApiCrypto.SECRET_SIZE_BYTES) return false

        val serverId = if (ready.serverHello.hasServerId()) {
            ready.serverHello.serverId.takeIf { it.isNotBlank() }
        } else {
            null
        }

        return updateMatchedVehicle(vehicleId) { vehicle ->
            vehicle.copy(
                apiClientId = credentials.clientId,
                apiSecretHex = ApiCrypto.toHex(credentials.secret),
                apiMode = Vehicle.ApiMode.EXTERNAL_API_V1,
                serverId = serverId ?: vehicle.serverId
            )
        }
    }

    fun persistSystemStatus(
        vehicleId: String,
        systemStatus: SystemProto.SystemStatus
    ): Boolean {
        if (!systemStatus.hasDisplayWidth() || !systemStatus.hasDisplayHeight()) return false
        val width = systemStatus.displayWidth
        val height = systemStatus.displayHeight
        if (width <= 0 || height <= 0) return false

        return updateMatchedVehicle(vehicleId) { vehicle ->
            vehicle.copy(
                displayWidth = width,
                displayHeight = height
            )
        }
    }

    private fun updateMatchedVehicle(
        vehicleId: String,
        transform: (Vehicle) -> Vehicle
    ): Boolean {
        var matched = false
        val updated = loadVehicles().map { vehicle ->
            if (vehicle.id == vehicleId || vehicle.ssid == vehicleId) {
                matched = true
                transform(vehicle)
            } else {
                vehicle
            }
        }

        if (!matched) return false
        saveVehicles(updated)
        return true
    }
}
