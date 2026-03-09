package org.openauto.companion.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val TAG = "WallpaperCrop"

@Composable
fun WallpaperCropScreen(
    sourceBitmap: Bitmap,
    targetAspectRatio: Float,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    // Compute min scale once we know the view size
    val minScale = remember(viewSize, sourceBitmap) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            CropCalculator.initialScale(
                sourceBitmap.width, sourceBitmap.height,
                viewSize.width.toFloat(), viewSize.height.toFloat()
            )
        } else 1f
    }
    val maxScale = 5f

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset scale to minScale when view size is first known
    LaunchedEffect(minScale) {
        if (minScale > 0f) {
            scale = minScale
            offsetX = 0f
            offsetY = 0f
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = CropCalculator.clampScale(scale * zoomChange, minScale, maxScale)
        val newOffsetX = offsetX + panChange.x
        val newOffsetY = offsetY + panChange.y
        val (clampedX, clampedY) = CropCalculator.clampOffset(
            newOffsetX, newOffsetY, newScale,
            sourceBitmap.width, sourceBitmap.height,
            viewSize.width.toFloat(), viewSize.height.toFloat()
        )
        scale = newScale
        offsetX = clampedX
        offsetY = clampedY
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Crop area — aspect-ratio-locked box centered in screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp) // Leave room for button bar
                .onSizeChanged { size ->
                    // Compute the largest rect with target aspect ratio that fits
                    val containerRatio = size.width.toFloat() / size.height.toFloat()
                    viewSize = if (containerRatio > targetAspectRatio) {
                        // Container is wider — height-limited
                        val h = size.height
                        val w = (h * targetAspectRatio).toInt()
                        IntSize(w, h)
                    } else {
                        // Container is taller — width-limited
                        val w = size.width
                        val h = (w / targetAspectRatio).toInt()
                        IntSize(w, h)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (viewSize.width > 0) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(LocalDensity.current) { viewSize.width.toDp() },
                            height = with(LocalDensity.current) { viewSize.height.toDp() }
                        )
                        .transformable(state = transformState)
                ) {
                    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Crop preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale / minScale
                                scaleY = scale / minScale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Button bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.White)
            }

            TextButton(
                onClick = {
                    coroutineScope.launch {
                        launch {
                            animate(scale, minScale) { value, _ -> scale = value }
                        }
                        launch {
                            animate(offsetX, 0f) { value, _ -> offsetX = value }
                        }
                        launch {
                            animate(offsetY, 0f) { value, _ -> offsetY = value }
                        }
                    }
                }
            ) {
                Text("Reset", color = Color.White)
            }

            TextButton(
                onClick = {
                    try {
                        val rect = CropCalculator.computeCropRect(
                            offsetX, offsetY, scale,
                            sourceBitmap.width, sourceBitmap.height,
                            viewSize.width.toFloat(), viewSize.height.toFloat()
                        )
                        if (rect.width <= 0 || rect.height <= 0) {
                            Log.w(TAG, "Invalid crop rect: $rect")
                            onCancel()
                            return@TextButton
                        }
                        val cropped = Bitmap.createBitmap(
                            sourceBitmap, rect.x, rect.y, rect.width, rect.height
                        )
                        onConfirm(cropped)
                    } catch (e: Exception) {
                        Log.e(TAG, "Crop failed", e)
                        onCancel()
                    }
                }
            ) {
                Text("Confirm", color = Color.White)
            }
        }
    }
}
