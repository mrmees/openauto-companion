# In-App Web Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the external browser settings launch with an in-app WebView that can use the head-unit Wi-Fi network Android Auto owns.

**Architecture:** Add a small, unit-testable process-binding coordinator in `net`, then add a Compose WebView screen in `ui`. `MainActivity` routes the existing settings action to that screen and passes the same settings URL already used by the browser launch.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android WebView, Android `ConnectivityManager`, existing `WifiMonitor`.

---

### Task 1: Process Binding Coordinator

**Files:**
- Create: `app/src/main/java/org/openauto/companion/net/ProcessNetworkBinding.kt`
- Test: `app/src/test/java/org/openauto/companion/net/ProcessNetworkBindingTest.kt`

- [ ] **Step 1: Write failing tests**

Create `ProcessNetworkBindingTest` with a fake process binder. Cover successful bind/restore, missing network, failed bind, and restoring the previous network.

- [ ] **Step 2: Run tests to verify failure**

Run: `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:testDebugUnitTest --tests org.openauto.companion.net.ProcessNetworkBindingTest`

Expected: compile failure because `ProcessNetworkBinding` does not exist.

- [ ] **Step 3: Implement coordinator**

Create `ProcessNetworkBinding` with:
- `ProcessNetworkBinder` interface exposing `current()` and `bind(network: Network?)`.
- `AndroidProcessNetworkBinder` wrapping `ConnectivityManager`.
- `ProcessNetworkBinding.bindForScope(network)` returning a `Binding` with `restore()`.
- Result values for `Bound`, `NoNetwork`, and `Failed`.

- [ ] **Step 4: Run tests to verify pass**

Run the same targeted test command. Expected: tests pass.

### Task 2: WebView Screen

**Files:**
- Create: `app/src/main/java/org/openauto/companion/ui/WebConfigScreen.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`

- [ ] **Step 1: Add screen route**

Add `Screen.WebConfig(vehicle: Vehicle)` and route `onOpenSettingsPage` to that screen instead of `Intent.ACTION_VIEW`.

- [ ] **Step 2: Add WebView UI**

Create `WebConfigScreen` with a top app bar back button, loading indicator, warning/error text, and `AndroidView` WebView. Bind process routing in `DisposableEffect` before loading the URL and call `restore()` when the screen leaves composition.

- [ ] **Step 3: Compile**

Run: `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:assembleDebug`

Expected: build succeeds.

### Task 3: Docs, Handoff, and Device Install

**Files:**
- Modify: `docs/roadmap-current.md`
- Modify: `docs/session-handoffs.md`

- [ ] **Step 1: Update roadmap**

Record that in-app web config is now the active fix for Android Auto Wi-Fi routing and external browser routing is no longer reliable for this path.

- [ ] **Step 2: Append handoff**

Append what changed, why, status, next tests, and verification commands/results.

- [ ] **Step 3: Run full verification**

Run: `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug`

Expected: unit tests and debug build pass.

- [ ] **Step 4: Install on Pixel**

Run: `/mnt/e/Android/Sdk/platform-tools/adb.exe -s 39260DLJH000LX install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.
