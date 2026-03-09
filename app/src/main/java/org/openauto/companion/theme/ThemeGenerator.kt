package org.openauto.companion.theme

import me.tatarka.google.material.dynamiccolor.DynamicScheme
import me.tatarka.google.material.hct.Hct
import me.tatarka.google.material.scheme.SchemeTonalSpot
import org.json.JSONObject

/**
 * Generates a full Material 3 color scheme from a seed color.
 *
 * Takes an ARGB seed color (typically extracted from a wallpaper) and produces
 * a JSON object with 34 color roles for both light and dark modes, ready to
 * send to the head unit.
 */
object ThemeGenerator {

    private val COLOR_ROLES: List<Pair<String, (DynamicScheme) -> Int>> = listOf(
        "primary" to DynamicScheme::getPrimary,
        "onPrimary" to DynamicScheme::getOnPrimary,
        "primaryContainer" to DynamicScheme::getPrimaryContainer,
        "onPrimaryContainer" to DynamicScheme::getOnPrimaryContainer,
        "secondary" to DynamicScheme::getSecondary,
        "onSecondary" to DynamicScheme::getOnSecondary,
        "secondaryContainer" to DynamicScheme::getSecondaryContainer,
        "onSecondaryContainer" to DynamicScheme::getOnSecondaryContainer,
        "tertiary" to DynamicScheme::getTertiary,
        "onTertiary" to DynamicScheme::getOnTertiary,
        "tertiaryContainer" to DynamicScheme::getTertiaryContainer,
        "onTertiaryContainer" to DynamicScheme::getOnTertiaryContainer,
        "error" to DynamicScheme::getError,
        "onError" to DynamicScheme::getOnError,
        "errorContainer" to DynamicScheme::getErrorContainer,
        "onErrorContainer" to DynamicScheme::getOnErrorContainer,
        "background" to DynamicScheme::getBackground,
        "onBackground" to DynamicScheme::getOnBackground,
        "surface" to DynamicScheme::getSurface,
        "onSurface" to DynamicScheme::getOnSurface,
        "surfaceVariant" to DynamicScheme::getSurfaceVariant,
        "onSurfaceVariant" to DynamicScheme::getOnSurfaceVariant,
        "outline" to DynamicScheme::getOutline,
        "outlineVariant" to DynamicScheme::getOutlineVariant,
        "inverseSurface" to DynamicScheme::getInverseSurface,
        "inverseOnSurface" to DynamicScheme::getInverseOnSurface,
        "inversePrimary" to DynamicScheme::getInversePrimary,
        "surfaceDim" to DynamicScheme::getSurfaceDim,
        "surfaceBright" to DynamicScheme::getSurfaceBright,
        "surfaceContainerLowest" to DynamicScheme::getSurfaceContainerLowest,
        "surfaceContainerLow" to DynamicScheme::getSurfaceContainerLow,
        "surfaceContainer" to DynamicScheme::getSurfaceContainer,
        "surfaceContainerHigh" to DynamicScheme::getSurfaceContainerHigh,
        "surfaceContainerHighest" to DynamicScheme::getSurfaceContainerHighest,
    )

    /**
     * Generate a complete M3 color scheme JSON from a seed ARGB color.
     *
     * @param seedArgb The seed color as an ARGB integer (e.g. 0xFF6750A4.toInt())
     * @param name Optional theme name to include in the output
     * @return JSONObject with version, seed, and light/dark color role maps
     */
    fun generateScheme(seedArgb: Int, name: String? = null): JSONObject {
        val hct = Hct.fromInt(seedArgb)
        val contrastLevel = 0.0

        val lightScheme = SchemeTonalSpot(hct, false, contrastLevel)
        val darkScheme = SchemeTonalSpot(hct, true, contrastLevel)

        return JSONObject().apply {
            put("version", 1)
            put("seed", argbToHex(seedArgb))
            if (name != null) {
                put("name", name)
            }
            put("light", extractColors(lightScheme))
            put("dark", extractColors(darkScheme))
        }
    }

    /**
     * Convert an ARGB int to a #RRGGBB hex string (alpha channel stripped).
     */
    fun argbToHex(argb: Int): String {
        return String.format("#%06X", argb and 0xFFFFFF)
    }

    private fun extractColors(scheme: DynamicScheme): JSONObject {
        return JSONObject().apply {
            for ((roleName, getter) in COLOR_ROLES) {
                put(roleName, argbToHex(getter(scheme)))
            }
        }
    }
}
