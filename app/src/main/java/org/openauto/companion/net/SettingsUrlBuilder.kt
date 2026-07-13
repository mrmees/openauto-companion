package org.openauto.companion.net

object SettingsUrlBuilder {
    const val FALLBACK_URL = "http://10.0.0.1:8080"
    private const val SETTINGS_PORT = 8080

    fun build(host: String?, port: Int?): String {
        val normalizedHost = host?.trim().orEmpty()
        if (normalizedHost.isBlank()) return FALLBACK_URL
        // Settings UI always uses the web-config port; pairing endpoint metadata is unrelated.
        @Suppress("UNUSED_VARIABLE")
        val ignoredPort = port
        return "http://$normalizedHost:$SETTINGS_PORT"
    }
}
