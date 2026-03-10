package org.openauto.companion.net

import android.util.Log
import org.json.JSONObject
import java.util.Base64

object ThemeTransfer {
    private const val TAG = "ThemeTransfer"
    private const val CHUNK_SIZE = 65536 // 64KB per chunk

    fun chunkBytes(data: ByteArray, chunkSize: Int = CHUNK_SIZE): List<String> {
        if (data.isEmpty()) return emptyList()
        val encoder = Base64.getEncoder()
        return (0 until data.size step chunkSize).map { offset ->
            val end = minOf(offset + chunkSize, data.size)
            encoder.encodeToString(data.copyOfRange(offset, end))
        }
    }

    sealed class TransferResult {
        data object Success : TransferResult()
        data class Failed(val reason: String) : TransferResult()
    }

    fun send(
        connection: PiConnection,
        themeJson: JSONObject,
        wallpaperBytes: ByteArray,
        readResponse: () -> String?
    ): TransferResult {
        // 1. Validate
        val sessionKey = connection.sessionKey
            ?: return TransferResult.Failed("No session key").also { Log.w(TAG, "send() failed: no session key") }
        if (!connection.isConnected()) {
            return TransferResult.Failed("Not connected").also { Log.w(TAG, "send() failed: not connected") }
        }
        if (wallpaperBytes.isEmpty()) {
            return TransferResult.Failed("Wallpaper data is empty").also { Log.w(TAG, "send() failed: empty wallpaper") }
        }

        Log.i(TAG, "Starting theme transfer: wallpaper=${wallpaperBytes.size} bytes, theme name='${themeJson.optString("name")}'")

        // 2. Chunk wallpaper
        val chunks = chunkBytes(wallpaperBytes)
        Log.d(TAG, "Wallpaper split into ${chunks.size} chunks (${CHUNK_SIZE} bytes each)")

        // 3. Send theme message
        val themeMessage = Protocol.buildThemeMessage(
            sessionKey = sessionKey,
            themeJson = themeJson,
            wallpaperFormat = "jpeg",
            wallpaperSize = wallpaperBytes.size,
            wallpaperChunks = chunks.size
        )
        connection.sendStatus(themeMessage)
        Log.d(TAG, "Sent theme message (${themeMessage.toString().length} chars)")

        // 4. Send each chunk
        chunks.forEachIndexed { index, base64Data ->
            val chunkMessage = Protocol.buildThemeDataChunk(
                sessionKey = sessionKey,
                chunkIndex = index,
                base64Data = base64Data
            )
            connection.sendStatus(chunkMessage)
            Log.d(TAG, "Sent chunk ${index + 1}/${chunks.size} (${base64Data.length} base64 chars)")
        }

        Log.d(TAG, "All chunks sent, waiting for theme_ack...")

        // 5. Read response
        val response = readResponse()
            ?: return TransferResult.Failed("No response from head unit").also { Log.w(TAG, "No theme_ack received (null response)") }

        Log.d(TAG, "Received response: $response")

        // 6. Parse and check ack
        return try {
            val json = JSONObject(response)
            if (json.optString("type") == "theme_ack" && json.optBoolean("accepted", false)) {
                Log.i(TAG, "Theme accepted by head unit")
                TransferResult.Success
            } else {
                val reason = json.optString("reason", "Theme not accepted")
                Log.w(TAG, "Theme rejected: $reason (response: $response)")
                TransferResult.Failed(reason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse theme_ack response: $response", e)
            TransferResult.Failed("Invalid response: ${e.message}")
        }
    }
}
