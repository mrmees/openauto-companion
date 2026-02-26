package org.openauto.companion.net

object SettingsUrlBuilder {
    const val FALLBACK_URL = "http://10.0.0.1:8080"

    fun build(host: String?, port: Int?): String {
        val normalizedHost = host?.trim().orEmpty()
        if (normalizedHost.isBlank() || port == null) return FALLBACK_URL
        return "http://$normalizedHost:$port"
    }
}
