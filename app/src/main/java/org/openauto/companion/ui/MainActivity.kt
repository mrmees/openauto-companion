package org.openauto.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openauto.companion.CompanionApp
import org.openauto.companion.data.CompanionPrefs
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
                var isPaired by remember { mutableStateOf(prefs.isPaired) }

                if (!isPaired) {
                    PairingScreen(onPaired = { pin ->
                        deriveAndStoreSecret(pin)
                        isPaired = true
                        startMonitoringIfPaired()
                    })
                } else {
                    val isConnected by CompanionService.connected.collectAsStateWithLifecycle()
                    val status = CompanionStatus(
                        connected = isConnected,
                        sharingTime = true,
                        sharingGps = true,
                        sharingBattery = true,
                        socks5Active = false,
                        ssid = prefs.targetSsid
                    )
                    var socks5Enabled by remember { mutableStateOf(prefs.socks5Enabled) }

                    StatusScreen(
                        status = status,
                        socks5Enabled = socks5Enabled,
                        onSocks5Toggle = {
                            socks5Enabled = it
                            prefs.socks5Enabled = it
                        },
                        onUnpair = {
                            prefs.sharedSecret = ""
                            isPaired = false
                        }
                    )
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

    private fun deriveAndStoreSecret(pin: String) {
        val material = "$pin:openauto-companion-v1"
        val digest = MessageDigest.getInstance("SHA-256")
        val secret = digest.digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
        prefs.sharedSecret = secret
    }

    private fun startMonitoringIfPaired() {
        if (prefs.isPaired) {
            (application as CompanionApp).startWifiMonitor(prefs.sharedSecret, prefs.targetSsid)
        }
    }
}
