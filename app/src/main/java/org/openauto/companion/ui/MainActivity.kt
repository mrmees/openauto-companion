package org.openauto.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openauto.companion.CompanionApp
import org.openauto.companion.data.CompanionPrefs
import org.openauto.companion.data.Vehicle
import org.openauto.companion.service.CompanionService
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private lateinit var prefs: CompanionPrefs

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startMonitoringIfPaired()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = CompanionPrefs(this)

        requestPermissions()

        setContent {
            MaterialTheme {
                var vehicles by remember { mutableStateOf(prefs.vehicles) }
                var screen by remember { mutableStateOf<Screen>(
                    if (vehicles.isEmpty()) Screen.Pairing else Screen.VehicleList
                ) }

                val isConnected by CompanionService.connected.collectAsStateWithLifecycle()
                val isSocks5Active by CompanionService.socks5Active.collectAsStateWithLifecycle()
                val isAudioKeepAliveActive by CompanionService.audioKeepAliveActive.collectAsStateWithLifecycle()
                val connectedVehicleName by CompanionService.vehicleName.collectAsStateWithLifecycle()

                var showPairingSuccess by remember { mutableStateOf<String?>(null) }

                // Determine which SSID is currently connected (by matching vehicle name back)
                val connectedSsid = if (isConnected) {
                    vehicles.find { it.name == connectedVehicleName }?.ssid
                } else null

                showPairingSuccess?.let { ssid ->
                    AlertDialog(
                        onDismissRequest = { showPairingSuccess = null },
                        icon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { Text("Pairing Successful") },
                        text = { Text("Vehicle \"$ssid\" has been paired. The app will connect automatically when in range.") },
                        confirmButton = {
                            TextButton(onClick = { showPairingSuccess = null }) {
                                Text("OK")
                            }
                        }
                    )
                }

                when (val s = screen) {
                    is Screen.VehicleList -> {
                        VehicleListScreen(
                            vehicles = vehicles,
                            connectedSsid = connectedSsid,
                            onVehicleTap = { screen = Screen.Status(it) },
                            onAddVehicle = { screen = Screen.Pairing },
                            onRemoveVehicle = { vehicle ->
                                prefs.removeVehicle(vehicle.id)
                                vehicles = prefs.vehicles
                                restartMonitoring()
                            }
                        )
                    }
                    is Screen.Pairing -> {
                        if (vehicles.isNotEmpty()) {
                            BackHandler { screen = Screen.VehicleList }
                        }
                        PairingScreen(
                            onPaired = { ssid, name, pin ->
                                val secret = deriveSecret(pin)
                                val vehicle = Vehicle(ssid = ssid, name = name, sharedSecret = secret)
                                prefs.addVehicle(vehicle)
                                vehicles = prefs.vehicles
                                restartMonitoring()
                                screen = Screen.VehicleList
                                showPairingSuccess = name
                            },
                            onCancel = if (vehicles.isNotEmpty()) {
                                { screen = Screen.VehicleList }
                            } else null,
                            onScanQr = { screen = Screen.QrScan }
                        )
                    }
                    is Screen.QrScan -> {
                        BackHandler { screen = Screen.Pairing }
                        QrScanScreen(
                            onScanned = { ssid, pin, host, port ->
                                val secret = deriveSecret(pin)
                                val vehicle = Vehicle(
                                    ssid = ssid,
                                    name = ssid,
                                    sharedSecret = secret,
                                    settingsHost = host,
                                    settingsPort = port
                                )
                                prefs.addVehicle(vehicle)
                                vehicles = prefs.vehicles
                                restartMonitoring()
                                screen = Screen.VehicleList
                                showPairingSuccess = ssid
                            },
                            onCancel = { screen = Screen.Pairing }
                        )
                    }
                    is Screen.Status -> {
                        BackHandler { screen = Screen.VehicleList }
                        val vehicle = s.vehicle
                        val isThisConnected = isConnected && connectedSsid == vehicle.ssid
                        val status = CompanionStatus(
                            connected = isThisConnected,
                            sharingTime = true,
                            sharingGps = true,
                            sharingBattery = true,
                            socks5Active = isThisConnected && isSocks5Active,
                            audioKeepAliveActive = isThisConnected && isAudioKeepAliveActive,
                            ssid = vehicle.ssid
                        )
                        var socks5Enabled by remember(vehicle.id) {
                            mutableStateOf(vehicle.socks5Enabled)
                        }
                        var audioKeepAlive by remember(vehicle.id) {
                            mutableStateOf(vehicle.audioKeepAlive)
                        }

                        StatusScreen(
                            vehicleName = vehicle.name,
                            status = status,
                            socks5Enabled = socks5Enabled,
                            onSocks5Toggle = {
                                socks5Enabled = it
                                prefs.updateVehicle(vehicle.id) { v -> v.copy(socks5Enabled = it) }
                            },
                            audioKeepAlive = audioKeepAlive,
                            onAudioKeepAliveToggle = {
                                audioKeepAlive = it
                                prefs.updateVehicle(vehicle.id) { v -> v.copy(audioKeepAlive = it) }
                            },
                            onUnpair = {
                                prefs.removeVehicle(vehicle.id)
                                vehicles = prefs.vehicles
                                restartMonitoring()
                                screen = if (vehicles.isEmpty()) Screen.Pairing else Screen.VehicleList
                            },
                            onBack = { screen = Screen.VehicleList }
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startMonitoringIfPaired()
        }
    }

    private fun deriveSecret(pin: String): String {
        val material = "$pin:openauto-companion-v1"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun startMonitoringIfPaired() {
        if (prefs.isPaired) {
            (application as CompanionApp).startWifiMonitor(prefs.vehicles)
        }
    }

    private fun restartMonitoring() {
        if (prefs.isPaired) {
            (application as CompanionApp).startWifiMonitor(prefs.vehicles)
        }
    }
}

private sealed class Screen {
    data object VehicleList : Screen()
    data object Pairing : Screen()
    data object QrScan : Screen()
    data class Status(val vehicle: Vehicle) : Screen()
}
