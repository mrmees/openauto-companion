package org.openauto.companion.net.api

import org.openauto.companion.data.Vehicle

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

        var matched = false
        val updated = loadVehicles().map { vehicle ->
            if (vehicle.id == vehicleId || vehicle.ssid == vehicleId) {
                matched = true
                vehicle.copy(
                    apiClientId = credentials.clientId,
                    apiSecretHex = ApiCrypto.toHex(credentials.secret),
                    apiMode = Vehicle.ApiMode.EXTERNAL_API_V1
                )
            } else {
                vehicle
            }
        }

        if (!matched) return false
        saveVehicles(updated)
        return true
    }
}
