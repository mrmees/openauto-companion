package org.openauto.companion.net.api

import org.openauto.companion.data.Vehicle
import prodigy.api.v1.System as SystemProto

class ApiPairingCredentialStore(
    private val loadVehicles: () -> List<Vehicle>,
    private val saveVehicles: (List<Vehicle>) -> Unit
) {
    fun containsSsid(ssid: String): Boolean {
        val normalized = ssid.trim()
        if (normalized.isBlank()) return false
        return loadVehicles().any { it.ssid == normalized }
    }

    fun persistNewPairing(
        ssid: String,
        displayName: String,
        host: String,
        ready: ApiSessionClient.ConnectResult.Ready
    ): Vehicle? {
        val normalizedSsid = ssid.trim()
        val normalizedHost = host.trim()
        if (normalizedSsid.isBlank() || normalizedHost.isBlank()) return null

        val credentials = ready.pairedCredentials ?: return null
        val clientId = credentials.clientId.trim()
        if (clientId.isBlank()) return null
        if (credentials.secret.size != ApiCrypto.SECRET_SIZE_BYTES) return null

        val current = loadVehicles()
        if (current.size >= Vehicle.MAX_VEHICLES) return null
        if (current.any { it.ssid == normalizedSsid }) return null

        val serverId = if (ready.serverHello.hasServerId()) {
            ready.serverHello.serverId.trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
        val vehicle = Vehicle(
            ssid = normalizedSsid,
            name = displayName.trim().ifBlank { normalizedSsid },
            sharedSecret = "",
            apiClientId = clientId,
            apiSecretHex = ApiCrypto.toHex(credentials.secret),
            apiMode = Vehicle.ApiMode.EXTERNAL_API_V1,
            serverId = serverId,
            settingsHost = normalizedHost
        )
        saveVehicles(current + vehicle)
        return vehicle
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
