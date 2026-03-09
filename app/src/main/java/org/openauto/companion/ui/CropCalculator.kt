package org.openauto.companion.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CropCalculator {

    data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

    fun initialScale(
        imageWidth: Int, imageHeight: Int,
        viewWidth: Float, viewHeight: Float
    ): Float {
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        return max(scaleX, scaleY)
    }

    fun clampScale(scale: Float, minScale: Float, maxScale: Float): Float =
        scale.coerceIn(minScale, maxScale)

    fun clampOffset(
        offsetX: Float, offsetY: Float,
        scale: Float,
        imageWidth: Int, imageHeight: Int,
        viewWidth: Float, viewHeight: Float
    ): Pair<Float, Float> {
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        val maxOffsetX = max(0f, (scaledW - viewWidth) / 2f)
        val maxOffsetY = max(0f, (scaledH - viewHeight) / 2f)
        return offsetX.coerceIn(-maxOffsetX, maxOffsetX) to
                offsetY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    fun computeCropRect(
        offsetX: Float, offsetY: Float,
        scale: Float,
        imageWidth: Int, imageHeight: Int,
        viewWidth: Float, viewHeight: Float
    ): CropRect {
        val visibleWidthInImage = viewWidth / scale
        val visibleHeightInImage = viewHeight / scale

        val centerXInImage = imageWidth / 2f - offsetX / scale
        val centerYInImage = imageHeight / 2f - offsetY / scale

        var cropX = (centerXInImage - visibleWidthInImage / 2f).roundToInt()
        var cropY = (centerYInImage - visibleHeightInImage / 2f).roundToInt()
        var cropW = visibleWidthInImage.roundToInt()
        var cropH = visibleHeightInImage.roundToInt()

        cropX = cropX.coerceIn(0, max(0, imageWidth - cropW))
        cropY = cropY.coerceIn(0, max(0, imageHeight - cropH))
        cropW = min(cropW, imageWidth - cropX)
        cropH = min(cropH, imageHeight - cropY)

        return CropRect(cropX, cropY, cropW, cropH)
    }
}
