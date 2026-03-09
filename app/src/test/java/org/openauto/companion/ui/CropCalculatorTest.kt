package org.openauto.companion.ui

import org.junit.Assert.*
import org.junit.Test

class CropCalculatorTest {

    // --- initialScale ---

    @Test
    fun `initialScale - landscape image wider than target fills height`() {
        val scale = CropCalculator.initialScale(
            imageWidth = 4000, imageHeight = 2000,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertEquals(0.3f, scale, 0.001f)
    }

    @Test
    fun `initialScale - tall image narrower than target fills width`() {
        val scale = CropCalculator.initialScale(
            imageWidth = 1000, imageHeight = 3000,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertEquals(1.024f, scale, 0.001f)
    }

    // --- clampOffset ---

    @Test
    fun `clampOffset - centered image returns zero offset`() {
        val (ox, oy) = CropCalculator.clampOffset(
            offsetX = 50f, offsetY = 50f,
            scale = 1f,
            imageWidth = 1024, imageHeight = 600,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertEquals(0f, ox, 0.5f)
        assertEquals(0f, oy, 0.5f)
    }

    @Test
    fun `clampOffset - zoomed in allows panning within bounds`() {
        val (ox, oy) = CropCalculator.clampOffset(
            offsetX = 600f, offsetY = -300f,
            scale = 1f,
            imageWidth = 2000, imageHeight = 1000,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertEquals(488f, ox, 0.5f)
        assertEquals(-200f, oy, 0.5f)
    }

    // --- computeCropRect ---

    @Test
    fun `computeCropRect - centered no-pan returns center region`() {
        val rect = CropCalculator.computeCropRect(
            offsetX = 0f, offsetY = 0f,
            scale = 0.3f,
            imageWidth = 4000, imageHeight = 2000,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertEquals(293, rect.x, 2)
        assertEquals(0, rect.y, 2)
        assertEquals(3413, rect.width, 2)
        assertEquals(2000, rect.height, 2)
    }

    @Test
    fun `computeCropRect - panned left shifts crop region`() {
        val rect = CropCalculator.computeCropRect(
            offsetX = 100f, offsetY = 0f,
            scale = 0.3f,
            imageWidth = 4000, imageHeight = 2000,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertTrue(rect.x >= 0)
        assertEquals(3413, rect.width, 2)
    }

    @Test
    fun `computeCropRect - result clamped to image bounds`() {
        val rect = CropCalculator.computeCropRect(
            offsetX = 0f, offsetY = 0f,
            scale = 0.3f,
            imageWidth = 4000, imageHeight = 2000,
            viewWidth = 1024f, viewHeight = 600f
        )
        assertTrue(rect.x >= 0)
        assertTrue(rect.y >= 0)
        assertTrue(rect.x + rect.width <= 4000)
        assertTrue(rect.y + rect.height <= 2000)
    }

    // --- clampScale ---

    @Test
    fun `clampScale - below minimum snaps to minimum`() {
        val minScale = CropCalculator.initialScale(4000, 2000, 1024f, 600f)
        val clamped = CropCalculator.clampScale(0.01f, minScale, 5f)
        assertEquals(minScale, clamped, 0.001f)
    }

    @Test
    fun `clampScale - above maximum snaps to maximum`() {
        val clamped = CropCalculator.clampScale(10f, 0.3f, 5f)
        assertEquals(5f, clamped, 0.001f)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue(
            "Expected $expected ± $tolerance but got $actual",
            kotlin.math.abs(expected - actual) <= tolerance
        )
    }
}
