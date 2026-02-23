package org.openauto.companion.data

import android.content.Context
import android.content.SharedPreferences

class CompanionPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    var sharedSecret: String
        get() = prefs.getString("shared_secret", "") ?: ""
        set(value) = prefs.edit().putString("shared_secret", value).apply()

    var targetSsid: String
        get() = prefs.getString("target_ssid", "OpenAutoProdigy") ?: "OpenAutoProdigy"
        set(value) = prefs.edit().putString("target_ssid", value).apply()

    var socks5Enabled: Boolean
        get() = prefs.getBoolean("socks5_enabled", true)
        set(value) = prefs.edit().putBoolean("socks5_enabled", value).apply()

    val isPaired: Boolean get() = sharedSecret.isNotEmpty()
}
