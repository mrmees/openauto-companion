package org.openauto.companion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import org.json.JSONObject
import org.openauto.companion.theme.ThemeGenerator
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "ThemeBuilder"

private fun decodeSampledBitmap(
    context: android.content.Context,
    uri: Uri,
    maxWidth: Int,
    maxHeight: Int
): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

    var sampleSize = 1
    val w = options.outWidth
    val h = options.outHeight
    if (w > maxWidth || h > maxHeight) {
        val halfW = w / 2
        val halfH = h / 2
        while ((halfW / sampleSize) >= maxWidth && (halfH / sampleSize) >= maxHeight) {
            sampleSize *= 2
        }
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
    }
}

@Composable
fun ThemeBuilderScreen(
    displayWidth: Int,
    displayHeight: Int,
    onSendTheme: (JSONObject, ByteArray) -> Unit,
    transferResult: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var wallpaperBytes by remember { mutableStateOf<ByteArray?>(null) }
    var extractedColors by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedSeed by remember { mutableStateOf<Int?>(null) }
    var themeName by remember {
        mutableStateOf(
            "Theme from ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
        )
    }
    var generatedTheme by remember { mutableStateOf<JSONObject?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCropScreen by remember { mutableStateOf(false) }
    var customSeedColor by remember { mutableStateOf<Int?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d(TAG, "Image picked: $uri")
            val maxDim = maxOf(displayWidth, displayHeight) * 2
            val source = decodeSampledBitmap(context, uri, maxDim, maxDim)
            if (source != null) {
                Log.d(TAG, "Source bitmap: ${source.width}x${source.height} (downsampled for max ${maxDim})")
                sourceBitmap?.recycle()
                sourceBitmap = source
                showCropScreen = true
            } else {
                Log.w(TAG, "Failed to decode image from $uri")
            }
        }
    }

    val handleCropResult: (Bitmap) -> Unit = { cropped ->
        Log.d(TAG, "Crop confirmed: ${cropped.width}x${cropped.height}")
        val scaled = Bitmap.createScaledBitmap(cropped, displayWidth, displayHeight, true)
        if (cropped != scaled) cropped.recycle()
        croppedBitmap = scaled

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        wallpaperBytes = baos.toByteArray()
        Log.d(TAG, "JPEG wallpaper: ${wallpaperBytes!!.size} bytes")

        val palette = Palette.from(scaled).maximumColorCount(16).generate()
        val colors = listOfNotNull(
            palette.getDominantColor(0).takeIf { it != 0 },
            palette.getVibrantColor(0).takeIf { it != 0 },
            palette.getMutedColor(0).takeIf { it != 0 },
            palette.getDarkVibrantColor(0).takeIf { it != 0 },
            palette.getLightVibrantColor(0).takeIf { it != 0 }
        ).distinct().take(5)
        extractedColors = colors
        selectedSeed = colors.firstOrNull()
        Log.d(TAG, "Extracted ${colors.size} colors, seed: ${colors.firstOrNull()?.let { "#%06X".format(it and 0xFFFFFF) } ?: "none"}")

        showCropScreen = false
    }

    DisposableEffect(Unit) {
        onDispose {
            sourceBitmap?.recycle()
        }
    }

    // Regenerate theme when seed or name changes
    LaunchedEffect(selectedSeed, themeName) {
        val seed = selectedSeed
        if (seed != null) {
            Log.d(TAG, "Generating M3 scheme: seed=#%06X, name='$themeName'".format(seed and 0xFFFFFF))
            generatedTheme = ThemeGenerator.generateScheme(seed, themeName)
            Log.d(TAG, "Theme generated: ${generatedTheme?.getJSONObject("light")?.length()} light roles, ${generatedTheme?.getJSONObject("dark")?.length()} dark roles")
        }
    }

    // Reset sending state when result comes in
    LaunchedEffect(transferResult) {
        if (transferResult != null) {
            Log.i(TAG, "Transfer result: $transferResult")
            isSending = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Theme Builder", style = MaterialTheme.typography.headlineMedium)
            }

            // 2. Target resolution
            Text(
                "Target: ${displayWidth}x${displayHeight}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Wallpaper picker
            Button(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose Wallpaper")
            }

            croppedBitmap?.let { bitmap ->
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Wallpaper preview — tap to adjust crop",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(displayWidth.toFloat() / displayHeight.toFloat())
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            if (sourceBitmap != null) {
                                showCropScreen = true
                            }
                        },
                    contentScale = ContentScale.Fit
                )
                if (sourceBitmap != null) {
                    Text(
                        "Tap image to adjust crop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 4. Seed color
            if (extractedColors.isNotEmpty() || croppedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Seed Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    extractedColors.forEach { colorArgb ->
                        val isSelected = colorArgb == selectedSeed && customSeedColor == null
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(colorArgb))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    }
                                )
                                .clickable {
                                    customSeedColor = null
                                    selectedSeed = colorArgb
                                }
                        )
                    }
                    // Custom color circle — shows custom color or "+" icon
                    val isCustomSelected = customSeedColor != null
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (customSeedColor != null) Color(customSeedColor!!)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (isCustomSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                }
                            )
                            .clickable { showColorPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (customSeedColor == null) {
                            Text("+", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Color picker dialog
            if (showColorPicker) {
                ColorPickerDialog(
                    initialColor = customSeedColor ?: selectedSeed ?: 0xFF6750A4.toInt(),
                    onColorSelected = { color ->
                        customSeedColor = color
                        selectedSeed = color
                        showColorPicker = false
                    },
                    onDismiss = { showColorPicker = false }
                )
            }

            // 5. Palette preview
            generatedTheme?.let { theme ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Light Palette", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                PalettePreview(theme.getJSONObject("light"))

                Spacer(modifier = Modifier.height(12.dp))
                Text("Dark Palette", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                PalettePreview(theme.getJSONObject("dark"))
            }

            // 6. Send button
            if (croppedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSendDialog = true },
                    enabled = generatedTheme != null && wallpaperBytes != null && !isSending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending...")
                    } else {
                        Text("Send to Head Unit")
                    }
                }
            }

            // Send confirmation dialog with theme name
            if (showSendDialog) {
                AlertDialog(
                    onDismissRequest = { showSendDialog = false },
                    title = { Text("Send Theme") },
                    text = {
                        OutlinedTextField(
                            value = themeName,
                            onValueChange = { themeName = it },
                            label = { Text("Theme Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSendDialog = false
                                val theme = generatedTheme
                                val bytes = wallpaperBytes
                                if (theme != null && bytes != null) {
                                    Log.i(TAG, "Sending theme '${themeName}' with ${bytes.size} byte wallpaper")
                                    isSending = true
                                    onSendTheme(theme, bytes)
                                }
                            }
                        ) { Text("Send") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSendDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // 8. Result display
            transferResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                val isSuccess = result.contains("success", ignoreCase = true) ||
                    result.contains("ok", ignoreCase = true)
                Text(
                    text = result,
                    color = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Crop overlay
        if (showCropScreen && sourceBitmap != null) {
            WallpaperCropScreen(
                sourceBitmap = sourceBitmap!!,
                targetAspectRatio = displayWidth.toFloat() / displayHeight.toFloat(),
                onConfirm = handleCropResult,
                onCancel = {
                    showCropScreen = false
                }
            )
        }
    }
}

private val PREVIEW_COLOR_ROLES = listOf(
    "primary", "onPrimary",
    "primaryContainer", "onPrimaryContainer",
    "secondary", "onSecondary",
    "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary",
    "surface", "onSurface"
)

@Composable
private fun PalettePreview(schemeJson: JSONObject) {
    val items = PREVIEW_COLOR_ROLES.mapNotNull { role ->
        val hex = schemeJson.optString(role, "")
        if (hex.isNotEmpty()) role to parseHexColor(hex) else null
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(12),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        userScrollEnabled = false
    ) {
        items(items) { (role, color) ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(color)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

private fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return try {
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (_: Exception) {
        Color.Gray
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val controller = com.github.skydoves.colorpicker.compose.rememberColorPickerController()
    var pickedColor by remember { mutableStateOf(initialColor) }
    var hexDisplay by remember { mutableStateOf("%06X".format(initialColor and 0xFFFFFF)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a Color") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.github.skydoves.colorpicker.compose.HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    controller = controller,
                    initialColor = Color(initialColor),
                    onColorChanged = { envelope ->
                        if (envelope.fromUser) {
                            pickedColor = envelope.color.toArgb()
                            hexDisplay = envelope.hexCode.takeLast(6)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Preview + hex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(pickedColor))
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "#$hexDisplay",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(pickedColor) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
