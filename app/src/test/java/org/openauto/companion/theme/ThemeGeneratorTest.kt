package org.openauto.companion.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeGeneratorTest {
    @Test
    fun generateScheme_producesLightAndDark() {
        val seedArgb = 0xFF6750A4.toInt()
        val result = ThemeGenerator.generateScheme(seedArgb)
        assertNotNull(result)
        assertEquals(1, result.getInt("version"))
        assertTrue(result.has("seed"))
        assertTrue(result.has("light"))
        assertTrue(result.has("dark"))
    }

    @Test
    fun generateScheme_lightHasAllRoles() {
        val result = ThemeGenerator.generateScheme(0xFF1A237E.toInt())
        val light = result.getJSONObject("light")
        val expectedRoles = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
            "error", "onError", "errorContainer", "onErrorContainer",
            "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "outline", "outlineVariant",
            "inverseSurface", "inverseOnSurface", "inversePrimary",
            "surfaceDim", "surfaceBright",
            "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
            "surfaceContainerHigh", "surfaceContainerHighest"
        )
        for (role in expectedRoles) {
            assertTrue("Missing light role: $role", light.has(role))
            assertTrue("Role $role should be hex color", light.getString(role).startsWith("#"))
        }
    }

    @Test
    fun generateScheme_darkHasAllRoles() {
        val result = ThemeGenerator.generateScheme(0xFF1A237E.toInt())
        val dark = result.getJSONObject("dark")
        assertTrue(dark.has("primary"))
        assertTrue(dark.has("surface"))
        assertTrue(dark.has("surfaceContainerHighest"))
    }

    @Test
    fun generateScheme_differentSeedsProduceDifferentThemes() {
        val scheme1 = ThemeGenerator.generateScheme(0xFFFF0000.toInt())
        val scheme2 = ThemeGenerator.generateScheme(0xFF0000FF.toInt())
        assertNotEquals(
            scheme1.getJSONObject("light").getString("primary"),
            scheme2.getJSONObject("light").getString("primary")
        )
    }

    @Test
    fun generateScheme_includesName() {
        val result = ThemeGenerator.generateScheme(0xFF6750A4.toInt(), "My Theme")
        assertEquals("My Theme", result.getString("name"))
    }

    @Test
    fun argbToHex_formatsCorrectly() {
        assertEquals("#FF0000", ThemeGenerator.argbToHex(0xFFFF0000.toInt()))
        assertEquals("#00FF00", ThemeGenerator.argbToHex(0xFF00FF00.toInt()))
        assertEquals("#000000", ThemeGenerator.argbToHex(0xFF000000.toInt()))
    }
}
