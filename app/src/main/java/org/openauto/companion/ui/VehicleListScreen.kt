package org.openauto.companion.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.openauto.companion.R
import org.openauto.companion.data.Vehicle

@Composable
fun VehicleListScreen(
    vehicles: List<Vehicle>,
    connectedSsid: String?,
    onVehicleTap: (Vehicle) -> Unit,
    onAddVehicle: () -> Unit,
    onRemoveVehicle: (Vehicle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        val context = LocalContext.current
        var tapTimes by remember { mutableStateOf(listOf<Long>()) }

        Spacer(modifier = Modifier.height(32.dp))
        Image(
            painter = painterResource(R.drawable.prodigy_logo),
            contentDescription = "Prodigy",
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.CenterHorizontally)
                .clickable {
                    val now = System.currentTimeMillis()
                    tapTimes = (tapTimes + now).filter { now - it < 2000 }
                    if (tapTimes.size >= 5) {
                        tapTimes = emptyList()
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=gbyvvc6n2Oo")))
                    }
                }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("OpenAuto Prodigy", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vehicles, key = { it.id }) { vehicle ->
                val isConnected = vehicle.ssid == connectedSsid
                VehicleRow(
                    vehicle = vehicle,
                    isConnected = isConnected,
                    onClick = { onVehicleTap(vehicle) },
                    onRemove = { onRemoveVehicle(vehicle) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddVehicle,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = vehicles.size < Vehicle.MAX_VEHICLES
        ) {
            Text("Add Vehicle")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun VehicleRow(
    vehicle: Vehicle,
    isConnected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection indicator
            val color = if (isConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = color,
                modifier = Modifier.size(12.dp)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                if (vehicle.name != vehicle.ssid) {
                    Text(vehicle.ssid, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove vehicle",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove ${vehicle.name}?") },
            text = { Text("This will unpair this vehicle. You can re-pair later with a new PIN.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onRemove() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
