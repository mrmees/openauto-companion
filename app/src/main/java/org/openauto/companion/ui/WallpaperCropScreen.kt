package org.openauto.companion.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
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

    // fillScale = minimum to cover the view (no empty space)
    // fitScale = minimum to see the entire image (may have letterboxing)
    val fillScale = remember(viewSize, sourceBitmap) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            CropCalculator.initialScale(
                sourceBitmap.width, sourceBitmap.height,
                viewSize.width.toFloat(), viewSize.height.toFloat()
            )
        } else 1f
    }
    val fitScale = remember(viewSize, sourceBitmap) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            kotlin.math.min(
                viewSize.width.toFloat() / sourceBitmap.width,
                viewSize.height.toFloat() / sourceBitmap.height
            )
        } else 1f
    }
    val minScale = fitScale
    val maxScale = 5f

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Initialize to fill scale when view size is first known
    LaunchedEffect(fillScale) {
        if (fillScale > 0f) {
            scale = fillScale
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
                                scaleX = scale / fitScale
                                scaleY = scale / fitScale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit
                    )

                    // Crop frame overlay — dark scrim with clear cutout
                    val density = LocalDensity.current
                    Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height

                            // Crop rect: the target aspect ratio centered in view
                            val cropW: Float
                            val cropH: Float
                            if (canvasW / canvasH > targetAspectRatio) {
                                cropH = canvasH
                                cropW = canvasH * targetAspectRatio
                            } else {
                                cropW = canvasW
                                cropH = canvasW / targetAspectRatio
                            }
                            val cropLeft = (canvasW - cropW) / 2f
                            val cropTop = (canvasH - cropH) / 2f

                            // Draw scrim everywhere except the crop rect
                            val cropPath = Path().apply {
                                addRect(Rect(Offset(cropLeft, cropTop), Size(cropW, cropH)))
                            }
                            clipPath(cropPath, clipOp = ClipOp.Difference) {
                                drawRect(Color.Black.copy(alpha = 0.5f))
                            }

                            // Draw crop frame border
                            drawRect(
                                color = Color.White.copy(alpha = 0.7f),
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = with(density) { 1.5.dp.toPx() }
                                )
                            )
                        }
                }
            }
        }

        // Button bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .navigationBarsPadding()
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
                            animate(scale, fillScale) { value, _ -> scale = value }
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
