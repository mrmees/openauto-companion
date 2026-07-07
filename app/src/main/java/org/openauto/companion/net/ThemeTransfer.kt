package org.openauto.companion.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object ThemeTransfer {
    private const val DEFAULT_HOST = "10.0.0.1"
    private const val WEB_CONFIG_PORT = 8080
    private const val INSTALL_PATH = "/api/theme/install"
    private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    private val defaultClient = OkHttpClient()

    sealed class TransferResult {
        data object Success : TransferResult()
        data class Failed(val reason: String) : TransferResult()
    }

    fun buildInstallUrl(host: String?): HttpUrl {
        val normalizedHost = host?.trim().orEmpty().ifBlank { DEFAULT_HOST }
        return "http://$normalizedHost:$WEB_CONFIG_PORT$INSTALL_PATH".toHttpUrl()
    }

    fun send(
        host: String?,
        themeJson: JSONObject,
        wallpaperBytes: ByteArray?,
        client: OkHttpClient = defaultClient
    ): TransferResult =
        sendToUrl(
            url = buildInstallUrl(host),
            themeJson = themeJson,
            wallpaperBytes = wallpaperBytes,
            client = client
        )

    internal fun sendToUrl(
        url: HttpUrl,
        themeJson: JSONObject,
        wallpaperBytes: ByteArray?,
        client: OkHttpClient = defaultClient
    ): TransferResult {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("manifest", themeJson.toString())
            .apply {
                if (wallpaperBytes != null && wallpaperBytes.isNotEmpty()) {
                    addFormDataPart(
                        "wallpaper",
                        "wallpaper.jpg",
                        wallpaperBytes.toRequestBody(JPEG_MEDIA_TYPE)
                    )
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                parseResponse(statusCode = response.code, body = response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            TransferResult.Failed(e.message ?: "Theme install request failed")
        }
    }

    private fun parseResponse(statusCode: Int, body: String): TransferResult {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            if (statusCode !in 200..299) {
                return TransferResult.Failed(defaultFailureReason(statusCode))
            }
            return TransferResult.Failed("Invalid response: ${e.message}")
        }

        if (statusCode == 200 && json.optBoolean("installed", false)) {
            return TransferResult.Success
        }

        val serverError = json.optString("error", "").trim()
        return TransferResult.Failed(
            serverError.ifBlank { defaultFailureReason(statusCode) }
        )
    }

    private fun defaultFailureReason(statusCode: Int): String =
        when (statusCode) {
            400 -> "Theme payload was rejected"
            413 -> "payload too large"
            500 -> "Head unit theme import failed"
            503 -> "Qt app not running"
            200 -> "Theme install failed"
            else -> "Theme install failed with HTTP $statusCode"
        }
}
