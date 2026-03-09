package org.openauto.companion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val source = BitmapFactory.decodeStream(stream)
                if (source != null) {
                    val cropped = centerCropBitmap(source, displayWidth, displayHeight)
                    source.recycle()
                    croppedBitmap = cropped

                    val baos = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    wallpaperBytes = baos.toByteArray()

                    val palette = Palette.from(cropped).maximumColorCount(16).generate()
                    val colors = listOfNotNull(
                        palette.getDominantColor(0).takeIf { it != 0 },
                        palette.getVibrantColor(0).takeIf { it != 0 },
                        palette.getMutedColor(0).takeIf { it != 0 },
                        palette.getDarkVibrantColor(0).takeIf { it != 0 },
                        palette.getLightVibrantColor(0).takeIf { it != 0 }
                    ).distinct().take(5)
                    extractedColors = colors
                    selectedSeed = colors.firstOrNull()
                }
            }
        }
    }

    // Regenerate theme when seed or name changes
    LaunchedEffect(selectedSeed, themeName) {
        val seed = selectedSeed
        if (seed != null) {
            generatedTheme = ThemeGenerator.generateScheme(seed, themeName)
        }
    }

    // Reset sending state when result comes in
    LaunchedEffect(transferResult) {
        if (transferResult != null) {
            isSending = false
        }
    }

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
            modifier = Modifier.padding(start = 48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Wallpaper picker
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
                contentDescription = "Wallpaper preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(displayWidth.toFloat() / displayHeight.toFloat())
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit
            )
        }

        // 4. Seed color bar
        if (extractedColors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Seed Color", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                extractedColors.forEach { colorArgb ->
                    val isSelected = colorArgb == selectedSeed
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(colorArgb))
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                }
                            )
                            .clickable { selectedSeed = colorArgb }
                    )
                }
            }
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

        // 6. Theme name
        if (croppedBitmap != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = themeName,
                onValueChange = { themeName = it },
                label = { Text("Theme Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 7. Send button
        if (croppedBitmap != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val theme = generatedTheme
                    val bytes = wallpaperBytes
                    if (theme != null && bytes != null) {
                        isSending = true
                        onSendTheme(theme, bytes)
                    }
                },
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
        columns = GridCells.Fixed(6),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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

private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
    val sourceRatio = source.width.toFloat() / source.height.toFloat()
    val (cropWidth, cropHeight) = if (sourceRatio > targetRatio) {
        val h = source.height
        val w = (h * targetRatio).toInt()
        w to h
    } else {
        val w = source.width
        val h = (w / targetRatio).toInt()
        w to h
    }
    val x = (source.width - cropWidth) / 2
    val y = (source.height - cropHeight) / 2
    val cropped = Bitmap.createBitmap(source, x, y, cropWidth, cropHeight)
    val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    if (cropped != source) cropped.recycle()
    return scaled
}
