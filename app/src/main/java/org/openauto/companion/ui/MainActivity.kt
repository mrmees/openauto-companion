package org.openauto.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.openauto.companion.CompanionApp
import org.openauto.companion.data.CompanionPrefs
import org.openauto.companion.data.Vehicle
import org.openauto.companion.net.NetworkSocketFactory
import org.openauto.companion.net.SettingsUrlBuilder
import org.openauto.companion.net.ThemeTransfer
import org.openauto.companion.net.WifiNetworkResolver
import org.openauto.companion.net.api.ApiPairingCoordinator
import org.openauto.companion.net.api.ApiPairingCredentialStore
import org.openauto.companion.net.api.ApiPairingDraft
import org.openauto.companion.service.CompanionService
import org.openauto.companion.service.VehicleIdentity

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
                val connectedVehicleId by CompanionService.vehicleId.collectAsStateWithLifecycle()
                val connectionIssue by CompanionService.connectionIssue.collectAsStateWithLifecycle()

                var showPairingSuccess by remember { mutableStateOf<String?>(null) }
                var pairingState by remember { mutableStateOf<PairingUiState>(PairingUiState.Idle) }

                val connectedVehicle = if (isConnected) {
                    vehicles.find { it.id == connectedVehicleId || it.ssid == connectedVehicleId }
                } else null
                val connectedSsid = connectedVehicle?.ssid
                    ?: if (isConnected && connectedVehicleName.isNotBlank()) connectedVehicleName else null

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
                            onAddVehicle = {
                                pairingState = PairingUiState.Idle
                                screen = Screen.Pairing
                            },
                            onRemoveVehicle = { vehicle ->
                                prefs.removeVehicle(vehicle.id)
                                vehicles = prefs.vehicles
                                restartMonitoring()
                            }
                        )
                    }
                    is Screen.Pairing -> {
                        BackHandler(enabled = vehicles.isNotEmpty()) {
                            if (pairingState !is PairingUiState.Pairing) {
                                pairingState = PairingUiState.Idle
                                screen = Screen.VehicleList
                            }
                        }
                        PairingScreen(
                            state = pairingState,
                            onPair = { ssid, name, pin ->
                                if (pairingState is PairingUiState.Pairing) return@PairingScreen
                                pairingState = PairingUiState.Pairing
                                lifecycleScope.launch {
                                    val credentialStore = ApiPairingCredentialStore(
                                        loadVehicles = { prefs.vehicles },
                                        saveVehicles = { prefs.vehicles = it }
                                    )
                                    val networkResolver = WifiNetworkResolver(this@MainActivity)
                                    val coordinator = ApiPairingCoordinator(
                                        credentialStore = credentialStore,
                                        resolveSocketFactory = { targetSsid ->
                                            networkResolver.resolve(targetSsid)?.let { network ->
                                                NetworkSocketFactory.forNetwork(
                                                    network,
                                                    onFallback = {
                                                        Log.w(
                                                            TAG,
                                                            "Wi-Fi-bound pairing socket was denied; retrying API v1 TCP unbound"
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    )
                                    when (
                                        val result = coordinator.pair(
                                            ApiPairingDraft(
                                                ssid = ssid,
                                                displayName = name
                                            ),
                                            pin = pin
                                        )
                                    ) {
                                        is ApiPairingCoordinator.Result.Success -> {
                                            vehicles = prefs.vehicles
                                            restartMonitoring()
                                            pairingState = PairingUiState.Idle
                                            screen = Screen.VehicleList
                                            showPairingSuccess = result.vehicle.name
                                        }
                                        is ApiPairingCoordinator.Result.Failure -> {
                                            pairingState = PairingUiState.Failed(result.message)
                                        }
                                        ApiPairingCoordinator.Result.Cancelled -> {
                                            pairingState = PairingUiState.Idle
                                        }
                                    }
                                }
                            },
                            onCancel = if (vehicles.isNotEmpty()) {
                                {
                                    pairingState = PairingUiState.Idle
                                    screen = Screen.VehicleList
                                }
                            } else null,
                        )
                    }
                    is Screen.Status -> {
                        BackHandler { screen = Screen.VehicleList }
                        val vehicle = s.vehicle
                        val isThisRuntime = VehicleIdentity.matches(
                            vehicle.id,
                            vehicle.ssid,
                            connectedVehicleId
                        )
                        val isThisConnected = isConnected && isThisRuntime
                        val status = CompanionStatus(
                            connected = isThisConnected,
                            headUnitAvailable = isThisRuntime,
                            connectionMessage = connectionIssue.takeIf { isThisRuntime },
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
                                CompanionService.setInternetSharingStatic(vehicle.id, it)
                            },
                            audioKeepAlive = audioKeepAlive,
                            onAudioKeepAliveToggle = {
                                audioKeepAlive = it
                                prefs.updateVehicle(vehicle.id) { v -> v.copy(audioKeepAlive = it) }
                            },
                            onOpenSettingsPage = {
                                screen = Screen.WebConfig(vehicle)
                            },
                            onOpenThemeBuilder = {
                                screen = Screen.ThemeBuilder(vehicle)
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
                    is Screen.ThemeBuilder -> {
                        BackHandler { screen = Screen.Status(s.vehicle) }
                        LaunchedEffect(Unit) {
                            CompanionService.clearThemeTransferResult()
                        }
                        val displayW by CompanionService.displayWidth.collectAsStateWithLifecycle()
                        val displayH by CompanionService.displayHeight.collectAsStateWithLifecycle()
                        val transferResult by CompanionService.themeTransferResult.collectAsStateWithLifecycle()

                        ThemeBuilderScreen(
                            displayWidth = displayW ?: 1024,
                            displayHeight = displayH ?: 600,
                            onSendTheme = { themeJson, wallpaperBytes ->
                                CompanionService.sendThemeStatic(s.vehicle.settingsHost, themeJson, wallpaperBytes)
                            },
                            transferResult = when (val r = transferResult) {
                                is ThemeTransfer.TransferResult.Success -> "success"
                                is ThemeTransfer.TransferResult.Failed -> r.reason
                                null -> null
                            },
                            onBack = { screen = Screen.Status(s.vehicle) }
                        )
                    }
                    is Screen.WebConfig -> {
                        val url = SettingsUrlBuilder.build(s.vehicle.settingsHost, s.vehicle.settingsPort)
                        val wifiNetwork = (application as CompanionApp).wifiMonitor?.getWifiNetwork()
                        WebConfigScreen(
                            vehicleName = s.vehicle.name,
                            url = url,
                            wifiNetwork = wifiNetwork,
                            onBack = { screen = Screen.Status(s.vehicle) }
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

    private fun startMonitoringIfPaired() {
        if (prefs.isPaired) {
            (application as CompanionApp).startWifiMonitor(prefs.vehicles)
        }
    }

    private fun restartMonitoring() {
        val app = application as CompanionApp
        if (prefs.isPaired) {
            app.startWifiMonitor(prefs.vehicles)
        } else {
            app.stopWifiMonitor()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

private sealed class Screen {
    data object VehicleList : Screen()
    data object Pairing : Screen()
    data class Status(val vehicle: Vehicle) : Screen()
    data class ThemeBuilder(val vehicle: Vehicle) : Screen()
    data class WebConfig(val vehicle: Vehicle) : Screen()
}
