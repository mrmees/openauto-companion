package org.openauto.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openauto.companion.net.api.PairingCode

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Pairing : PairingUiState
    data class Failed(val message: String) : PairingUiState
}

internal fun canSubmitManualPairing(
    ssid: String,
    pairingCodeInput: String,
    pairingInProgress: Boolean
): Boolean = !pairingInProgress &&
    ssid.trim().isNotEmpty() &&
    PairingCode.normalize(pairingCodeInput) != null

@Composable
fun PairingScreen(
    suggestedSsid: String = "",
    state: PairingUiState,
    onPair: (ssid: String, name: String, pairingCode: String) -> Unit,
    onScanQr: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var ssid by remember { mutableStateOf(suggestedSsid) }
    var name by remember { mutableStateOf("") }
    var pairingCodeInput by remember { mutableStateOf("") }
    val pairing = state is PairingUiState.Pairing
    val canonicalPairingCode = PairingCode.normalize(pairingCodeInput)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add Vehicle", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Scan the QR code on your head unit, or enter its WiFi details and pairing code manually.")
        Spacer(modifier = Modifier.height(20.dp))

        FilledTonalButton(
            onClick = onScanQr,
            enabled = !pairing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan QR")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Manual pairing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

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
            value = pairingCodeInput,
            onValueChange = { input ->
                PairingCode.formatInput(input)?.let { pairingCodeInput = it }
            },
            label = { Text("Pairing Code") },
            placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii
            ),
            singleLine = true,
            enabled = !pairing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !pairing) {
                    Text("Cancel")
                }
            }
            Button(
                onClick = {
                    onPair(
                        ssid.trim(),
                        name.trim().ifEmpty { ssid.trim() },
                        checkNotNull(canonicalPairingCode)
                    )
                },
                enabled = canSubmitManualPairing(ssid, pairingCodeInput, pairing)
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
