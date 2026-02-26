# Open Settings Page Button Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Status screen button that opens the head unit settings webpage on the phone browser, using per-vehicle QR-provided host/port with fallback to `http://10.0.0.1:8080`.

## Acceptance Criteria

- No active Android Auto media stream is interrupted while launching settings page flow.
- Settings page launch is resilient: invalid URLs, missing browser handler, and stale endpoint values fail safely with user-facing guidance.
- Existing connection/session state is preserved regardless of settings-launch outcome.

**Architecture:** Persist optional settings endpoint fields on each `Vehicle`, parse endpoint data from QR pairing links, and surface a primary action button in `StatusScreen` that launches a browser intent only when the selected vehicle is connected. Extract URI parsing and URL building into small pure Kotlin helpers so behavior is unit-testable.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android Intent ACTION_VIEW, JVM unit tests with JUnit4, Gradle.

---

### Task 1: Persist Per-Vehicle Settings Endpoint

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/data/Vehicle.kt`
- Modify: `app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt`

**Step 1: Write failing serialization tests for endpoint fields**

Add tests that assert `settingsHost` and `settingsPort` round-trip through `listToJson()` / `listFromJson()` and default to null when absent.

```kotlin
assertEquals("10.0.0.1", result[0].settingsHost)
assertEquals(8080, result[0].settingsPort)
assertNull(v.settingsHost)
assertNull(v.settingsPort)
```

**Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest"`
Expected: FAIL because new fields do not exist yet.

**Step 3: Implement minimal model changes**

- Add optional fields to `Vehicle`:
- `settingsHost: String? = null`
- `settingsPort: Int? = null`
- Include both fields in JSON output only when non-null.
- Read both fields with null-safe defaults in `fromJson`.

**Step 4: Re-run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/data/Vehicle.kt \
        app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt
git commit -m "feat: persist vehicle settings host and port"
```

### Task 2: Add Pairing URI Parser for QR Payload

**Files:**
- Create: `app/src/main/java/org/openauto/companion/net/PairingUriParser.kt`
- Create: `app/src/test/java/org/openauto/companion/net/PairingUriParserTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/QrScanScreen.kt`

**Step 1: Write failing parser tests**

Cover:
- valid `openauto://pair?...` URI with `pin`, `ssid`, `host`, `port`
- valid URI missing `host`/`port` (allowed)
- invalid scheme/path
- non-numeric port

```kotlin
val parsed = PairingUriParser.parse("openauto://pair?pin=123456&ssid=CarAP&host=10.0.0.1&port=8080")
assertEquals("123456", parsed?.pin)
assertEquals("CarAP", parsed?.ssid)
assertEquals("10.0.0.1", parsed?.host)
assertEquals(8080, parsed?.port)
```

**Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PairingUriParserTest"`
Expected: FAIL (parser class missing).

**Step 3: Implement parser**

Create a small parser returning a data class:

```kotlin
data class PairingPayload(
    val ssid: String,
    val pin: String,
    val host: String?,
    val port: Int?
)
```

Rules:
- require scheme `openauto`, host `pair`
- require 6-digit `pin`
- require non-empty `ssid` (current app pairing contract)
- `host`/`port` optional
- reject invalid `port`

**Step 4: Update QR scanner to use parser**

In `QrScanScreen`, replace inline `Uri` query extraction with `PairingUriParser.parse(rawValue)` and emit parsed fields.

**Step 5: Re-run parser tests**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PairingUriParserTest"`
Expected: PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/PairingUriParser.kt \
        app/src/test/java/org/openauto/companion/net/PairingUriParserTest.kt \
        app/src/main/java/org/openauto/companion/ui/QrScanScreen.kt
git commit -m "feat: parse settings endpoint from QR pairing URI"
```

### Task 3: Wire Parsed Endpoint into Pairing Flow

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/ui/QrScanScreen.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`

**Step 1: Write failing flow test (if practical) or add focused unit test around mapper helper**

Add a small helper in `MainActivity.kt` (or separate file) that builds `Vehicle` from parsed payload and test it in JVM.

**Step 2: Update QR callback signature**

Change callback from:

```kotlin
onScanned: (ssid: String, pin: String) -> Unit
```

to:

```kotlin
onScanned: (ssid: String, pin: String, host: String?, port: Int?) -> Unit
```

**Step 3: Save host/port on vehicle creation in `MainActivity`**

When QR pairing succeeds, create `Vehicle(..., settingsHost = host, settingsPort = port)`.

**Step 4: Run targeted tests**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest" --tests "org.openauto.companion.net.PairingUriParserTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/ui/QrScanScreen.kt \
        app/src/main/java/org/openauto/companion/ui/MainActivity.kt
git commit -m "feat: store QR settings host and port during pairing"
```

### Task 4: Add URL Builder with Fallback

**Files:**
- Create: `app/src/main/java/org/openauto/companion/net/SettingsUrlBuilder.kt`
- Create: `app/src/test/java/org/openauto/companion/net/SettingsUrlBuilderTest.kt`

**Step 1: Write failing URL builder tests**

Cover:
- host+port present → `http://host:port`
- missing host or port → fallback `http://10.0.0.1:8080`
- blank host → fallback

**Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.SettingsUrlBuilderTest"`
Expected: FAIL.

**Step 3: Implement minimal builder**

```kotlin
object SettingsUrlBuilder {
    const val FALLBACK_URL = "http://10.0.0.1:8080"
    fun build(host: String?, port: Int?): String = ...
}
```

**Step 4: Re-run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.SettingsUrlBuilderTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/SettingsUrlBuilder.kt \
        app/src/test/java/org/openauto/companion/net/SettingsUrlBuilderTest.kt
git commit -m "feat: add settings URL builder with fallback"
```

### Task 5: Add Status Screen Button and Browser Launch

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/ui/StatusScreen.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`

**Step 1: Add new `StatusScreen` callback parameter**

```kotlin
onOpenSettingsPage: () -> Unit
```

**Step 2: Add button directly under connection card**

- Full-width primary `Button`
- Text: `Open Settings Page`
- `enabled = status.connected`
- No helper text when disabled

**Step 3: Implement browser launch in `MainActivity`**

- Build URL from selected vehicle endpoint with `SettingsUrlBuilder.build(...)`
- Launch with `Intent(Intent.ACTION_VIEW, Uri.parse(url))`
- Guard with `resolveActivity(packageManager)` and show `Toast` if unavailable

**Step 4: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: all unit tests pass; app compiles.

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/ui/StatusScreen.kt \
        app/src/main/java/org/openauto/companion/ui/MainActivity.kt
git commit -m "feat: add status screen button to open head unit settings"
```

### Task 6: Verification and Documentation

**Files:**
- Modify: `docs/plans/2026-02-26-open-settings-button-design.md` (status/result notes)

**Step 1: Full verification run**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

**Step 2: Manual QA checklist**

- Paired vehicle with host/port: button opens expected URL.
- Paired vehicle without host/port: button opens fallback URL.
- Disconnected vehicle: button disabled.
- Connected vehicle: button enabled.

**Step 3: Record implementation notes**

Append brief “Implemented” section in design doc with date + verification commands used.

**Step 4: Final commit**

```bash
git add docs/plans/2026-02-26-open-settings-button-design.md
git commit -m "docs: record open settings button verification"
```
