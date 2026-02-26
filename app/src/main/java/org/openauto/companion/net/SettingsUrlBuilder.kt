package org.openauto.companion.net

object SettingsUrlBuilder {
    const val FALLBACK_URL = "http://10.0.0.1:8080"
    private const val SETTINGS_PORT = 8080

    fun build(host: String?, port: Int?): String {
        val normalizedHost = host?.trim().orEmpty()
        if (normalizedHost.isBlank()) return FALLBACK_URL
        // Settings UI is served on the web-config socket, not the companion socket.
        // Ignore stored companion pairing port values (e.g. 9876).
        @Suppress("UNUSED_VARIABLE")
        val ignoredPort = port
        return "http://$normalizedHost:$SETTINGS_PORT"
    }
}
