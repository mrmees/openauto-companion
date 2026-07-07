# Theme HTTP Install Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Companion theme/wallpaper transfer over legacy TCP `9876` with one multipart HTTP POST to the head-unit web-config endpoint.

**Architecture:** Keep the transport boundary in `ThemeTransfer` and make it an OkHttp multipart installer. Use a small test-visible URL builder plus `sendToUrl` so unit tests can run against MockWebServer without binding port `8080`. Keep the UI callback shape intact and make `CompanionService` call the HTTP installer instead of requiring an active `PiConnection` for theme transfer.

**Tech Stack:** Kotlin, Android foreground service, OkHttp 4.12.0, MockWebServer, JUnit 4, `org.json.JSONObject`, Gradle Android unit tests.

---

## File Structure

- Modify `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt`
  - Replace legacy chunking tests with HTTP multipart and response-mapping tests.
- Modify `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt`
  - Remove chunking, legacy `PiConnection`, HMAC, and ack parsing.
  - Add host URL building, multipart request construction, response parsing, and exception mapping.
- Modify `app/src/main/java/org/openauto/companion/service/CompanionService.kt`
  - Change theme sending to call `ThemeTransfer.send(settingsHost, themeJson, wallpaperBytes)`.
  - Remove the active `PiConnection` requirement from theme transfer.
- Modify `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`
  - Pass the selected vehicle settings host into `CompanionService.sendThemeStatic`.
- Modify `docs/project-vision.md`
  - Mark the web-config theme/wallpaper upload endpoint dependency as delivered.
- Modify `docs/roadmap-current.md`
  - Remove wording that theme transfer still depends on a future endpoint.
- Modify `docs/session-handoffs.md`
  - Append the completed session handoff with verification evidence.

## Scope Guard

Do not change:

- `PiConnection.kt` runtime status transport.
- `Protocol.kt` legacy status/theme JSON builders except leaving unused theme builders in place for now.
- SOCKS5 behavior.
- External API v1 transport/session classes.
- Theme generation role names or color conversion.
- Theme builder UI layout beyond passing the selected host to the service call.

### Task 1: HTTP ThemeTransfer Tests

**Files:**
- Modify: `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt`
- Later implementation target: `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt`

- [ ] **Step 1: Replace the existing legacy chunking tests with failing HTTP tests**

Replace the full contents of `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt` with:

```kotlin
package org.openauto.companion.net

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ThemeTransferTest {
    @Test
    fun buildInstallUrl_usesDefaultHeadUnitHostAndWebConfigPort() {
        val url = ThemeTransfer.buildInstallUrl(null)

        assertEquals("http://10.0.0.1:8080/api/theme/install", url.toString())
    }

    @Test
    fun buildInstallUrl_trimsHostAndUsesWebConfigPort() {
        val url = ThemeTransfer.buildInstallUrl("  10.0.0.23  ")

        assertEquals("http://10.0.0.23:8080/api/theme/install", url.toString())
    }

    @Test
    fun sendToUrl_postsManifestAsPlainFormFieldAndWallpaperAsJpegFile() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"installed":true,"slug":"test-theme","applied":true}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            val request = server.takeRequest(5, TimeUnit.SECONDS)
            val body = request.body.readUtf8()

            assertTrue(result is ThemeTransfer.TransferResult.Success)
            assertEquals("POST", request.method)
            assertEquals("/api/theme/install", request.path)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
            assertTrue(body.contains("Content-Disposition: form-data; name=\"manifest\""))
            assertFalse(body.contains("Content-Disposition: form-data; name=\"manifest\"; filename="))
            assertTrue(body.contains("\"name\":\"Test Theme\""))
            assertTrue(body.contains("\"light\":{\"primary\":\"#FF8A65\"}"))
            assertTrue(body.contains("\"dark\":{\"primary\":\"#FFB59D\"}"))
            assertTrue(body.contains("Content-Disposition: form-data; name=\"wallpaper\"; filename=\"wallpaper.jpg\""))
            assertTrue(body.contains("Content-Type: image/jpeg"))
            assertTrue(body.contains("jpeg-bytes"))
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_omitsWallpaperPartWhenWallpaperBytesAreEmpty() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"installed":true,"slug":"color-only","applied":true}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = ByteArray(0),
                client = client
            )

            val body = server.takeRequest(5, TimeUnit.SECONDS).body.readUtf8()

            assertTrue(result is ThemeTransfer.TransferResult.Success)
            assertTrue(body.contains("Content-Disposition: form-data; name=\"manifest\""))
            assertFalse(body.contains("name=\"wallpaper\""))
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsInstalledFalseResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"installed":false,"error":"missing manifest"}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertEquals(ThemeTransfer.TransferResult.Failed("missing manifest"), result)
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsPayloadTooLargeResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(413).setBody("""{"installed":false,"error":"payload too large"}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertEquals(ThemeTransfer.TransferResult.Failed("payload too large"), result)
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsMalformedJsonResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertTrue(result is ThemeTransfer.TransferResult.Failed)
            assertTrue((result as ThemeTransfer.TransferResult.Failed).reason.startsWith("Invalid response:"))
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsNetworkFailureToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        val url = server.url("/api/theme/install")
        server.shutdown()

        val result = ThemeTransfer.sendToUrl(
            url = url,
            themeJson = sampleTheme(),
            wallpaperBytes = "jpeg-bytes".toByteArray(),
            client = client
        )

        client.shutdownForTest()
        assertTrue(result is ThemeTransfer.TransferResult.Failed)
        assertTrue((result as ThemeTransfer.TransferResult.Failed).reason.isNotBlank())
    }

    private fun sampleTheme(): JSONObject = JSONObject().apply {
        put("name", "Test Theme")
        put("seed", "#FF8A65")
        put("light", JSONObject().apply {
            put("primary", "#FF8A65")
        })
        put("dark", JSONObject().apply {
            put("primary", "#FFB59D")
        })
    }

    private fun OkHttpClient.shutdownForTest() {
        dispatcher.executorService.shutdown()
        connectionPool.evictAll()
    }
}
```

- [ ] **Step 2: Run the focused tests and verify the RED state**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ThemeTransferTest"
```

Expected: FAIL during Kotlin test compilation because `ThemeTransfer.buildInstallUrl` and `ThemeTransfer.sendToUrl` do not exist yet.

- [ ] **Step 3: Commit the failing tests**

```bash
git add app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt
git commit -m "test: specify http theme install transfer"
```

### Task 2: HTTP ThemeTransfer Implementation

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt`
- Test: `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt`

- [ ] **Step 1: Replace legacy transfer implementation with HTTP multipart implementation**

Replace the full contents of `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt` with:

```kotlin
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
                if (!wallpaperBytes.isNullOrEmpty()) {
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
```

- [ ] **Step 2: Run the focused ThemeTransfer tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ThemeTransferTest"
```

Expected: PASS for all `ThemeTransferTest` tests.

- [ ] **Step 3: Run protocol tests that still reference legacy theme builders**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ProtocolTest"
```

Expected: PASS. This confirms leaving `Protocol.buildThemeMessage` and `Protocol.buildThemeDataChunk` in place did not break existing protocol tests.

- [ ] **Step 4: Commit the HTTP transfer implementation**

```bash
git add app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt
git commit -m "feat: install themes over web-config http"
```

### Task 3: Service and UI Host Integration

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`
- Test: `app/src/test/java/org/openauto/companion/net/ThemeTransferTest.kt`

- [ ] **Step 1: Update the static service theme call to accept the selected vehicle host**

In `app/src/main/java/org/openauto/companion/service/CompanionService.kt`, replace lines 340-360 with:

```kotlin
    fun sendTheme(settingsHost: String?, themeJson: JSONObject, wallpaperBytes: ByteArray) {
        val targetHost = settingsHost?.trim()?.ifBlank { null }
        Log.i(
            TAG,
            "sendTheme: dispatching HTTP transfer to ${targetHost ?: "10.0.0.1"} (wallpaper=${wallpaperBytes.size} bytes)"
        )
        _themeTransferResult.value = null
        themeExecutor.execute {
            try {
                val result = ThemeTransfer.send(targetHost, themeJson, wallpaperBytes)
                Log.i(TAG, "sendTheme: transfer complete, result=$result")
                _themeTransferResult.value = result
            } catch (e: Exception) {
                Log.e(TAG, "sendTheme: transfer failed with exception", e)
                _themeTransferResult.value = ThemeTransfer.TransferResult.Failed(
                    e.message ?: "Transfer failed"
                )
            }
        }
    }
```

In the companion object of the same file, replace lines 412-415 with:

```kotlin
        fun sendThemeStatic(settingsHost: String?, themeJson: JSONObject, wallpaperBytes: ByteArray) {
            instance?.sendTheme(settingsHost, themeJson, wallpaperBytes)
                ?: run { _themeTransferResult.value = ThemeTransfer.TransferResult.Failed("Service not running") }
        }
```

- [ ] **Step 2: Update MainActivity to pass the selected vehicle settings host**

In `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`, replace the `onSendTheme` callback inside `Screen.ThemeBuilder` with:

```kotlin
                            onSendTheme = { themeJson, wallpaperBytes ->
                                CompanionService.sendThemeStatic(s.vehicle.settingsHost, themeJson, wallpaperBytes)
                            },
```

- [ ] **Step 3: Run focused compile/tests for affected unit-test target**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ThemeTransferTest"
```

Expected: PASS. This also compiles the main source changes and catches stale `sendThemeStatic` call signatures.

- [ ] **Step 4: Run a debug assemble check**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. This catches Android source compile issues outside JVM test coverage.

- [ ] **Step 5: Commit service/UI integration**

```bash
git add app/src/main/java/org/openauto/companion/service/CompanionService.kt app/src/main/java/org/openauto/companion/ui/MainActivity.kt
git commit -m "feat: route theme installs through web-config host"
```

### Task 4: Documentation Updates

**Files:**
- Modify: `docs/project-vision.md`
- Modify: `docs/roadmap-current.md`

- [ ] **Step 1: Mark the delivered head-unit dependency in project vision**

In `docs/project-vision.md`, find this blocker:

```markdown
- Web-config theme/wallpaper upload endpoint for legacy `9876` retirement.
  - Need: head-unit web-config HTTP endpoint for theme JSON plus wallpaper multipart upload/install.
  - Why: External API v1 deliberately excludes theme/wallpaper blobs and has a 256 KiB frame cap; HTTP is the approved channel.
  - Companion impact: theme transfer must stay on legacy `9876` until the HTTP endpoint contract ships and is integrated.
  - Status: Requested
```

Replace it with:

```markdown
- Web-config theme/wallpaper upload endpoint for legacy `9876` retirement.
  - Need: head-unit web-config HTTP endpoint for theme JSON plus wallpaper multipart upload/install.
  - Why: External API v1 deliberately excludes theme/wallpaper blobs and has a 256 KiB frame cap; HTTP is the approved channel.
  - Companion impact: Companion now installs themes through `POST /api/theme/install`; non-theme legacy `9876` runtime traffic is tracked separately.
  - Status: Delivered
```

In the same file, add this line to the end of `## Direction Change Log`:

```markdown
- 2026-07-07: Migrated Companion theme/wallpaper transfer from legacy `9876` chunks to the delivered web-config HTTP install endpoint.
```

- [ ] **Step 2: Update the roadmap dependency wording**

In `docs/roadmap-current.md`, inside the `External API v1 companion migration foundation` item, replace this sentence:

```markdown
    pairing window or stored known-client credentials. Theme transfer retirement
    depends on a future web-config HTTP upload endpoint.
```

with:

```markdown
    pairing window or stored known-client credentials. Theme transfer now uses
    the delivered web-config HTTP upload endpoint, while non-theme legacy
    traffic remains available during the broader runtime migration.
```

- [ ] **Step 3: Commit documentation updates**

```bash
git add docs/project-vision.md docs/roadmap-current.md
git commit -m "docs: mark theme http endpoint delivered"
```

### Task 5: Final Verification and Handoff

**Files:**
- Modify: `docs/session-handoffs.md`

- [ ] **Step 1: Run the repository verification gate**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL with both `:app:testDebugUnitTest` and `:app:assembleDebug` complete.

- [ ] **Step 2: Inspect git diff for scope**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected before the handoff entry: clean working tree if all earlier commits were made. If documentation handoff is not committed yet, only `docs/session-handoffs.md` should appear after Step 3.

- [ ] **Step 3: Append the session handoff**

Run:

```bash
date '+## %Y-%m-%d %H:%M (local)'
```

Append the command output as the heading in `docs/session-handoffs.md`, then append this body below it:

```markdown
- What changed:
  - Replaced legacy `9876` theme/wallpaper chunk transfer with one OkHttp multipart POST to the head-unit web-config theme install endpoint.
  - Sent `manifest` as a no-filename form field and wallpaper as optional `image/jpeg` file part `wallpaper.jpg`.
  - Updated theme install result handling so server `installed:false`, HTTP errors, malformed responses, and network failures surface as visible failures.
  - Passed the selected vehicle web-config host into theme installs, falling back to `10.0.0.1`.
  - Marked the delivered head-unit HTTP theme endpoint in project docs and roadmap.
- Why:
  - The head unit now ships the verified HTTP theme install endpoint, so theme transfer no longer needs the legacy `9876` chunk/HMAC/ack protocol.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Web-config theme/wallpaper upload endpoint for legacy `9876` retirement.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Run an optional live install against `10.0.0.1:8080` on real head-unit AP hardware.
  - 2) Continue External API runtime migration for non-theme legacy `9876` traffic.
  - 3) Remove legacy theme message builders after no remaining code references them.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ThemeTransferTest"` -> PASS
    - `./gradlew :app:assembleDebug` -> PASS
  - AA stream continuity: not tested (unit/build validation only; no runtime media/session behavior changed)
```

Use the actual local time in the heading. Keep the verification lines accurate if any command failed.

- [ ] **Step 4: Commit the handoff**

```bash
git add docs/session-handoffs.md
git commit -m "docs: record theme http install handoff"
```

- [ ] **Step 5: Confirm final repository state**

Run:

```bash
git status --short
git log --oneline -6
```

Expected: clean working tree. Recent commits should include the test, implementation, integration, docs, and handoff commits from this plan.

## Self-Review Checklist

- Spec coverage:
  - HTTP endpoint, multipart manifest no filename, optional JPEG wallpaper, result parsing, host fallback, docs updates, and final Gradle verification are covered.
  - Non-theme `9876` traffic remains untouched.
- Placeholder scan:
  - The plan contains exact paths, commands, code snippets, and expected results.
- Type consistency:
  - `ThemeTransfer.send(host, themeJson, wallpaperBytes, client)` delegates to `sendToUrl`.
  - `CompanionService.sendThemeStatic(settingsHost, themeJson, wallpaperBytes)` matches the `MainActivity` call site.
  - `TransferResult.Success` and `TransferResult.Failed(reason)` remain the UI-facing result types.
