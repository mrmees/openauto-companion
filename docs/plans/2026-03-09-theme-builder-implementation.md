# Theme Builder & Wallpaper Transfer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Native Material You theme builder that extracts colors from a wallpaper, generates a full M3 palette, and transfers the theme + wallpaper image to the head unit over the existing authenticated socket.

**Architecture:** Wallpaper-first flow — user picks image, crops to head unit resolution, app extracts dominant colors as seed candidates, generates M3 light+dark schemes, user confirms and sends. Transfer uses existing PiConnection with new `theme`/`theme_data`/`theme_ack` message types. Display resolution learned from extended `hello_ack`.

**Tech Stack:** Kotlin, Jetpack Compose, material-color-utilities (Google's M3 color algorithm), androidx.palette (dominant color extraction), image cropping library, existing JSON-over-TCP protocol with HMAC-SHA256 auth.

---

### Task 1: Add New Dependencies

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Add material-color-utilities, palette, and cropper dependencies**

In the `dependencies` block of `app/build.gradle.kts`, add:

```kotlin
// Theme builder
implementation("com.google.material:material-color-utilities:0.12.0")
implementation("androidx.palette:palette-ktx:1.0.0")
implementation("com.vanniktech:android-image-cropper:4.6.0")
```

**Step 2: Sync and verify build**

Run: `cd /home/matt/claude/personal/openautopro/companion-app && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add material-color-utilities, palette, and image cropper deps"
```

---

### Task 2: Extend Vehicle Model with Display Resolution

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/data/Vehicle.kt`
- Modify: `app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt`

**Step 1: Write the failing tests**

Add to `VehicleSerializationTest.kt`:

```kotlin
@Test
fun roundTrip_displayResolution() {
    val v = Vehicle(id = "disp1", ssid = "TestAP", sharedSecret = "s",
        displayWidth = 1024, displayHeight = 600)
    val result = Vehicle.listFromJson(Vehicle.listToJson(listOf(v)))
    assertEquals(1024, result[0].displayWidth)
    assertEquals(600, result[0].displayHeight)
}

@Test
fun fromJson_displayResolutionDefaultsWhenMissing() {
    val json = org.json.JSONObject().apply {
        put("ssid", "OldHU")
        put("shared_secret", "abc")
    }
    val v = Vehicle.fromJson(json)
    assertNull(v.displayWidth)
    assertNull(v.displayHeight)
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest" --info 2>&1 | tail -20`
Expected: FAIL — `displayWidth` property doesn't exist

**Step 3: Add display fields to Vehicle**

In `Vehicle.kt`, add two nullable fields to the data class:

```kotlin
data class Vehicle(
    val id: String = UUID.randomUUID().toString().take(8),
    val ssid: String,
    val name: String = ssid,
    val sharedSecret: String,
    val socks5Enabled: Boolean = true,
    val audioKeepAlive: Boolean = false,
    val settingsHost: String? = null,
    val settingsPort: Int? = null,
    val displayWidth: Int? = null,
    val displayHeight: Int? = null
)
```

In `toJson()`, add:

```kotlin
if (displayWidth != null) put("display_width", displayWidth)
if (displayHeight != null) put("display_height", displayHeight)
```

In `fromJson()`, add:

```kotlin
displayWidth = if (json.has("display_width")) json.optInt("display_width") else null,
displayHeight = if (json.has("display_height")) json.optInt("display_height") else null
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest" --info 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/data/Vehicle.kt \
       app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt
git commit -m "feat: add display resolution fields to Vehicle model"
```

---

### Task 3: Parse Display Resolution from hello_ack

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/net/PiConnection.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt`

**Step 1: Write failing tests**

Create or update `PiConnectionParsingTest.kt`. Since `PiConnection.connect()` does the parsing inline and the parsing logic isn't factored out, we need to extract it. Add a new internal parsing function and test it.

Add a new file-level function in `PiConnection.kt`:

```kotlin
internal fun parseDisplayFromAck(ack: JSONObject): Pair<Int, Int>? {
    val display = ack.optJSONObject("display") ?: return null
    val width = display.optInt("width", 0)
    val height = display.optInt("height", 0)
    if (width <= 0 || height <= 0) return null
    return width to height
}
```

Write tests in `PiConnectionParsingTest.kt`:

```kotlin
package org.openauto.companion.net

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PiConnectionParsingTest {
    @Test
    fun parseDisplayFromAck_validDisplay() {
        val ack = JSONObject("""{"type":"hello_ack","accepted":true,"session_key":"aa","display":{"width":1024,"height":600}}""")
        val result = parseDisplayFromAck(ack)
        assertNotNull(result)
        assertEquals(1024, result!!.first)
        assertEquals(600, result.second)
    }

    @Test
    fun parseDisplayFromAck_missingDisplay() {
        val ack = JSONObject("""{"type":"hello_ack","accepted":true,"session_key":"aa"}""")
        assertNull(parseDisplayFromAck(ack))
    }

    @Test
    fun parseDisplayFromAck_zeroValues() {
        val ack = JSONObject("""{"type":"hello_ack","accepted":true,"session_key":"aa","display":{"width":0,"height":0}}""")
        assertNull(parseDisplayFromAck(ack))
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PiConnectionParsingTest" --info 2>&1 | tail -20`
Expected: FAIL — function doesn't exist yet

**Step 3: Implement parseDisplayFromAck**

Add to `PiConnection.kt` (file-level, after the existing `shouldFallbackToUnboundSocket` function):

```kotlin
internal fun parseDisplayFromAck(ack: JSONObject): Pair<Int, Int>? {
    val display = ack.optJSONObject("display") ?: return null
    val width = display.optInt("width", 0)
    val height = display.optInt("height", 0)
    if (width <= 0 || height <= 0) return null
    return width to height
}
```

Add import for `JSONObject` if not already present (it is — used by PiConnection).

Also add a `displaySize` property to `PiConnection`:

```kotlin
var displayWidth: Int? = null
    private set
var displayHeight: Int? = null
    private set
```

In the `connect()` method, after the session key extraction succeeds, add:

```kotlin
// Parse display resolution if present
val displayInfo = parseDisplayFromAck(ack)
if (displayInfo != null) {
    displayWidth = displayInfo.first
    displayHeight = displayInfo.second
    Log.i(TAG, "Head unit display: ${displayWidth}x${displayHeight}")
}
```

In `disconnect()`, reset them:

```kotlin
displayWidth = null
displayHeight = null
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PiConnectionParsingTest" --info 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/PiConnection.kt \
       app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt
git commit -m "feat: parse display resolution from hello_ack"
```

---

### Task 4: Theme Protocol Messages

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/net/Protocol.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/ProtocolTest.kt`

**Step 1: Write failing tests**

Add to `ProtocolTest.kt`:

```kotlin
@Test
fun buildThemeMessage_includesAllFields() {
    val sessionKey = "test-key".toByteArray()
    val themeJson = org.json.JSONObject().apply {
        put("version", 1)
        put("name", "Test Theme")
        put("seed", "#FF0000")
        put("light", org.json.JSONObject().apply { put("primary", "#FF0000") })
        put("dark", org.json.JSONObject().apply { put("primary", "#CC0000") })
    }
    val msg = Protocol.buildThemeMessage(sessionKey, themeJson, "jpeg", 65536, 1)
    assertEquals("theme", msg.getString("type"))
    assertNotNull(msg.getJSONObject("theme"))
    assertEquals("Test Theme", msg.getJSONObject("theme").getString("name"))
    val wallpaper = msg.getJSONObject("wallpaper")
    assertEquals("jpeg", wallpaper.getString("format"))
    assertEquals(65536, wallpaper.getInt("size"))
    assertEquals(1, wallpaper.getInt("chunks"))
    assertTrue(msg.has("mac"))
    assertTrue(Protocol.verifyMac(msg, sessionKey))
}

@Test
fun buildThemeDataChunk_includesFields() {
    val sessionKey = "test-key".toByteArray()
    val data = "SGVsbG8=" // base64 for "Hello"
    val msg = Protocol.buildThemeDataChunk(sessionKey, 0, data)
    assertEquals("theme_data", msg.getString("type"))
    assertEquals(0, msg.getInt("chunk"))
    assertEquals(data, msg.getString("data"))
    assertTrue(Protocol.verifyMac(msg, sessionKey))
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ProtocolTest" --info 2>&1 | tail -20`
Expected: FAIL — methods don't exist

**Step 3: Implement theme protocol methods**

Add to `Protocol.kt`:

```kotlin
fun buildThemeMessage(
    sessionKey: ByteArray,
    themeJson: JSONObject,
    wallpaperFormat: String,
    wallpaperSize: Int,
    wallpaperChunks: Int
): JSONObject {
    val payload = JSONObject().apply {
        put("type", "theme")
        put("theme", themeJson)
        put("wallpaper", JSONObject().apply {
            put("format", wallpaperFormat)
            put("size", wallpaperSize)
            put("chunks", wallpaperChunks)
        })
    }
    val mac = computeHmac(sessionKey, payload.toString().toByteArray())
    payload.put("mac", mac)
    return payload
}

fun buildThemeDataChunk(
    sessionKey: ByteArray,
    chunkIndex: Int,
    base64Data: String
): JSONObject {
    val payload = JSONObject().apply {
        put("type", "theme_data")
        put("chunk", chunkIndex)
        put("data", base64Data)
    }
    val mac = computeHmac(sessionKey, payload.toString().toByteArray())
    payload.put("mac", mac)
    return payload
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ProtocolTest" --info 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/Protocol.kt \
       app/src/test/java/org/openauto/companion/net/ProtocolTest.kt
git commit -m "feat: add theme and theme_data protocol message builders"
```

---

### Task 5: Theme Color Generation

**Files:**
- Create: `app/src/main/java/org/openauto/companion/theme/ThemeGenerator.kt`
- Create: `app/src/test/java/org/openauto/companion/theme/ThemeGeneratorTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.openauto.companion.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeGeneratorTest {
    @Test
    fun generateScheme_producesLightAndDark() {
        val seedArgb = 0xFF6750A4.toInt() // Material purple
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
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.theme.ThemeGeneratorTest" --info 2>&1 | tail -20`
Expected: FAIL — class doesn't exist

**Step 3: Implement ThemeGenerator**

```kotlin
package org.openauto.companion.theme

import com.google.material.color.scheme.SchemeContent
import com.google.material.color.hct.Hct
import com.google.material.color.dynamiccolor.MaterialDynamicColors
import org.json.JSONObject

object ThemeGenerator {

    private val dynamicColors = MaterialDynamicColors()

    fun generateScheme(seedArgb: Int, name: String = ""): JSONObject {
        val hct = Hct.fromInt(seedArgb)
        val lightScheme = SchemeContent(hct, false, 0.0)
        val darkScheme = SchemeContent(hct, true, 0.0)

        return JSONObject().apply {
            put("version", 1)
            if (name.isNotBlank()) put("name", name)
            put("seed", argbToHex(seedArgb))
            put("light", schemeToJson(lightScheme))
            put("dark", schemeToJson(darkScheme))
        }
    }

    private fun schemeToJson(scheme: SchemeContent): JSONObject {
        val colors = dynamicColors
        return JSONObject().apply {
            put("primary", argbToHex(colors.primary().getArgb(scheme)))
            put("onPrimary", argbToHex(colors.onPrimary().getArgb(scheme)))
            put("primaryContainer", argbToHex(colors.primaryContainer().getArgb(scheme)))
            put("onPrimaryContainer", argbToHex(colors.onPrimaryContainer().getArgb(scheme)))
            put("secondary", argbToHex(colors.secondary().getArgb(scheme)))
            put("onSecondary", argbToHex(colors.onSecondary().getArgb(scheme)))
            put("secondaryContainer", argbToHex(colors.secondaryContainer().getArgb(scheme)))
            put("onSecondaryContainer", argbToHex(colors.onSecondaryContainer().getArgb(scheme)))
            put("tertiary", argbToHex(colors.tertiary().getArgb(scheme)))
            put("onTertiary", argbToHex(colors.onTertiary().getArgb(scheme)))
            put("tertiaryContainer", argbToHex(colors.tertiaryContainer().getArgb(scheme)))
            put("onTertiaryContainer", argbToHex(colors.onTertiaryContainer().getArgb(scheme)))
            put("error", argbToHex(colors.error().getArgb(scheme)))
            put("onError", argbToHex(colors.onError().getArgb(scheme)))
            put("errorContainer", argbToHex(colors.errorContainer().getArgb(scheme)))
            put("onErrorContainer", argbToHex(colors.onErrorContainer().getArgb(scheme)))
            put("background", argbToHex(colors.background().getArgb(scheme)))
            put("onBackground", argbToHex(colors.onBackground().getArgb(scheme)))
            put("surface", argbToHex(colors.surface().getArgb(scheme)))
            put("onSurface", argbToHex(colors.onSurface().getArgb(scheme)))
            put("surfaceVariant", argbToHex(colors.surfaceVariant().getArgb(scheme)))
            put("onSurfaceVariant", argbToHex(colors.onSurfaceVariant().getArgb(scheme)))
            put("outline", argbToHex(colors.outline().getArgb(scheme)))
            put("outlineVariant", argbToHex(colors.outlineVariant().getArgb(scheme)))
            put("inverseSurface", argbToHex(colors.inverseSurface().getArgb(scheme)))
            put("inverseOnSurface", argbToHex(colors.inverseOnSurface().getArgb(scheme)))
            put("inversePrimary", argbToHex(colors.inversePrimary().getArgb(scheme)))
            put("surfaceDim", argbToHex(colors.surfaceDim().getArgb(scheme)))
            put("surfaceBright", argbToHex(colors.surfaceBright().getArgb(scheme)))
            put("surfaceContainerLowest", argbToHex(colors.surfaceContainerLowest().getArgb(scheme)))
            put("surfaceContainerLow", argbToHex(colors.surfaceContainerLow().getArgb(scheme)))
            put("surfaceContainer", argbToHex(colors.surfaceContainer().getArgb(scheme)))
            put("surfaceContainerHigh", argbToHex(colors.surfaceContainerHigh().getArgb(scheme)))
            put("surfaceContainerHighest", argbToHex(colors.surfaceContainerHighest().getArgb(scheme)))
        }
    }

    fun argbToHex(argb: Int): String {
        return "#%06X".format(argb and 0xFFFFFF)
    }
}
```

**Note:** The exact API surface of `material-color-utilities` may differ by version. The implementer MUST check the actual library API. Key classes to look for: `SchemeContent` (or `SchemeTonalSpot`, `SchemeVibrant`), `Hct`, `MaterialDynamicColors`, `DynamicColor`. If the Java API uses `DynamicScheme` as the base class, `SchemeContent` extends it. The `MaterialDynamicColors` instance provides accessor methods for each color role. Consult the library source or docs if method names differ — the concept is the same.

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.theme.ThemeGeneratorTest" --info 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/theme/ThemeGenerator.kt \
       app/src/test/java/org/openauto/companion/theme/ThemeGeneratorTest.kt
git commit -m "feat: M3 theme generation from seed color"
```

---

### Task 6: Theme Transfer Logic

**Files:**
- Create: `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt`
- Create: `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt`

This encapsulates the logic of chunking a wallpaper image, building protocol messages, and sending them over a connection. Separated from PiConnection to keep concerns clean and testable.

**Step 1: Write failing tests**

```kotlin
package org.openauto.companion.net

import org.junit.Assert.*
import org.junit.Test
import android.util.Base64

class ThemeTransferTest {
    @Test
    fun chunkData_singleChunk() {
        val data = ByteArray(100) { it.toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        assertEquals(1, chunks.size)
        // Verify base64 round-trip
        val decoded = java.util.Base64.getDecoder().decode(chunks[0])
        assertArrayEquals(data, decoded)
    }

    @Test
    fun chunkData_multipleChunks() {
        val data = ByteArray(150000) { (it % 256).toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        assertEquals(3, chunks.size) // 65536 + 65536 + 18928
    }

    @Test
    fun chunkData_exactBoundary() {
        val data = ByteArray(65536) { it.toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        assertEquals(1, chunks.size)
    }

    @Test
    fun chunkData_emptyInput() {
        val chunks = ThemeTransfer.chunkBytes(ByteArray(0), chunkSize = 65536)
        assertEquals(0, chunks.size)
    }

    @Test
    fun chunkData_roundTripsAllData() {
        val data = ByteArray(200000) { (it % 256).toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        val reassembled = chunks.map { java.util.Base64.getDecoder().decode(it) }
            .reduce { acc, bytes -> acc + bytes }
        assertArrayEquals(data, reassembled)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ThemeTransferTest" --info 2>&1 | tail -20`
Expected: FAIL — class doesn't exist

**Step 3: Implement ThemeTransfer**

```kotlin
package org.openauto.companion.net

import android.util.Log
import org.json.JSONObject
import java.util.Base64

object ThemeTransfer {
    private const val TAG = "ThemeTransfer"
    private const val CHUNK_SIZE = 65536 // 64KB per chunk

    /**
     * Splits raw bytes into base64-encoded chunks.
     */
    fun chunkBytes(data: ByteArray, chunkSize: Int = CHUNK_SIZE): List<String> {
        if (data.isEmpty()) return emptyList()
        return data.toList()
            .chunked(chunkSize)
            .map { chunk ->
                Base64.getEncoder().encodeToString(chunk.toByteArray())
            }
    }

    /**
     * Result of a theme transfer attempt.
     */
    sealed class TransferResult {
        data object Success : TransferResult()
        data class Failed(val reason: String) : TransferResult()
    }

    /**
     * Sends a theme + wallpaper to the head unit.
     * Call from a background thread — blocks until complete or failed.
     *
     * @param connection Active, authenticated PiConnection
     * @param themeJson The full theme JSON (version, name, seed, light, dark)
     * @param wallpaperBytes JPEG-encoded wallpaper image
     * @param readResponse Function that reads a line from the connection (injected for testability)
     */
    fun send(
        connection: PiConnection,
        themeJson: JSONObject,
        wallpaperBytes: ByteArray,
        readResponse: () -> String?
    ): TransferResult {
        val sessionKey = connection.sessionKey
            ?: return TransferResult.Failed("No session key")
        if (!connection.isConnected()) {
            return TransferResult.Failed("Not connected")
        }

        val chunks = chunkBytes(wallpaperBytes)
        if (chunks.isEmpty()) {
            return TransferResult.Failed("Wallpaper data is empty")
        }

        // Send theme message
        val themeMsg = Protocol.buildThemeMessage(
            sessionKey = sessionKey,
            themeJson = themeJson,
            wallpaperFormat = "jpeg",
            wallpaperSize = wallpaperBytes.size,
            wallpaperChunks = chunks.size
        )
        connection.sendStatus(themeMsg) // reuses the same send mechanism
        Log.i(TAG, "Sent theme message (${chunks.size} wallpaper chunks to follow)")

        // Send wallpaper chunks
        for ((index, chunkData) in chunks.withIndex()) {
            val chunkMsg = Protocol.buildThemeDataChunk(sessionKey, index, chunkData)
            connection.sendStatus(chunkMsg)
            Log.d(TAG, "Sent wallpaper chunk $index/${chunks.size}")
        }

        // Wait for ack
        return try {
            val ackLine = readResponse()
                ?: return TransferResult.Failed("No theme_ack received")
            val ack = JSONObject(ackLine)
            if (ack.optString("type") == "theme_ack" && ack.optBoolean("accepted", false)) {
                Log.i(TAG, "Theme accepted by head unit")
                TransferResult.Success
            } else {
                TransferResult.Failed("Theme rejected: ${ack.optString("reason", "unknown")}")
            }
        } catch (e: Exception) {
            TransferResult.Failed("Error reading ack: ${e.message}")
        }
    }
}
```

**Note:** The `readResponse` parameter is injected because `PiConnection` currently doesn't expose a public `readLine()`. We'll need to add one — see step 4.

**Step 4: Add readLine to PiConnection**

In `PiConnection.kt`, add a public method:

```kotlin
fun readLine(): String? = reader?.readLine()
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ThemeTransferTest" --info 2>&1 | tail -20`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt \
       app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt \
       app/src/main/java/org/openauto/companion/net/PiConnection.kt
git commit -m "feat: theme transfer with chunked wallpaper sending"
```

---

### Task 7: Expose Connection + Display Info from CompanionService

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`

The ThemeBuilderScreen needs access to: (a) whether we're connected, (b) the display resolution, and (c) a way to trigger a theme send. We expose display info as StateFlows and add a theme send method.

**Step 1: Add display resolution StateFlows**

In `CompanionService.kt` companion object, add:

```kotlin
private val _displayWidth = MutableStateFlow<Int?>(null)
val displayWidth: StateFlow<Int?> = _displayWidth.asStateFlow()

private val _displayHeight = MutableStateFlow<Int?>(null)
val displayHeight: StateFlow<Int?> = _displayHeight.asStateFlow()

private val _themeTransferResult = MutableStateFlow<ThemeTransfer.TransferResult?>(null)
val themeTransferResult: StateFlow<ThemeTransfer.TransferResult?> = _themeTransferResult.asStateFlow()
```

**Step 2: Populate display info after connection**

In `attemptConnection()`, after `connection = conn` and `_connected.value = true`, add:

```kotlin
_displayWidth.value = conn.displayWidth
_displayHeight.value = conn.displayHeight
```

In `clearConnectionState()`, add:

```kotlin
_displayWidth.value = null
_displayHeight.value = null
_themeTransferResult.value = null
```

In `onDestroy()`, add:

```kotlin
_displayWidth.value = null
_displayHeight.value = null
_themeTransferResult.value = null
```

**Step 3: Add theme send method**

Add to `CompanionService`:

```kotlin
fun sendTheme(themeJson: JSONObject, wallpaperBytes: ByteArray) {
    val conn = connection ?: run {
        _themeTransferResult.value = ThemeTransfer.TransferResult.Failed("Not connected")
        return
    }
    _themeTransferResult.value = null // reset
    executor.execute {
        val result = ThemeTransfer.send(conn, themeJson, wallpaperBytes) { conn.readLine() }
        _themeTransferResult.value = result
    }
}

fun clearThemeTransferResult() {
    _themeTransferResult.value = null
}
```

Also update `attemptConnection` to store display resolution on the Vehicle via prefs — in the success branch, after reading display info:

```kotlin
// Persist display resolution for offline use (crop ratio)
val dw = conn.displayWidth
val dh = conn.displayHeight
if (dw != null && dh != null) {
    // We don't have prefs here, so we emit via StateFlow only.
    // MainActivity will persist when it observes.
}
```

Actually, keep it simple — just use StateFlows. The crop ratio only matters while connected anyway.

**Step 4: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, ALL TESTS PASS

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/service/CompanionService.kt
git commit -m "feat: expose display resolution and theme transfer from CompanionService"
```

---

### Task 8: ThemeBuilderScreen UI

**Files:**
- Create: `app/src/main/java/org/openauto/companion/ui/ThemeBuilderScreen.kt`

This is the main UI. It's a Compose screen that orchestrates: wallpaper picking, cropping, color extraction, seed selection, palette preview, naming, and sending.

**Step 1: Create ThemeBuilderScreen**

```kotlin
package org.openauto.companion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.palette.graphics.Palette
import org.json.JSONObject
import org.openauto.companion.theme.ThemeGenerator
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * State for the theme builder flow.
 */
data class ThemeBuilderState(
    val croppedBitmap: Bitmap? = null,
    val wallpaperBytes: ByteArray? = null,
    val dominantColors: List<Int> = emptyList(),  // ARGB ints
    val selectedSeed: Int? = null,
    val themeName: String = "Theme from ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())}",
    val generatedTheme: JSONObject? = null,
    val isSending: Boolean = false,
    val sendResult: String? = null
)

@Composable
fun ThemeBuilderScreen(
    displayWidth: Int,
    displayHeight: Int,
    onSendTheme: (JSONObject, ByteArray) -> Unit,
    transferResult: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ThemeBuilderState()) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // TODO: Launch cropper activity with aspect ratio displayWidth:displayHeight
            // For now, load and scale directly (cropper integration in next step)
            val inputStream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                val cropped = centerCropBitmap(bitmap, displayWidth, displayHeight)
                val baos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()

                // Extract dominant colors
                val palette = Palette.from(cropped).maximumColorCount(16).generate()
                val colors = listOfNotNull(
                    palette.getDominantColor(0).takeIf { c -> c != 0 },
                    palette.getVibrantColor(0).takeIf { c -> c != 0 },
                    palette.getMutedColor(0).takeIf { c -> c != 0 },
                    palette.getDarkVibrantColor(0).takeIf { c -> c != 0 },
                    palette.getLightVibrantColor(0).takeIf { c -> c != 0 }
                ).distinct().take(5)

                val seed = colors.firstOrNull() ?: 0xFF6750A4.toInt()
                val theme = ThemeGenerator.generateScheme(seed, state.themeName)

                state = state.copy(
                    croppedBitmap = cropped,
                    wallpaperBytes = bytes,
                    dominantColors = colors,
                    selectedSeed = seed,
                    generatedTheme = theme
                )
            }
        }
    }

    // Update theme when seed changes
    LaunchedEffect(state.selectedSeed, state.themeName) {
        val seed = state.selectedSeed ?: return@LaunchedEffect
        state = state.copy(generatedTheme = ThemeGenerator.generateScheme(seed, state.themeName))
    }

    // Update send result from service
    LaunchedEffect(transferResult) {
        if (transferResult != null) {
            state = state.copy(isSending = false, sendResult = transferResult)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Theme Builder", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Target info
        Text(
            "Target: ${displayWidth}x${displayHeight}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Wallpaper picker
        if (state.croppedBitmap == null) {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose Wallpaper")
            }
        } else {
            // Show cropped wallpaper preview
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    androidx.compose.foundation.Image(
                        bitmap = state.croppedBitmap!!.asImageBitmap(),
                        contentDescription = "Wallpaper preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(displayWidth.toFloat() / displayHeight.toFloat())
                    )
                    TextButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Change")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Seed color selection
            Text("Seed Color", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.dominantColors.forEach { colorArgb ->
                    val isSelected = colorArgb == state.selectedSeed
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(colorArgb))
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                state = state.copy(selectedSeed = colorArgb)
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Palette preview
            state.generatedTheme?.let { theme ->
                Text("Light Palette", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                PalettePreview(theme.getJSONObject("light"))

                Spacer(modifier = Modifier.height(12.dp))

                Text("Dark Palette", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                PalettePreview(theme.getJSONObject("dark"))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Theme name
            OutlinedTextField(
                value = state.themeName,
                onValueChange = { state = state.copy(themeName = it) },
                label = { Text("Theme Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Send button
            Button(
                onClick = {
                    val theme = state.generatedTheme ?: return@Button
                    val bytes = state.wallpaperBytes ?: return@Button
                    state = state.copy(isSending = true, sendResult = null)
                    onSendTheme(theme, bytes)
                },
                enabled = state.generatedTheme != null && state.wallpaperBytes != null && !state.isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sending...")
                } else {
                    Text("Send to Head Unit")
                }
            }

            // 6. Result
            state.sendResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                val isSuccess = result == "success"
                Text(
                    text = if (isSuccess) "Theme sent successfully!" else "Failed: $result",
                    color = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PalettePreview(schemeJson: JSONObject) {
    val previewRoles = listOf(
        "primary", "onPrimary", "primaryContainer",
        "secondary", "onSecondary", "secondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer",
        "surface", "surfaceVariant", "outline"
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(previewRoles) { role ->
            val hex = schemeJson.optString(role, "#808080")
            val color = parseHexColor(hex)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(color, MaterialTheme.shapes.small)
            )
        }
    }
}

private fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return try {
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (_: Exception) {
        Color.Gray
    }
}

/**
 * Simple center-crop to target aspect ratio and resolution.
 * TODO: Replace with proper cropper library for user-controlled cropping.
 */
private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
    val sourceRatio = source.width.toFloat() / source.height.toFloat()

    val (cropWidth, cropHeight) = if (sourceRatio > targetRatio) {
        // Source is wider — crop sides
        val h = source.height
        val w = (h * targetRatio).toInt()
        w to h
    } else {
        // Source is taller — crop top/bottom
        val w = source.width
        val h = (w / targetRatio).toInt()
        w to h
    }

    val x = (source.width - cropWidth) / 2
    val y = (source.height - cropHeight) / 2
    val cropped = Bitmap.createBitmap(source, x, y, cropWidth, cropHeight)
    return Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
}
```

**Note on cropper library:** The initial implementation uses a simple `centerCropBitmap` function. Integrating the `android-image-cropper` library for user-controlled cropping is a follow-up enhancement. The library requires launching a crop activity/contract which adds complexity. Start with auto center-crop to get the feature working end-to-end, then iterate on UX.

**Step 2: Run build to verify compilation**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/org/openauto/companion/ui/ThemeBuilderScreen.kt
git commit -m "feat: ThemeBuilderScreen with wallpaper picker and palette preview"
```

---

### Task 9: Wire ThemeBuilderScreen into Navigation

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/StatusScreen.kt`

**Step 1: Add ThemeBuilder screen to navigation enum**

In `MainActivity.kt`, update the `Screen` sealed class:

```kotlin
private sealed class Screen {
    data object VehicleList : Screen()
    data object Pairing : Screen()
    data object QrScan : Screen()
    data class Status(val vehicle: Vehicle) : Screen()
    data class ThemeBuilder(val vehicle: Vehicle) : Screen()
}
```

**Step 2: Add "Theme Builder" button to StatusScreen**

In `StatusScreen.kt`, add a parameter and button. Update the function signature:

```kotlin
@Composable
fun StatusScreen(
    vehicleName: String,
    status: CompanionStatus,
    socks5Enabled: Boolean,
    onSocks5Toggle: (Boolean) -> Unit,
    audioKeepAlive: Boolean,
    onAudioKeepAliveToggle: (Boolean) -> Unit,
    onOpenSettingsPage: () -> Unit,
    onOpenThemeBuilder: () -> Unit,  // NEW
    onUnpair: () -> Unit,
    onBack: () -> Unit
)
```

Add a button after the "Open Settings Page" button:

```kotlin
Button(
    onClick = onOpenSettingsPage,
    enabled = status.connected,
    modifier = Modifier.fillMaxWidth()
) {
    Text("Open Settings Page")
}

Spacer(modifier = Modifier.height(8.dp))

Button(
    onClick = onOpenThemeBuilder,
    enabled = status.connected,
    modifier = Modifier.fillMaxWidth()
) {
    Text("Theme Builder")
}
```

**Step 3: Add ThemeBuilder case to MainActivity's when block**

In `MainActivity.kt`, inside the `when (val s = screen)` block, add:

```kotlin
is Screen.ThemeBuilder -> {
    BackHandler { screen = Screen.Status(s.vehicle) }
    val displayW by CompanionService.displayWidth.collectAsStateWithLifecycle()
    val displayH by CompanionService.displayHeight.collectAsStateWithLifecycle()
    val transferResult by CompanionService.themeTransferResult.collectAsStateWithLifecycle()

    ThemeBuilderScreen(
        displayWidth = displayW ?: 1024,
        displayHeight = displayH ?: 600,
        onSendTheme = { themeJson, wallpaperBytes ->
            // Get the service instance and call sendTheme
            // Since CompanionService is a started service, we need a different approach.
            // Use a companion object method that delegates to the active instance.
            CompanionService.sendThemeStatic(themeJson, wallpaperBytes)
        },
        transferResult = when (val r = transferResult) {
            is ThemeTransfer.TransferResult.Success -> "success"
            is ThemeTransfer.TransferResult.Failed -> r.reason
            null -> null
        },
        onBack = { screen = Screen.Status(s.vehicle) }
    )
}
```

**Step 4: Add static sendTheme accessor to CompanionService**

In `CompanionService.kt`, we need a way for the UI to trigger theme sending without a bound service. Add to the companion object:

```kotlin
private var instance: CompanionService? = null
```

In `onCreate()`:

```kotlin
instance = this
```

In `onDestroy()`:

```kotlin
instance = null
```

Add static method:

```kotlin
fun sendThemeStatic(themeJson: JSONObject, wallpaperBytes: ByteArray) {
    instance?.sendTheme(themeJson, wallpaperBytes)
        ?: run { _themeTransferResult.value = ThemeTransfer.TransferResult.Failed("Service not running") }
}
```

**Step 5: Update the StatusScreen call in MainActivity to include onOpenThemeBuilder**

In the `Screen.Status` case, add the parameter:

```kotlin
onOpenThemeBuilder = {
    screen = Screen.ThemeBuilder(vehicle)
},
```

**Step 6: Add necessary imports to MainActivity**

```kotlin
import org.openauto.companion.net.ThemeTransfer
```

**Step 7: Run full build and tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, ALL TESTS PASS

**Step 8: Commit**

```bash
git add app/src/main/java/org/openauto/companion/ui/MainActivity.kt \
       app/src/main/java/org/openauto/companion/ui/StatusScreen.kt \
       app/src/main/java/org/openauto/companion/service/CompanionService.kt
git commit -m "feat: wire ThemeBuilderScreen into app navigation"
```

---

### Task 10: Integration Smoke Test & Cleanup

**Files:**
- All files from previous tasks

**Step 1: Run full build and test suite**

Run: `./gradlew clean :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, ALL TESTS PASS

**Step 2: Verify APK size hasn't exploded**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: Should be under 10MB (material-color-utilities adds ~200KB)

**Step 3: Review all new files are in correct packages**

Run: `find app/src -name "*.kt" | sort`

Expected new files:
- `app/src/main/java/org/openauto/companion/theme/ThemeGenerator.kt`
- `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt`
- `app/src/main/java/org/openauto/companion/ui/ThemeBuilderScreen.kt`
- `app/src/test/java/org/openauto/companion/theme/ThemeGeneratorTest.kt`
- `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt`

**Step 4: Final commit if any cleanup was needed**

```bash
git add -A
git commit -m "chore: theme builder integration cleanup"
```

---

## Future Enhancements (Not in this plan)

1. **User-controlled image cropping** — Integrate `android-image-cropper` library for drag-to-crop instead of auto center-crop
2. **Custom color picker** — Add a full HSB color wheel for manual seed selection beyond extracted palette colors
3. **Wallpaper + theme mockup preview** — Composite the wallpaper with themed UI overlays to show how it'll look on the head unit
4. **Persist display resolution per vehicle** — Store in CompanionPrefs so crop ratio is available offline
5. **Theme history** — Save sent themes locally for re-sending
