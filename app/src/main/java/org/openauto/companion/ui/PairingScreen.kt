package org.openauto.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Pairing : PairingUiState
    data class Failed(val message: String) : PairingUiState
}

@Composable
fun PairingScreen(
    suggestedSsid: String = "",
    state: PairingUiState,
    onPair: (ssid: String, name: String, pin: String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var ssid by remember { mutableStateOf(suggestedSsid) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val pairing = state is PairingUiState.Pairing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add Vehicle", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the WiFi SSID and 6-digit PIN shown on your head unit's settings screen.")
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("WiFi SSID") },
            placeholder = { Text("e.g. OpenAutoProdigy-A3F2") },
            singleLine = true,
            enabled = !pairing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Vehicle Name (optional)") },
            placeholder = { Text("e.g. Miata") },
            singleLine = true,
            enabled = !pairing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !pairing,
            modifier = Modifier.width(200.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !pairing) {
                    Text("Cancel")
                }
            }
            Button(
                onClick = { onPair(ssid.trim(), name.trim().ifEmpty { ssid.trim() }, pin) },
                enabled = !pairing && pin.length == 6 && ssid.trim().isNotEmpty()
            ) {
                if (pairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pairing...")
                } else {
                    Text("Pair")
                }
            }
        }

        if (state is PairingUiState.Failed) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
