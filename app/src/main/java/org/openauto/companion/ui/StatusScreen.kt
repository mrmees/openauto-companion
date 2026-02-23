package org.openauto.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class CompanionStatus(
    val connected: Boolean = false,
    val sharingTime: Boolean = false,
    val sharingGps: Boolean = false,
    val sharingBattery: Boolean = false,
    val socks5Active: Boolean = false,
    val ssid: String = ""
)

@Composable
fun StatusScreen(
    vehicleName: String,
    status: CompanionStatus,
    socks5Enabled: Boolean,
    onSocks5Toggle: (Boolean) -> Unit,
    onUnpair: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(vehicleName, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Connection status
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = if (status.connected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color,
                    modifier = Modifier.size(12.dp)
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (status.connected) "Connected" else "Waiting for ${status.ssid}...")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sharing status
        Text("Sharing", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        StatusRow("Time", status.sharingTime && status.connected)
        StatusRow("GPS Location", status.sharingGps && status.connected)
        StatusRow("Battery Level", status.sharingBattery && status.connected)
        StatusRow("Internet (SOCKS5)", status.socks5Active && status.connected)

        Spacer(modifier = Modifier.height(24.dp))

        // Settings
        Text("Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Internet Sharing")
            Switch(checked = socks5Enabled, onCheckedChange = onSocks5Toggle)
        }

        Spacer(modifier = Modifier.height(48.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onUnpair, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Unpair", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            if (active) "Active" else "\u2014",
            color = if (active) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
        )
    }
}
