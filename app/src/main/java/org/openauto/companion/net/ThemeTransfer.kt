package org.openauto.companion.net

import org.json.JSONObject
import java.util.Base64

object ThemeTransfer {
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
            ?: return TransferResult.Failed("No session key")
        if (!connection.isConnected()) {
            return TransferResult.Failed("Not connected")
        }
        if (wallpaperBytes.isEmpty()) {
            return TransferResult.Failed("Wallpaper data is empty")
        }

        // 2. Chunk wallpaper
        val chunks = chunkBytes(wallpaperBytes)

        // 3. Send theme message
        val themeMessage = Protocol.buildThemeMessage(
            sessionKey = sessionKey,
            themeJson = themeJson,
            wallpaperFormat = "jpeg",
            wallpaperSize = wallpaperBytes.size,
            wallpaperChunks = chunks.size
        )
        connection.sendStatus(themeMessage)

        // 4. Send each chunk
        chunks.forEachIndexed { index, base64Data ->
            val chunkMessage = Protocol.buildThemeDataChunk(
                sessionKey = sessionKey,
                chunkIndex = index,
                base64Data = base64Data
            )
            connection.sendStatus(chunkMessage)
        }

        // 5. Read response
        val response = readResponse()
            ?: return TransferResult.Failed("No response from head unit")

        // 6. Parse and check ack
        return try {
            val json = JSONObject(response)
            if (json.optString("type") == "theme_ack" && json.optBoolean("accepted", false)) {
                TransferResult.Success
            } else {
                val reason = json.optString("reason", "Theme not accepted")
                TransferResult.Failed(reason)
            }
        } catch (e: Exception) {
            TransferResult.Failed("Invalid response: ${e.message}")
        }
    }
}
