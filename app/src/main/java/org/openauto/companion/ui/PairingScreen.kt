package org.openauto.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(
    suggestedSsid: String = "",
    onPaired: (ssid: String, name: String, pin: String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var ssid by remember { mutableStateOf(suggestedSsid) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Vehicle Name (optional)") },
            placeholder = { Text("e.g. Miata") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(200.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
            Button(
                onClick = { onPaired(ssid.trim(), name.trim().ifEmpty { ssid.trim() }, pin) },
                enabled = pin.length == 6 && ssid.trim().isNotEmpty()
            ) {
                Text("Pair")
            }
        }
    }
}
