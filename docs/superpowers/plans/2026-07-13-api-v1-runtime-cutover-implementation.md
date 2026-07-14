# External API v1 Runtime Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Follow
> test-driven development, keep each task green before committing, and do not
> push without Matthew's approval.

**Goal:** Remove Companion's legacy TCP `9876` runtime and replace it with
manual External API v1 PIN pairing plus reconnect-safe TCP `9810` reports for
time, GPS, battery, and connectivity.

**Architecture:** Keep Android collectors and lifecycle ownership in
`CompanionService`, but move transport/session/retry/report decisions into
small testable helpers. Pairing uses a short-lived network-bound v1 session and
saves a vehicle only after READY. Runtime owns one vehicle generation, one v1
client at a time, one serialized report writer, and a local SOCKS listener that
survives transient API reconnects. A schema-gated migration deletes legacy
vehicle records before the old protocol and data fields are removed.

**Tech Stack:** Kotlin, coroutines and Flow/Channel, Android foreground
service, `ConnectivityManager`, `LocationManager`, protobuf-javalite, TCP
`9810`, JUnit 4, kotlinx-coroutines-test, Gradle Android unit/instrumentation
tests.

**Design:**
`docs/superpowers/specs/2026-07-13-api-v1-runtime-cutover-design.md`

## Execution Status (2026-07-13)

- Tasks 1–9: complete in focused green commits through `1bcef52`.
- Task 10: instrumentation update complete in `202f684`; AndroidTest APK
  compiles. The no-flag device guard and guarded live suite passed on the
  attached Pixel.
- Task 11: JVM regression groups, mandatory unit/build gate, structural scans,
  and the bench-ready documentation/handoff are complete in `94910a0`.
- Task 12: complete. The Pixel/Prodigy bench passed with manual pairing,
  stored-client reconnect, all report types, route toggling/owner clearing,
  TCP `9876` refused, and the AA TCP session continuous through Companion
  lifecycle tests.

---

## Global Scope Guards

- Do not edit `app/src/main/proto/api/*.proto`; Prodigy owns the frozen
  additive contract.
- Do not change Prodigy source from this repository.
- Do not add TCP-to-WebSocket or v1-to-legacy fallback.
- Do not connect to or publish on port `9876` after the final cleanup task.
- Do not process-bind the application for API sockets; use the matched Android
  `Network.socketFactory`.
- Do not log PINs, API secrets/proofs, proxy passwords, or full credentialed
  protobuf messages.
- Keep `ThemeTransfer`, in-app web config, and audio keep-alive behavior intact.
- Do not activate QR pairing; manual PIN is the only pairing entry point.
- Run the repository gate before claiming implementation complete:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Commit Policy

Each task below ends in a focused green commit. Use the suggested message or an
equally narrow equivalent. Do not mix roadmap/handoff completion claims into a
code task before its verification is green. Do not push during execution.

---

### Task 1: Extract the Wi-Fi-Bound API Socket Factory

**Files:**

- Create: `app/src/main/java/org/openauto/companion/net/NetworkSocketFactory.kt`
- Create: `app/src/test/java/org/openauto/companion/net/NetworkSocketFactoryTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/PiConnection.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt`
- Modify: `app/src/androidTest/java/org/openauto/companion/net/api/ApiV1LiveValidationTest.kt`

**Purpose:** Preserve the proven EPERM-only socket fallback without keeping it
owned by the legacy client. Pairing and runtime will share this helper.

- [x] **Step 1: Write failing helper tests**

Cover:

- a bound socket is returned when the bound factory succeeds
- EPERM or nested `Operation not permitted` falls back exactly once to the
  injected unbound factory
- timeout, DNS, and unrelated socket errors do not fall back
- the fallback predicate handles nested causes

- [x] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.NetworkSocketFactoryTest"
```

Expected: test compilation fails because `NetworkSocketFactory` does not yet
exist.

- [x] **Step 3: Implement the transport-neutral helper**

Provide a testable function that accepts bound and unbound socket factories,
plus an Android adapter that returns a `() -> Socket` for a nullable/matched
`Network`. Preserve the existing fallback wording semantically but identify
the attempted transport as API v1 where the caller logs it.

- [x] **Step 4: Move the existing EPERM tests**

Remove `shouldFallbackToUnboundSocket` ownership from `PiConnection`; keep only
legacy display/key parsing tests there until the final deletion task. Update
the live API probe to use the shared helper where practical.

- [x] **Step 5: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.NetworkSocketFactoryTest" --tests "org.openauto.companion.net.PiConnectionParsingTest"
./gradlew :app:assembleDebugAndroidTest
```

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/NetworkSocketFactory.kt
git add app/src/test/java/org/openauto/companion/net/NetworkSocketFactoryTest.kt
git add app/src/main/java/org/openauto/companion/net/PiConnection.kt
git add app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt
git add app/src/androidTest/java/org/openauto/companion/net/api/ApiV1LiveValidationTest.kt
git commit -m "refactor: share wifi-bound API socket creation"
```

---

### Task 2: Add READY-Session Completion and System Subscription Helpers

**Files:**

- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiSessionClient.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiSessionClientTest.kt`
- Create: `app/src/main/java/org/openauto/companion/net/api/ApiRequests.kt`
- Create: `app/src/test/java/org/openauto/companion/net/api/ApiRequestsTest.kt`

**Purpose:** Give runtime code an explicit way to await disconnect and build a
correct nonzero-id `TOPIC_SYSTEM` subscription while continuously draining
server messages.

- [x] **Step 1: Write failing session lifecycle tests**

Cover:

- EOF after READY completes an `awaitClosed()`/completion seam
- reader exception preserves a failure cause
- `AuthReject` or `Error` after READY closes the transport and completes the
  session
- explicit client close cancels the reader and completes once
- sends after READY remain allowed and sends before READY remain rejected
- one `ApiSessionClient` remains single-use after close

- [x] **Step 2: Write failing request-builder tests**

`ApiRequests.subscribeSystem(requestId)` must:

- reject request id `0`
- create `SubscribeRequest` containing only `TOPIC_SYSTEM`
- preserve the caller's nonzero request id

- [x] **Step 3: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiSessionClientTest" --tests "org.openauto.companion.net.api.ApiRequestsTest"
```

- [x] **Step 4: Implement lifecycle and request seams**

Keep `incoming` available for READY messages, but make reader termination
observable without polling `Socket.isConnected`. Distinguish explicit
handshake rejection from transport close/EOF in the connect result so the
future retry loop can classify permanent versus transient failures without
string matching.

Rename `send` to `sendReadyMessage`, or retain a compatibility delegate, so
the ready-state guard accurately covers reports and subscription requests.

- [x] **Step 5: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiSessionClientTest" --tests "org.openauto.companion.net.api.ApiRequestsTest"
```

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/api/ApiSessionClient.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiSessionClientTest.kt
git add app/src/main/java/org/openauto/companion/net/api/ApiRequests.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiRequestsTest.kt
git commit -m "feat: expose API ready-session lifecycle"
```

---

### Task 3: Build the Conflated Report State and Serialized Publisher

**Files:**

- Create: `app/src/main/java/org/openauto/companion/net/api/ApiReportPublisher.kt`
- Create: `app/src/test/java/org/openauto/companion/net/api/ApiReportPublisherTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiReports.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiReportsTest.kt`

**Purpose:** Separate Android collection from wire delivery. Cache current
truth while disconnected, replay it after READY, and serialize sends without
letting GPS callbacks starve connectivity or grow an unbounded queue.

- [x] **Step 1: Write failing report-state tests**

Model current time provider, optional GPS snapshot, battery snapshot, and
connectivity snapshot. Cover:

- READY replay order is connectivity, time, battery, then GPS when present
- no GPS snapshot emits no fake `0,0` report
- every report uses request id `0`
- repeated identical battery/connectivity snapshots are suppressed within one
  READY session
- each report type is conflated independently while the writer is busy
- a new READY session replays the latest snapshots even when unchanged
- writer failure closes/cancels the ready publisher rather than continuing a
  half-alive queue

- [x] **Step 2: Extend builder validation tests**

Cover finite latitude/longitude/speed/bearing/accuracy/altitude, nonnegative
age, bearing range `[0, 360)`, battery `0..100`, inactive SOCKS port `0`, and
optional password/altitude presence. Do not change the proto.

- [x] **Step 3: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiReportPublisherTest" --tests "org.openauto.companion.net.api.ApiReportsTest"
```

- [x] **Step 4: Implement publisher and validation**

Use per-report conflation or an equivalent bounded design so one report type
cannot overwrite another. Accept a suspending send function for tests and for
`ApiSessionClient.sendReadyMessage` in production. Generate `TimeReport` from
the injected current-time/timezone providers at READY rather than replaying a
stale wall-clock timestamp.

- [x] **Step 5: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiReportPublisherTest" --tests "org.openauto.companion.net.api.ApiReportsTest"
```

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/api/ApiReportPublisher.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiReportPublisherTest.kt
git add app/src/main/java/org/openauto/companion/net/api/ApiReports.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiReportsTest.kt
git commit -m "feat: add reconnect-safe API report publisher"
```

---

### Task 4: Separate Cellular Upstream State From SOCKS5

**Files:**

- Create: `app/src/main/java/org/openauto/companion/net/CellularUpstreamMonitor.kt`
- Create: `app/src/test/java/org/openauto/companion/net/CellularUpstreamStateTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/Socks5Server.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/Socks5ServerTest.kt`

**Purpose:** Make `internet_available` truthful, share the selected cellular
network with SOCKS egress, release callbacks correctly, and implement the API's
password-only proxy convention.

- [x] **Step 1: Write failing upstream-state tests**

Extract a pure state seam and cover:

- cellular internet/validated availability selects an upstream
- capability loss clears it and notifies once
- losing a non-selected network does not clear the selected upstream
- stop/reset clears state idempotently

- [x] **Step 2: Write failing SOCKS authentication tests**

Extend loopback or parser-level coverage to prove:

- arbitrary usernames succeed with the correct password
- the previous hard-coded username is no longer required
- wrong passwords fail
- repeated `start()` does not double-bind
- `stop()` is idempotent
- outbound sockets ask the injected network provider for the current upstream

- [x] **Step 3: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.CellularUpstreamStateTest" --tests "org.openauto.companion.net.Socks5ServerTest"
```

- [x] **Step 4: Implement monitor and SOCKS changes**

`CellularUpstreamMonitor` owns and unregisters its Android network callback.
It exposes current usability, the selected `Network`, and a change callback or
Flow. `Socks5Server` no longer registers its own cellular request; it receives
a password and a provider for the selected egress network. Compare only the
password after parsing RFC 1929 credentials.

Keep private/link-local destination blocking, connection limits, and relay
behavior unchanged.

- [x] **Step 5: Verify GREEN and compile Android code**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.CellularUpstreamStateTest" --tests "org.openauto.companion.net.Socks5ServerTest"
./gradlew :app:assembleDebug
```

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/CellularUpstreamMonitor.kt
git add app/src/test/java/org/openauto/companion/net/CellularUpstreamStateTest.kt
git add app/src/main/java/org/openauto/companion/net/Socks5Server.kt
git add app/src/test/java/org/openauto/companion/net/Socks5ServerTest.kt
git commit -m "feat: track cellular upstream for SOCKS reports"
```

---

### Task 5: Implement the Pure v1 Runtime/Reconnect Loop

**Files:**

- Create: `app/src/main/java/org/openauto/companion/net/api/ApiRuntimeLoop.kt`
- Create: `app/src/test/java/org/openauto/companion/net/api/ApiRuntimeLoopTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt`

**Purpose:** Centralize session creation, known-client authentication,
server-identity checks, system subscription handling, and retry classification
outside the Android service.

- [x] **Step 1: Write failing runtime-loop tests with virtual time**

Use fake session/client factories and an injected backoff policy. Cover:

- a transient connect close retries TCP sessions in the configured sequence
- READY resets backoff
- only one client exists at a time
- explicit `AuthReject`/`Error` stops retry and reports re-pair required
- early handshake EOF remains transient and never selects another transport
- stored/nonblank `server_id` mismatch is permanent
- optional missing `server_id` is tolerated after authenticated READY
- READY starts one report publisher, sends one system subscription, and drains
  incoming messages
- `SubscribeResponse` rejection is nonfatal
- `SystemStatus` with both positive dimensions invokes persistence
- session close stops its publisher before reconnect begins
- cancellation/vehicle-generation change closes the active client and exits

- [x] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiRuntimeLoopTest"
```

- [x] **Step 3: Implement runtime interfaces and loop**

Define narrow injected seams for:

- client creation (the Android caller supplies only TCP `9810` clients)
- backoff/delay
- connection-state observation
- ready report publisher creation
- display-dimension persistence

Reuse/refactor `ApiPairingCredentialStore.persistSystemStatus`; do not add
Android dependencies to the runtime loop. Ensure all client and writer cleanup
occurs in `finally` paths.

- [x] **Step 4: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiRuntimeLoopTest" --tests "org.openauto.companion.net.api.ApiPairingCredentialStoreTest" --tests "org.openauto.companion.net.api.ApiSessionClientTest"
```

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/api/ApiRuntimeLoop.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiRuntimeLoopTest.kt
git add app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt
git commit -m "feat: add External API runtime reconnect loop"
```

---

### Task 6: Cut CompanionService and WifiMonitor Over to v1

**Files:**

- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`
- Modify: `app/src/main/java/org/openauto/companion/service/WifiMonitor.kt`
- Create: `app/src/main/java/org/openauto/companion/service/LocationReportMapper.kt`
- Create: `app/src/test/java/org/openauto/companion/service/LocationReportMapperTest.kt`
- Create: `app/src/main/java/org/openauto/companion/service/ProxyPassword.kt`
- Create: `app/src/test/java/org/openauto/companion/service/ProxyPasswordTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/StatusScreen.kt`

**Purpose:** Make the foreground service a v1-only runtime owner while
preserving theme HTTP, WebView, audio keep-alive, and foreground notification
behavior.

- [x] **Step 1: Write failing pure service-helper tests**

`LocationReportMapperTest` covers:

- mapping from a pure `LocationSample`/primitive snapshot so JVM tests do not
  depend on Android's stubbed `Location` implementation
- real latitude/longitude mapping
- missing speed/bearing/accuracy become `0`
- bearing normalization and finite checks
- monotonic age clamping
- optional altitude only when present and finite
- no location produces no GPS snapshot
- one-second throttle behavior

`ProxyPasswordTest` covers cryptographically sourced nonblank output with at
least 128 bits of randomness represented without leaking API credentials. Use
an injected byte source to make tests deterministic.

- [x] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.service.LocationReportMapperTest" --tests "org.openauto.companion.service.ProxyPasswordTest"
```

- [x] **Step 3: Change WifiMonitor service inputs**

Pass vehicle id/name/SSID, API client id, API secret hex, optional server id,
head-unit host, SOCKS preference, and audio preference. Refuse to start the
service for a vehicle without valid v1 credentials. Remove runtime dependence
on the legacy shared secret and API-mode selection.

- [x] **Step 4: Replace the service connection/push loop**

In `CompanionService`:

- replace scheduled `PiConnection` attempts with a structured coroutine scope
  and one `ApiRuntimeLoop` per vehicle generation
- create only `ApiTcpTransport(host, 9810, shared Wi-Fi socket factory)`
- cache sticky battery state and register/unregister battery changes
- register/unregister Android time/timezone change broadcasts
- request location updates at a one-second-capable cadence and map only real
  Android fixes into the pure `LocationSample`/mapper seam
- start the cellular upstream monitor for the service generation
- create one random proxy password and keep one SOCKS server across transient
  v1 reconnects
- feed state changes into `ApiReportPublisher`
- persist `SystemStatus` dimensions through `CompanionPrefs`
- leave the theme executor and HTTP transfer independent of API readiness
- retain audio keep-alive behavior

- [x] **Step 5: Make bridge toggles immediate**

Add a vehicle-scoped service command/static seam. The status UI callback must
persist the setting and notify the running service. Enable starts SOCKS and
updates the connectivity snapshot; disable stops it and sends inactive state
when READY. Ignore commands for a stale/different vehicle generation.

Expose UI state so bridge Active means API READY, usable cellular upstream,
and local SOCKS listener—not just the saved preference.

- [x] **Step 6: Compile and run focused tests**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.service.*" --tests "org.openauto.companion.net.api.ApiRuntimeLoopTest" --tests "org.openauto.companion.net.api.ApiReportPublisherTest" --tests "org.openauto.companion.net.Socks5ServerTest"
./gradlew :app:assembleDebug
```

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/org/openauto/companion/service/CompanionService.kt
git add app/src/main/java/org/openauto/companion/service/WifiMonitor.kt
git add app/src/main/java/org/openauto/companion/service/LocationReportMapper.kt
git add app/src/test/java/org/openauto/companion/service/LocationReportMapperTest.kt
git add app/src/main/java/org/openauto/companion/service/ProxyPassword.kt
git add app/src/test/java/org/openauto/companion/service/ProxyPasswordTest.kt
git add app/src/main/java/org/openauto/companion/ui/MainActivity.kt
git add app/src/main/java/org/openauto/companion/ui/StatusScreen.kt
git commit -m "feat: cut companion runtime over to API v1"
```

---

### Task 7: Add the Manual Live Pairing Backend

**Files:**

- Create: `app/src/main/java/org/openauto/companion/net/WifiNetworkResolver.kt`
- Create: `app/src/main/java/org/openauto/companion/net/api/ApiPairingCoordinator.kt`
- Create: `app/src/test/java/org/openauto/companion/net/api/ApiPairingCoordinatorTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt`

**Purpose:** Run pairing against the live server before any vehicle is saved.
Keep Android network discovery in an adapter and pairing/persistence decisions
in JVM-testable code.

- [x] **Step 1: Write failing coordinator tests**

Define a pairing draft containing SSID, display name, and host. Use fake
network/session factories. Cover:

- invalid PIN and blank SSID fail before transport creation
- duplicate SSID fails before contacting the server
- successful READY creates one vehicle with client id, 32-byte secret,
  `server_id`, host, and v1 mode during the transitional model
- the vehicle is saved only after the complete READY result
- missing paired credentials, blank granted client id, invalid secret length,
  auth rejection, early close, exception, and cancellation save nothing
- the client is always closed
- the transport request is TCP host/default `10.0.0.1`, port `9810`, and uses
  the resolved Wi-Fi socket factory

- [x] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiPairingCoordinatorTest" --tests "org.openauto.companion.net.api.ApiPairingCredentialStoreTest"
```

- [x] **Step 3: Implement resolver and coordinator**

`WifiNetworkResolver` inspects app-visible Wi-Fi networks, matches the entered
SSID, and returns the Android `Network`; it never process-binds. The
coordinator creates a network-bound `ApiTcpTransport`, uses
`ApiHandshake.pairing`, converts only a valid READY result into a vehicle, and
returns explicit success/failure/cancelled UI results.

Refactor `ApiPairingCredentialStore` as needed so pairing can create a new
vehicle rather than requiring a legacy draft vehicle to already exist. Keep
system-dimension persistence available for runtime.

- [x] **Step 4: Verify GREEN and compile Android adapter**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiPairingCoordinatorTest" --tests "org.openauto.companion.net.api.ApiPairingCredentialStoreTest"
./gradlew :app:assembleDebug
```

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/net/WifiNetworkResolver.kt
git add app/src/main/java/org/openauto/companion/net/api/ApiPairingCoordinator.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiPairingCoordinatorTest.kt
git add app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt
git add app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt
git commit -m "feat: add manual External API pairing backend"
```

---

### Task 8: Add the One-Time Purge and Wire Manual Pairing UI

**Files:**

- Create: `app/src/main/java/org/openauto/companion/data/VehicleStorageMigration.kt`
- Create: `app/src/test/java/org/openauto/companion/data/VehicleStorageMigrationTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/data/CompanionPrefs.kt`
- Modify: `app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/PairingScreen.kt`

**Purpose:** Delete saved legacy vehicles exactly once and make live manual API
pairing the only active way to create a vehicle.

- [x] **Step 1: Write failing pure migration tests**

Cover JSON/schema behavior:

- legacy-only records are removed
- valid v1 records survive with settings/display/server identity intact
- v1 mode with missing/blank client id is removed
- malformed, non-hex, or non-32-byte secret is removed
- malformed individual array entries do not discard valid siblings
- old single-vehicle keys are scheduled for removal, never converted
- a successful schema marker prevents later valid v1 vehicles from being
  purged
- when a preference commit is retried, runtime filtering still exposes only
  valid v1 vehicles

- [x] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleStorageMigrationTest" --tests "org.openauto.companion.data.VehicleSerializationTest"
```

- [x] **Step 3: Implement schema-gated purge**

Add a versioned migration in `CompanionPrefs`. Rewrite survivors, remove old
single-vehicle keys, and set the marker in one synchronous editor commit.
Make all getters/adders reject vehicles without valid v1 credentials even if
the cleanup write failed. Do not log deleted secrets.

- [x] **Step 4: Wire pairing UI asynchronously**

Replace local `deriveSecret(pin)` vehicle creation with
`ApiPairingCoordinator`. Add Idle/Pairing/Failed UI state, disable duplicate
submission while pairing, show the server/network failure without saving, and
restart monitoring only after success.

Remove the active QR route/button from `PairingScreen` and `MainActivity`.
`QrScanScreen`, parser, and CameraX/ML Kit dependencies may remain dormant for
future v1 QR work; they must have no active persistence callback.

- [x] **Step 5: Verify GREEN and UI compile**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleStorageMigrationTest" --tests "org.openauto.companion.data.VehicleSerializationTest" --tests "org.openauto.companion.net.api.ApiPairingCoordinatorTest"
./gradlew :app:assembleDebug
```

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/org/openauto/companion/data/VehicleStorageMigration.kt
git add app/src/test/java/org/openauto/companion/data/VehicleStorageMigrationTest.kt
git add app/src/main/java/org/openauto/companion/data/CompanionPrefs.kt
git add app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt
git add app/src/main/java/org/openauto/companion/ui/MainActivity.kt
git add app/src/main/java/org/openauto/companion/ui/PairingScreen.kt
git commit -m "feat: migrate saved vehicles to live API pairing"
```

---

### Task 9: Delete the Legacy Protocol and Simplify the Vehicle Model

**Files:**

- Delete: `app/src/main/java/org/openauto/companion/net/PiConnection.kt`
- Delete: `app/src/main/java/org/openauto/companion/net/Protocol.kt`
- Delete: `app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt`
- Delete: `app/src/test/java/org/openauto/companion/net/ProtocolTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/data/Vehicle.kt`
- Modify: `app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/data/CompanionPrefs.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/SettingsUrlBuilder.kt`
- Modify: any remaining compile-time callers found by the inventory scan

**Purpose:** Make exclusivity structural and remove transitional legacy data
fields once v1 runtime and pairing are active.

- [x] **Step 1: Update model tests first**

Change `VehicleSerializationTest` so every vehicle has required API client id
and 32-byte secret hex. Prove JSON round-trip for server id, endpoint,
preferences, and display size without `shared_secret` or `api_mode`.

Keep `VehicleStorageMigration` responsible for parsing pre-cutover raw JSON;
the final `Vehicle.fromJson` handles only valid post-cutover records.

- [x] **Step 2: Verify RED after removing legacy model expectations**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest"
```

- [x] **Step 3: Remove fields and protocol files**

Remove:

- `Vehicle.sharedSecret`
- `Vehicle.ApiMode` and `api_mode` serialization
- legacy `shared_secret` serialization from the active model
- legacy intent extras and any local legacy secret derivation
- `PiConnection`, `Protocol`, dead theme JSON builders, legacy display parsing,
  and their tests

Update the settings URL comment/example so production Java/Kotlin contains no
misleading `9876` reference. Do not delete `ThemeTransfer` or change its HTTP
behavior.

- [x] **Step 4: Run structural scans**

```bash
rg -n "PiConnection|Protocol\.buildStatus|buildThemeMessage|buildThemeDataChunk|ApiMode|9876" app/src/main/java
```

Expected: no output. References in historical docs/tests explicitly dedicated
to migration history are allowed outside `app/src/main/java`.

```bash
rg -n "shared_secret|api_mode" app/src/main/java
```

Expected: matches, if any, are confined to the one-time raw preference
migration/cleanup compatibility seam. They must not appear in `Vehicle`,
`WifiMonitor`, `CompanionService`, pairing, or API runtime code.

```bash
rg -n "ApiWebSocketTransport" app/src/main/java/org/openauto/companion/service app/src/main/java/org/openauto/companion/ui app/src/main/java/org/openauto/companion/net/api/ApiRuntimeLoop.kt
```

Expected: no runtime selection/reference. The standalone tested WebSocket
foundation file may remain.

- [x] **Step 5: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

- [x] **Step 6: Commit**

```bash
git add -A app/src/main app/src/test
git commit -m "refactor: remove legacy companion protocol"
```

---

### Task 10: Update Opt-In Live Transport Validation for the Cutover

**Files:**

- Modify: `app/src/androidTest/java/org/openauto/companion/net/api/ApiV1LiveValidationTest.kt`
- Create or modify: focused Android test helpers under
  `app/src/androidTest/java/org/openauto/companion/net/api/`

**Purpose:** Keep hardware transport diagnostics opt-in and aligned with the
shared network-bound socket seam without consuming a real pairing window or
creating an orphaned head-unit credential. Production manual pairing and
reports are validated in Task 12.

- [x] **Step 1: Preserve opt-in safety**

All live tests continue to skip unless `-e live_api_v1 true` is supplied. PIN,
client id, and secret are instrumentation arguments and are never printed.

- [x] **Step 2: Update cutover transport diagnostics**

Support:

- API TCP `9810` accepts a socket created by the shared Wi-Fi-bound helper
- legacy TCP `9876` is refused when an additional explicit
  `live_expect_legacy_refused=true` guard is supplied
- the existing invalid known-client diagnostic still accepts either a
  delivered terminal rejection or the documented early-close behavior
- a successful known-client result, if real credentials are deliberately
  supplied by Matthew, still asserts v1.1 `server_id`

Do not turn malformed-auth terminal-frame delivery into a blocker for normal
runtime work; preserve the explicit early-close diagnostic. Do not send
companion reports from instrumentation because a second diagnostic session
could take report ownership away from the production service and disturb the
SOCKS route under test.

- [x] **Step 3: Compile instrumentation APK**

```bash
./gradlew :app:assembleDebugAndroidTest
```

- [x] **Step 4: Run the opt-in guard without hardware mutation**

Run the existing instrumentation selector without `-e live_api_v1 true` and
confirm the live cases skip/return OK.

- [x] **Step 5: Commit**

```bash
git add app/src/androidTest/java/org/openauto/companion/net/api
git commit -m "test: extend live API cutover validation"
```

---

### Task 11: Documentation, Full Verification, and Bench-Ready Handoff

**Files:**

- Modify: `docs/project-vision.md`
- Modify: `docs/roadmap-current.md`
- Modify: `docs/plans/api-v1-migration.md` or archive/update its active status
  without rewriting completed historical evidence
- Modify: `docs/session-handoffs.md`
- Modify: this plan's checkboxes during execution

**Purpose:** Record the cutover accurately without claiming the hardware bench
has passed before Matthew runs it.

- [x] **Step 1: Run focused regression groups**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.*"
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.Socks5ServerTest" --tests "org.openauto.companion.data.*" --tests "org.openauto.companion.service.*"
```

- [x] **Step 2: Run the mandatory repository gate**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [x] **Step 3: Run static checks**

```bash
git diff --check
rg -n "PiConnection|Protocol\.buildStatus|ApiMode|9876" app/src/main/java
rg -n "shared_secret|api_mode" app/src/main/java
```

Expected: whitespace check passes; the connection/runtime legacy scan is
empty; old JSON key strings are confined to the one-time storage migration.

- [x] **Step 4: Update project memory**

- Change the vision constraint from legacy `9876` to External API v1 TCP
  `9810` plus HTTP web config `8080`.
- Update the roadmap item from foundation-beside-legacy to implementation
  complete/live-bench pending.
- Record that manual PIN pairing is active and QR v1 pairing remains future
  work, without promoting it unless Matthew requests it.
- Mark the old service-integration/retirement plan phases superseded by the
  approved Superpowers spec/plan while preserving foundation history.

- [x] **Step 5: Append the implementation handoff**

Include:

- sender inventory found
- mapping table used
- structural transport-exclusivity mechanism
- one-time vehicle deletion/manual pairing behavior
- reconnect and SOCKS ownership behavior
- tests/build results
- status `in progress` until live bench passes
- next steps: install, pair, run Prodigy §7, capture AA continuity

- [x] **Step 6: Commit**

```bash
git add docs/project-vision.md
git add docs/roadmap-current.md
git add docs/plans/api-v1-migration.md
git add docs/session-handoffs.md
git add docs/superpowers/specs/2026-07-13-api-v1-runtime-cutover-design.md
git add docs/superpowers/plans/2026-07-13-api-v1-runtime-cutover-implementation.md
git commit -m "docs: prepare API v1 cutover bench handoff"
```

---

### Task 12: Live Pixel/Prodigy Cutover Bench With Matthew

**Files:**

- Modify after results: `docs/session-handoffs.md`
- Modify after results: `docs/roadmap-current.md`
- Optionally record results in the current Prodigy runbook from the Prodigy
  session/repository, not from Companion code changes

**Prerequisites:**

- Current Prodigy `origin/dev` API inbound-state parity is deployed.
- Pixel is attached; use Windows ADB from WSL:
  `/mnt/e/Android/Sdk/platform-tools/adb.exe`.
- Matthew can open the External API pairing window and observe Pi widgets,
  SystemService state, and journal.

- [x] **Step 1: Install Companion**

```bash
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 39260DLJH000LX install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`.

- [x] **Step 2: Disable and prove legacy listener absence**

On the Pi, set `companion.enabled: false`, restart Prodigy, confirm
`ss -ltnp` has no `9876` listener, and confirm a phone-side connection attempt
is refused. Keep API TCP `9810` available.

- [x] **Step 3: Validate migration and manual pairing**

Launch Companion, confirm legacy vehicles were deleted, start Prodigy API
pairing, enter the displayed PIN, and confirm known-client reconnect succeeds
after the short pairing session closes.

- [x] **Step 4: Validate every report**

- GPS visible with bearing/accuracy and approximately 30-second staleness
- battery percentage and charging state track the Pixel
- time report produces the controlled clock-step/timezone journal evidence
- connectivity initially reflects real upstream/listener state

- [x] **Step 5: Validate bridge toggle and reconnect**

- enable bridge: listener active, Prodigy proxy row On, SystemService route up
- disable bridge: route down immediately without waiting for API disconnect
- re-enable, force API/network disconnect: Prodigy route/GPS/battery clear
- reconnect: one v1 client, one listener, route/reports replay once
- confirm no simultaneous legacy/v1 publisher and no duplicate SOCKS bind

- [x] **Step 6: Validate logs and Android Auto continuity**

Companion logs must show `api-v1-tcp` only, zero `9876` attempts, zero legacy
fallback, and no credentials. Keep AA media active through toggle/disconnect/
reconnect and record whether playback is uninterrupted.

- [x] **Step 7: Record final results**

If all rows pass:

- update roadmap status to cutover/live validation complete
- append a final handoff with exact commands/results and AA evidence
- rerun `./gradlew :app:testDebugUnitTest :app:assembleDebug` if any bench fix
  changed code
- commit the bench evidence

If a row fails, leave status `in progress`, record the exact observable and
logs, and use the repository's Superpowers debugging workflow. Do not restore
legacy fallback as a workaround.

**Live result (2026-07-13): PASS.** A live-only resolver regression found that
Android redacts the SSID on the Android Auto-owned network from synchronous
capability reads. Pairing now prefers an exact SSID match and safely falls back
to the Wi-Fi network with a direct route to the configured Prodigy host. The
new live resolver test failed before that fix and passed afterward. With
Prodigy `companion.enabled: false`, Pi `ss` showed only `9810`, and the guarded
Pixel suite passed API reachability, resolver selection, `9876` refusal, and
auth response checks. Manual PIN pairing and two saved-client reconnect paths
passed. IPC showed live GPS, battery/charging, and connectivity reports; a
controlled timezone mismatch was restored by `TimeReport`. Bridge off/on and
force-stop/relaunch produced immediate, verified SystemService route teardown
and replay. The same established AA `5277` socket remained present throughout
the Companion lifecycle checks.

---

## Final Definition of Done

- Manual live PIN pairing is the only active vehicle-creation path.
- Legacy saved vehicles are deleted once; valid v1 records survive.
- Companion runtime creates only network-bound API TCP `9810` sessions.
- Production source contains no legacy `9876` connection/publisher code.
- Time, GPS, battery, and connectivity reports follow the frozen v1 contract.
- SOCKS enable/disable is immediate, disconnect tears the route down, and
  reconnect re-applies it exactly once.
- GPS/battery owner state clears on disconnect and display dimensions arrive
  through the system subscription when available.
- Authentication/identity failures require re-pairing and never downgrade.
- Theme HTTP, web config, audio keep-alive, and AA stream continuity remain
  intact.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` passes.
- The live §7 bench passes with `companion.enabled: false`, zero legacy
  attempts, and recorded Pixel/Pi/AA evidence.
