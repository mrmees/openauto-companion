# External API v1 Runtime Cutover Design

## Status

Implemented and live-validated 2026-07-13.

## Context

OpenAuto Companion still uses the legacy newline-delimited JSON/HMAC client on
TCP `9876` for its foreground-service runtime reports. The External API v1.1
foundation already exists beside that path: vendored protobuf inputs, frame
codec, handshake state machine, TCP and WebSocket transports, session client,
report builders, and credential persistence helpers are implemented and
unit-tested.

The remaining cutover must move live pairing and runtime reporting to External
API v1 without dual-publishing. Theme and wallpaper installation already uses
the web-config HTTP endpoint and is not part of the socket migration.

The Prodigy-side prerequisites are complete on its current `origin/dev`:
External API TCP `9810` and WebSocket `9811`, v1.1 identity/time/display fields,
companion report consumers, GPS staleness, report-owner clearing, and proxy
route teardown on session disconnect. Live validation follows section 7 of
Prodigy's `2026-07-11-hfp-bench-runbook.md` with the legacy listener disabled.

This design supersedes the service-integration and retirement portions of
`docs/plans/api-v1-migration.md`. The earlier document remains the historical
design for the completed foundation slices.

## Vision Alignment

The cutover follows the priorities in `docs/project-vision.md`:

- reliability over novelty: one transport owner, explicit reconnect behavior,
  and no silent downgrade
- security-first defaults: authenticated v1 pairing and a proxy credential
  separated from the long-term API secret
- per-vehicle configuration: credentials and settings remain vehicle-scoped
- low-friction UX: manual PIN pairing is the first supported v1 entry point
- no-stream interruption: connection recovery uses app-owned sockets and does
  not rebind the process or manipulate Android Auto networking
- evidence before completion: unit/build verification precedes the live bench

## Locked Decisions

1. Delete the legacy runtime transport instead of retaining a debug fallback.
2. Delete saved legacy vehicle records during a one-time storage migration.
   Preserve only records that already contain valid External API credentials.
3. Ship manual API PIN pairing first. Hide the QR pairing entry point until a
   v1 QR payload is defined and emitted by Prodigy.
4. Use External API TCP on port `9810` for pairing and runtime. Do not
   automatically fall back to WebSocket or TCP `9876`.
5. Keep the local SOCKS5 listener alive across transient API reconnects while
   the vehicle Wi-Fi remains matched and internet sharing remains enabled.
6. Generate a random SOCKS5 password independent of the long-term API secret.

## Legacy Sender Inventory

### Active Runtime Messages

`PiConnection` currently sends two legacy message types:

- `hello`: authenticates with the legacy shared secret and advertises
  `time`, `gps`, `battery`, and `socks5` capabilities
- `status`: one combined message every five seconds containing:
  - phone time and IANA timezone
  - GPS latitude, longitude, speed, bearing, accuracy, and fix age
  - battery percentage and charging state
  - SOCKS5 active state and port

The active legacy status message does not carry explicit upstream-internet
availability or altitude. Those values must come from authoritative Android
state during the v1 cutover; they are not inferred legacy fields.

### Stale Legacy Code

`Protocol.buildThemeMessage`, `Protocol.buildThemeDataChunk`, legacy MAC
verification, and `PiConnection.readLine` remain after theme transfer moved to
HTTP. They have no production callers and should be deleted with the rest of
the legacy protocol implementation.

### Mapping

| Existing state | External API v1 message | Delivery policy |
| --- | --- | --- |
| time + timezone | `TimeReport` | after every READY and on phone time/timezone change |
| GPS fix | `GpsReport` | valid fixes only, approximately 1 Hz while moving |
| battery + charging | `BatteryReport` | after READY and whenever either value changes |
| upstream + SOCKS state | `ConnectivityReport` | after READY and on upstream/listener/toggle changes |
| legacy `hello` | v1 `ClientHello` authentication | once per connection attempt |

All four reports remain fire-and-forget with `request_id = 0`. Companion never
edits the vendored protobuf contract. Any newly discovered payload without a
v1 home stops implementation and is reported to the Prodigy project.

## Goals

- Pair a vehicle through the live External API pairing challenge and persist
  credentials only after `ServerHello` completes the exchange.
- Authenticate known vehicles on TCP `9810` through the matched Android Wi-Fi
  `Network`.
- Replace the combined legacy status loop with the four v1 reports.
- Detect disconnects promptly, tear down the client session, and reconnect
  with bounded backoff while the matched Wi-Fi remains available.
- Re-advertise current SOCKS5 state after every successful reconnect.
- Apply internet-sharing toggles to the running service immediately.
- Subscribe to `TOPIC_SYSTEM` so v1.1 display dimensions replace legacy
  `hello_ack` display metadata.
- Remove every production path that can connect or publish to TCP `9876`.
- Preserve HTTP theme installation, in-app web config, audio keep-alive, and
  Android Auto stream continuity.

## Non-Goals

- No protobuf additions or Prodigy source changes.
- No API WebSocket runtime selection or TCP-to-WebSocket fallback.
- No API v1 QR payload design or QR generation on the head unit.
- No automatic conversion of a legacy shared secret into v1 credentials; the
  v1 secret depends on the server-provided pairing salt and cannot be derived
  offline.
- No reverse-direction bridge work.
- No change to theme/wallpaper HTTP request semantics.
- No multi-head-unit ambiguity redesign beyond the existing SSID/local-id
  model; stable `server_id` is still checked when available.
- No migration of API credentials to a new Android keystore abstraction in
  this cutover; the existing credential-storage layer remains the baseline.

## Architecture

### 1. One-Time Vehicle Storage Migration

Saved vehicles become External-API-only. The post-cutover `Vehicle` model no
longer needs `sharedSecret` or `ApiMode`; every stored vehicle is implicitly an
External API v1 vehicle and has a non-blank `apiClientId` plus a valid 32-byte
`apiSecretHex`. `serverId` remains nullable because v1.1 fields are detected by
presence.

`CompanionPrefs` gains a vehicle-storage schema marker. On the first process
start with the new schema:

1. Read the existing `vehicles_json` array directly and tolerate malformed
   individual entries.
2. Retain only entries marked for External API v1 whose client id is non-blank
   and whose secret decodes to exactly 32 bytes.
3. Delete legacy-only and partially populated entries.
4. Remove the old single-vehicle `shared_secret`, `target_ssid`, and related
   migration keys without converting them into a vehicle.
5. Rewrite the retained list and schema marker in one synchronous preference
   transaction.

If the preference commit fails, the runtime parser still filters out records
without valid v1 credentials and the migration retries next process start.
The foreground service must therefore never receive a legacy vehicle even
during a failed cleanup write. Once the marker is committed, later launches do
not purge vehicles created by the new pairing flow.

When the filtered vehicle list is empty, the app opens the manual pairing
screen. There is no automatic connection attempt and no legacy retry message.

### 2. Manual Live Pairing

Manual pairing remains a draft until the server accepts it. The user:

1. connects the phone to the Prodigy/Android Auto Wi-Fi network
2. opens External API settings on the head unit and starts the pairing window
3. enters or confirms SSID, display name, and the six-digit API PIN in
   Companion
4. submits once while the UI shows an in-progress state

A small Android-facing pairing coordinator resolves the currently connected
Wi-Fi `Network` matching the submitted SSID. It creates an `ApiTcpTransport`
whose socket comes from that network, then runs `ApiHandshake.pairing` through
`ApiSessionClient`.

On `ConnectResult.Ready`, the coordinator requires a non-blank granted client
id and a 32-byte derived secret. It creates the final `Vehicle`, stores the
optional `ServerHello.server_id`, closes the short-lived pairing session, and
only then adds the vehicle to `CompanionPrefs`. Restarting Wi-Fi monitoring
causes the service to reconnect immediately as a known client if the network
is still present.

Pairing failure, early close, cancellation, duplicate SSID, or invalid result
leaves no partial vehicle record. Pairing is user-driven and is never retried
automatically with a PIN.

The QR button is hidden from `PairingScreen`. Existing QR parser/scanner code
may remain dormant to avoid unrelated dependency churn, but it cannot create a
vehicle or feed legacy credentials into the active pairing flow.

### 3. Wi-Fi-Bound TCP Transport

TCP `9810` is the sole runtime API transport. Both pairing and known-client
sessions use the matched `Network.socketFactory` so the API socket reaches
`10.0.0.1` even when Android's default route remains cellular.

The current EPERM-only bound-socket fallback is extracted from
`PiConnection` into a transport-neutral helper before `PiConnection` is
deleted. If Android refuses creation of a Wi-Fi-bound socket with EPERM, the
helper may try a normal unbound TCP socket and must log that it is still an API
v1 TCP attempt. Other socket-factory failures propagate normally.

There is no fallback to WebSocket, no fallback to port `9876`, and no process
network binding. Retrying always creates another TCP `9810` session.

The target host is the vehicle's validated head-unit/settings host when
present, otherwise `10.0.0.1`. The API port is fixed at `9810` for this slice;
the stored web-config port remains unrelated.

### 4. Runtime Session Ownership

`CompanionService` owns exactly one vehicle runtime generation. A generation
contains:

- the selected vehicle identity and v1 credentials
- one reconnect job
- at most one `ApiSessionClient`
- one serialized report writer for the READY session
- collectors/caches for location, battery, time, and connectivity
- the optional local SOCKS5 server and its random password

Switching vehicles or losing the matched Wi-Fi cancels the entire previous
generation before a new one starts. Callbacks and queued sends carry or check
the generation identity so stale work cannot publish into the replacement
session.

For a known-client attempt, the service decodes the 32-byte secret, builds
`ApiHandshake.knownClient`, connects, and waits for READY. A successful READY:

1. validates `server_id` against the stored value when both are present
2. marks the service connected
3. starts one writer bound to that client
4. sends the latest initial report snapshot
5. subscribes to `TOPIC_SYSTEM`
6. continuously drains incoming messages until the session closes

`ApiSessionClient` gains an explicit ready-session completion signal or
suspending `awaitClosed` seam. The service must not infer health from
`Socket.isConnected`; EOF, transport failure, terminal API frames, or writer
failure all end the generation's current session and enter reconnect handling.

All post-READY sends pass through one bounded/conflated writer so transport
writes cannot race and rapid Android callbacks cannot create an unbounded
queue. The current values are retained outside the writer and replayed after
the next READY.

### 5. Reconnect Policy

Transient failures reconnect only while the same vehicle Wi-Fi generation is
active. Backoff is injected/testable and follows a short bounded sequence such
as 1, 2, 5, 10, then 30 seconds maximum, with production jitter. A successful
READY resets the sequence.

Explicit `AuthReject`/terminal credential errors and a non-blank `server_id`
mismatch are permanent for that vehicle session. They stop automatic retries,
surface a re-pair-required state, and never attempt a legacy transport. An
early close without a delivered terminal frame remains a transient transport
failure because the known Prodigy terminal-frame delivery issue can otherwise
misclassify a healthy credential path.

When a READY session drops, Prodigy's report-owner handling clears GPS and
battery and tears down an owned proxy route. Companion marks itself
disconnected but keeps its latest Android snapshots for replay. If internet
sharing is enabled, the local SOCKS5 listener remains available during the
short reconnect window; it is not reachable from the head unit until the new
session reports an active route.

### 6. Report Collection and Delivery

Collectors update cached truth regardless of API connection state. The writer
sends only while READY.

#### ConnectivityReport

Cellular upstream selection moves into a small service-lifetime monitor rather
than living privately inside `Socks5Server`. It observes a cellular network
with internet capability, tracks whether it is currently usable, exposes the
selected `Network` for SOCKS egress, and notifies the report cache on changes.
`Socks5Server` asks this monitor for the network when opening an outbound
socket.

The report values are:

- `internet_available`: whether the selected cellular upstream is usable
- `socks5_active`: whether the local listener is bound and running
- `socks5_port`: `1080` only while the listener is active, otherwise `0`
- `socks5_password`: present only while the listener is active

Prodigy activates its route only when upstream and SOCKS are both available.
Companion sends connectivity first after READY so bridge restoration is not
delayed by time synchronization or optional subscriptions.

Enabling internet sharing starts the listener even if the API is temporarily
disconnected, then reports it on the next READY. Disabling it stops the
listener and immediately enqueues an inactive connectivity report when READY.
Closing the API session remains the authoritative fallback teardown on the
head unit if that final report cannot be delivered.

`Socks5Server` validates only the password and accepts any syntactically valid
username, matching the API's password-only route configuration. Each service
generation creates a cryptographically random proxy password that is not
derived from or equal to the API secret. The cellular network callback is
unregistered when its monitor stops.

The status-screen switch both persists the per-vehicle preference and calls a
vehicle-checked service command so the running bridge changes immediately.
The displayed bridge-active state means READY plus usable upstream plus local
listener, rather than merely "the preference is enabled."

#### TimeReport

Send `System.currentTimeMillis()` and the current non-blank
`TimeZone.getDefault().id` immediately after connectivity on each READY. Also
send after Android time or timezone change broadcasts. Do not maintain the
legacy five-second clock-step loop.

#### BatteryReport

Read the sticky `ACTION_BATTERY_CHANGED` value for the initial snapshot and
keep a receiver for level/charging changes. Clamp/validate the percentage
before building the report and suppress identical consecutive snapshots.

#### GpsReport

Request location updates at a cadence capable of approximately 1 Hz while
moving. Send real fixes only; never manufacture a `0,0` fix when Android has no
location. Map:

- latitude and longitude directly
- speed when present, otherwise `0`
- bearing when present, normalized to `[0, 360)`, otherwise `0`
- horizontal accuracy when present, otherwise `0`
- age from monotonic elapsed time, clamped non-negative to the builder's
  supported unsigned range
- altitude only when `Location.hasAltitude()` and the value is finite

Throttle duplicate/high-frequency callbacks to at most approximately 1 Hz.
Prodigy combines the reported age with time since receipt to apply its 30
second staleness rule.

### 7. System Subscription and Display Dimensions

After the initial reports, the READY writer sends a nonzero-request-id
`SubscribeRequest` for `TOPIC_SYSTEM`. The continuous reader validates the
subscription result, accepts `SystemStatus` snapshots, and persists positive
display width/height when both optional fields are present.

Unavailable system subscription or absent dimensions do not disconnect the
report session. Theme Builder continues using persisted dimensions or its
existing UI default. The reader still drains all frames so server backpressure
cannot build up.

### 8. Legacy Removal and Exclusivity

The implementation deletes:

- `PiConnection`
- legacy `Protocol` and its tests
- legacy session key, sequence, combined status loop, and `9876` intent extras
- service selection based on `Vehicle.ApiMode`
- active UI paths that locally derive/store the legacy PIN secret

`ThemeTransfer` remains HTTP-only and independent of the API session. In-app
web config continues to use the matched Wi-Fi network. Neither feature is a
reason to retain a legacy socket.

Transport exclusivity is therefore structural: a production build has one
runtime API transport family, configured for TCP `9810`, and contains no code
that can create a connection to `9876`.

## Data Flows

### First Pairing

1. User starts the Prodigy API pairing window and enters its PIN.
2. Companion resolves the matching Wi-Fi `Network`.
3. TCP `9810` sends pairing `ClientHello`.
4. Companion derives the secret from `PairingChallenge.salt`, proves the PIN,
   and receives `ServerHello.granted_client_id`.
5. Companion constructs and persists the v1 vehicle atomically.
6. The pairing session closes and Wi-Fi monitoring starts/restarts.
7. The foreground service reconnects with known-client credentials.

### Known-Client Runtime

1. `WifiMonitor` matches a stored vehicle and starts the service with v1
   identity, endpoint, credentials, and per-vehicle settings.
2. The service connects and authenticates on network-bound TCP `9810`.
3. READY starts the serialized writer and continuous reader.
4. Companion sends connectivity, time, battery, and the latest real GPS fix.
5. Companion subscribes to system status.
6. Android state changes enqueue only the corresponding current report.

### Disconnect and Recovery

1. Reader EOF, terminal frame, or send failure closes the current v1 client.
2. Prodigy clears report-owned state and its proxy route.
3. Companion marks the API disconnected but preserves cached phone state and,
   when enabled, the local SOCKS listener.
4. A transient failure waits for the next backoff interval.
5. A new known-client session reaches READY and replays current reports,
   restoring the proxy route without legacy traffic.

### Internet-Sharing Toggle

1. UI persists the new setting.
2. UI sends a vehicle-scoped command to the running service.
3. Enable binds SOCKS with a random password; disable stops it.
4. Connectivity cache changes and the READY writer sends the new report.
5. If disconnected, the cached state is sent immediately after reconnect.

## Error Handling and User State

- Pairing-network unavailable: remain on the form with a Wi-Fi-specific error.
- Pairing window closed, wrong PIN, terminal rejection, or early close: do not
  save; show a retryable pairing error.
- Duplicate saved SSID: require unpairing the existing entry before starting a
  second pairing, avoiding an orphaned head-unit credential.
- Invalid stored secret: exclude it during migration/runtime loading and show
  pairing when no valid vehicles remain.
- Known-client auth rejection: stop retries and show re-pair required.
- `server_id` mismatch: close, stop retries, and show identity mismatch/re-pair
  required. If either side omits the optional field, continue using successful
  authenticated-session proof and log the absence.
- Transient TCP/EOF failure: reconnect with backoff, v1 only.
- Report validation/build failure: log without secrets, retain the session,
  and wait for corrected Android state.
- Writer or reader transport failure: close the whole client so reconnect
  cannot leave half-alive send/read paths.
- System subscription rejection: log and continue companion reports.
- SOCKS bind failure: report `socks5_active=false`, reflect inactive UI state,
  and keep the API session alive.
- Cellular upstream loss: send `internet_available=false`; Prodigy tears down
  the unusable route while the local listener may remain bound.

Logs identify transport (`api-v1-tcp`), vehicle id, session lifecycle, report
type, and retry delay. They never print PINs, API secrets, proofs, proxy
passwords, or complete serialized reports containing credentials.

## Testing

### Pure/JVM Coverage

- Vehicle storage migration:
  - legacy-only records are deleted
  - valid v1 records survive
  - malformed/partial v1 records are deleted
  - old single-vehicle keys are removed without conversion
  - the schema marker prevents a later valid pairing from being purged
- Pairing coordinator:
  - successful pairing creates one fully credentialed vehicle after READY
  - failure, cancellation, early close, and malformed READY save nothing
  - duplicate SSID is rejected before opening a pairing session
  - transport factory receives the resolved Wi-Fi network and TCP `9810`
- Runtime session:
  - reports cannot send before READY
  - initial report order starts with connectivity and includes time/battery/GPS
    when available
  - all report envelopes use request id `0`
  - system subscription uses a nonzero request id
  - disconnect cancels the old writer and reconnect replays current state
  - stale generation callbacks cannot send through a replacement session
  - permanent auth/identity failures do not retry or select another transport
- Report mapping:
  - no-location state emits no fake GPS report
  - altitude presence, bearing, accuracy, age, and approximately 1 Hz throttle
  - battery and connectivity deduplication
  - time/timezone replay after READY
- SOCKS/connectivity:
  - any username plus the correct password authenticates
  - wrong password fails
  - enable/disable changes the listener and report immediately
  - upstream loss changes connectivity without stopping the API session
  - reconnect does not double-bind the listener and does re-advertise it
  - network callbacks are released during teardown
- Existing HTTP theme, WebView network binding, audio keep-alive, API codec,
  handshake, and transport tests remain green.

### Structural Checks

- Production Java/Kotlin contains no `PiConnection`, legacy status builder, or
  TCP `9876` connection path.
- No legacy shared-secret or `apiMode` extra reaches `CompanionService`.
- The QR scanner has no active vehicle-persistence callback.
- TCP and WebSocket foundation tests may remain, but runtime transport
  selection tests assert TCP `9810` only.

### Repository Gate

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

### Live Bench

With Matthew and the Pixel connected to the head-unit AP:

1. Deploy Prodigy with the current API inbound-state parity work.
2. Set `companion.enabled: false`, restart Prodigy, and prove no listener is
   bound to `9876`.
3. Install the Companion debug APK; confirm the one-time migration removes
   legacy vehicles and opens manual pairing.
4. Start the Prodigy External API pairing window and pair with its PIN.
5. Verify Companion authenticates on v1 TCP only with zero `9876` attempts,
   zero legacy fallback retries, and no secrets in logs.
6. Verify GPS appears and becomes stale after approximately 30 seconds without
   fresh fixes.
7. Verify battery percentage and charging state track the Pixel.
8. Enable internet sharing and verify SOCKS route up; disable it and verify
   immediate route down.
9. Re-enable sharing, disconnect the API session/network, and verify route,
   GPS, and battery owner state clear on Prodigy.
10. Reconnect and verify the route and latest reports are re-applied once.
11. Verify the controlled clock-step/timezone journal entry.
12. Keep Android Auto media active through toggle and reconnect exercises and
    record whether stream continuity is preserved.

## Implementation Boundaries

Expected production areas to change:

- `data/Vehicle.kt` and `data/CompanionPrefs.kt`
- manual pairing UI/state in `ui/MainActivity.kt` and `ui/PairingScreen.kt`
- a Wi-Fi network resolver/pairing coordinator
- `service/WifiMonitor.kt` and `service/CompanionService.kt`
- `net/Socks5Server.kt` plus a cellular-upstream monitor
- existing `net/api` session, request, and runtime orchestration helpers
- focused unit and instrumentation tests
- roadmap and session handoff documentation after implementation

Expected deletions or inactive paths:

- `net/PiConnection.kt`
- `net/Protocol.kt`
- their legacy tests and service call sites
- QR entry from the active pairing screen

Intentionally unchanged:

- vendored `app/src/main/proto/api/*.proto`
- `ThemeTransfer` HTTP contract
- web-config URL and WebView behavior
- Prodigy repository source

## Documentation During Implementation

- Update `docs/roadmap-current.md` from "foundation beside legacy" to runtime
  cutover status and live-bench readiness.
- Update `docs/project-vision.md` constraints so they no longer describe TCP
  `9876` as the Companion runtime target after the cutover is verified.
- Update or archive the now-obsolete migration-plan phases without rewriting
  completed historical evidence.
- Append `docs/session-handoffs.md` with changes, rationale, status, next
  steps, verification results, and Android Auto continuity evidence.

## Acceptance Criteria

- A fresh or migrated install can create a vehicle only through successful
  live API v1 PIN pairing.
- Legacy and malformed saved vehicles are deleted once and cannot start the
  service.
- Manual pairing persists client id, 32-byte secret, and optional server id
  only after READY.
- Runtime uses network-bound TCP `9810`; no code can connect to `9876` and no
  transport fallback can dual-publish.
- Time, GPS, battery, and connectivity reports follow the frozen contract and
  required delivery policies.
- Internet-sharing toggle changes the running SOCKS bridge and v1 report
  immediately.
- An active bridge is torn down on API disconnect and re-applied after
  reconnect without restarting or double-binding the phone listener.
- System subscription supplies display dimensions when available.
- Permanent authentication/identity failures require re-pairing and never
  fall back to legacy behavior.
- Unit tests and `./gradlew :app:testDebugUnitTest :app:assembleDebug` pass.
- Live bench with Prodigy legacy listener disabled passes every payload,
  disconnect, reconnect, logging, and AA continuity check.
