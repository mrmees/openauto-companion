# External API v1 Migration Plan

## Goal

Begin migrating OpenAuto Companion from the legacy JSON/HMAC socket on TCP
`9876` to OpenAuto Prodigy External API v1 while preserving the existing app,
foreground service, collectors, SOCKS5 bridge, multi-vehicle model, settings
link, and theme builder.

This phase builds only the additive foundation:

- vendor the frozen v1 protos from `../openauto-prodigy/proto/api/`
- wire protobuf code generation into the Android build
- add pure JVM-testable v1 framing, envelope, crypto, and handshake state
  classes under `app/src/main/java/org/openauto/companion/net/api/`
- keep all runtime service traffic on legacy `9876` until a later integration
  phase

## Alignment With Project Vision

The project vision prioritizes reliable reconnect behavior, authenticated
head-unit communication, per-vehicle configuration, and no Android Auto stream
interruption. This phase supports that direction by adding v1 beside the
working legacy path. It avoids a live service cutover before the first Pi
live-client validation and keeps theme/wallpaper transfer on the only channel
that currently works.

## Frozen Contract Source

Source of truth:

`../openauto-prodigy/proto/api/*.proto`

Vendored copy:

`app/src/main/proto/api/*.proto`

Rules:

- Treat vendored protos as generated-input copies, not local design files.
- Never edit field numbers, names, package, or semantics in this repo.
- Target the deployed additive v1.1 contract.
- Use field presence for `SystemStatus` display dimensions,
  `TimeReport.timezone_id`, and `ServerHello.server_id`.
- Feature detection must use field presence, not minor version comparison.

## Current Legacy Inventory

### `app/src/main/java/org/openauto/companion/net/PiConnection.kt`

- Connects to `10.0.0.1:9876` by default.
- Uses optional Android `Network.socketFactory` binding with unbound fallback
  on EPERM.
- Reads a JSON `challenge`, sends legacy `hello`, reads `hello_ack`.
- Stores a legacy session key from `hello_ack.session_key`.
- Parses display dimensions from `hello_ack.display.width/height`.
- Exposes `sendStatus(JSONObject)` and `readLine()` for both status reports
  and theme transfer.

Target mapping:

- Connection mechanics move to a future v1 client with configurable host/port:
  TCP `9810` length-prefixed protobuf and WebSocket `9811` binary protobuf.
- Display dimensions are available from `SystemStatus.display_width` and
  `display_height` when the server supplies them. Keep legacy display parsing
  only as a runtime fallback until service cutover is complete.
- Legacy `PiConnection` stays for service/runtime traffic in this phase.

### `app/src/main/java/org/openauto/companion/net/Protocol.kt`

- Computes legacy HMAC as lowercase hex string.
- Builds `hello`, combined `status`, `theme`, and `theme_data` JSON messages.
- Verifies legacy JSON MACs.

Target mapping:

- v1 crypto uses raw bytes:
  `secret = SHA256(utf8(pin) || salt)`,
  proof = `HMAC_SHA256(key = secret, data = server_nonce)`.
- v1 reports are separate protobuf messages:
  `TimeReport`, `GpsReport`, `BatteryReport`, `ConnectivityReport`, each
  sent as `ApiMessage` with `request_id = 0`.
- `TimeReport.timezone_id` should be sent whenever the phone can provide a
  non-blank IANA timezone id.
- Legacy theme JSON/chunk messages remain on `9876` until the head-unit
  web-config HTTP upload endpoint is implemented.

### `app/src/main/java/org/openauto/companion/service/CompanionService.kt`

- Starts `PiConnection(sharedSecret = secret)` when a paired vehicle SSID is
  detected.
- Pushes one combined legacy status every 5 seconds.
- Starts phone SOCKS5 on port `1080` when enabled.
- Derives current SOCKS credentials as username `oap` and password from the
  first 8 chars of the stored legacy secret.
- Sends theme/wallpaper through `ThemeTransfer` over the active `PiConnection`.

Target mapping:

- Future v1 integration should send:
  - `TimeReport` once shortly after READY and periodically if needed
  - `GpsReport` at about 1 Hz while moving
  - `BatteryReport` on change
  - `ConnectivityReport` on internet/SOCKS state changes
- SOCKS5 v1 convention is password-only. The app's SOCKS server should accept
  any username and validate only the reported password when the service cutover
  phase updates the bridge behavior.
- `AuthReject` and `Error` are terminal connection frames; future service
  integration must close cleanly and surface the reason.
- Backpressure means future v1 clients must keep reading and treat disconnects
  as normal reconnect events.

### `app/src/main/java/org/openauto/companion/net/ThemeTransfer.kt`

- Chunks wallpaper data and sends legacy `theme` / `theme_data`.
- Waits for legacy `theme_ack`.

Target mapping:

- No v1 equivalent by design.
- Theme/wallpaper moves to a future web-config HTTP endpoint on
  `http://<host>:8080`.
- The endpoint does not exist yet, so legacy `9876` remains the theme transfer
  channel and is a retirement gate.

### Pairing, Settings, Identity

- `PairingScreen`, `QrScanScreen`, and `PairingUriParser` collect SSID, PIN,
  optional `vehicle_id`, optional host, and optional port.
- `MainActivity.deriveSecret()` stores a legacy SHA-256 hex secret immediately.
- `Vehicle.sharedSecret` stores that legacy secret.
- `SettingsUrlBuilder` constructs `http://<host>:8080` and ignores companion
  socket port values.
- `WifiMonitor` matches by SSID and starts the service with per-vehicle
  settings.

Target mapping:

- v1 pairing cannot persist a v1 secret until the server sends
  `PairingChallenge.salt`. Future UI/service integration must drive the live
  pairing exchange, then persist `client_id` plus 32-byte secret per vehicle.
- `ServerHello.server_id` is the stable head-unit identity for future
  vehicle-key migration. This slice persists it without changing existing
  local vehicle matching.
- Settings URL stays HTTP convention-based. No API field is planned.

## Target Architecture

Create `org.openauto.companion.net.api` as a pure protocol package:

- `ApiCrypto`
  - derive pairing secret from PIN and server salt
  - compute HMAC-SHA256 proof bytes
  - decode/encode hex for persisted 32-byte secrets when storage integration
    is added later

- `ApiFrameCodec`
  - serialize and parse one `ApiMessage`
  - encode/decode TCP frames with 4-byte big-endian length prefix
  - enforce the 256 KiB max frame size
  - WebSocket uses the same serialized `ApiMessage` bytes with no extra
    envelope; this phase only needs the byte codec

- `ApiHandshake`
  - model the client-side v1 session state machine without sockets
  - build first `ClientHello` for known-client auth or first-time pairing
  - answer `AuthRequired` using the exact server nonce
  - answer `PairingChallenge` using the exact server nonce and salt
  - finish on `ServerHello`
  - surface `AuthReject` and `Error` as terminal failures
  - reject any attempt to send reports before READY

- `ApiReports`
  - pure builders for `TimeReport`, `GpsReport`, `BatteryReport`, and
    `ConnectivityReport`
  - all report envelopes use `request_id = 0`
  - include `TimeReport.timezone_id` when a non-blank IANA timezone id is
    available

Transport adapters for TCP and WebSocket are a later phase. The first slice
keeps networking out of these classes so they remain unit-testable on the JVM.

## Phases

### Phase 1: Plan, Vendoring, Codegen, Pure Protocol Slice

Deliver now:

1. Add this plan.
2. Vendor `../openauto-prodigy/proto/api/*.proto` into
   `app/src/main/proto/api/` with a README naming the source of truth.
3. Add protobuf Gradle plugin/runtime and generate lite Java classes.
4. Add TDD-covered pure classes for crypto, framing, handshake, and report
   builders.
5. Keep existing app runtime behavior unchanged.
6. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
7. Append a session handoff.

### Phase 2: v1 Transport Adapters

Add `ApiTcpTransport` and `ApiWebSocketTransport` behind a small interface:

- host configurable, default `10.0.0.1`
- TCP default port `9810`
- WebSocket default port `9811`
- keep reading continuously
- close on terminal frames
- reconnect with backoff
- expose connection state and fatal reason

This phase should use fake local TCP/WS servers in tests where practical.

### Phase 3: Pairing and Credential Storage Integration

Add per-vehicle v1 credential storage alongside legacy `sharedSecret`:

- `client_id`
- 32-byte secret, persisted as hex
- selected API mode, defaulting to legacy until live Pi validation passes

Current slice:

- `Vehicle` persists optional `api_client_id`, `api_secret_hex`, and
  `api_mode` fields while older JSON defaults to legacy mode.
- `ApiPairingCredentialStore` can consume a successful
  `ApiSessionClient.ConnectResult.Ready` pairing result and persist the
  granted client id plus 32-byte secret hex for a matched vehicle.
- Legacy `sharedSecret` remains present and the foreground service is not wired
  to v1 reports.

Manual and QR pairing should be updated to perform the live v1 pairing flow
only when v1 mode is selected and the Pi API is available.

### Phase 4: Service Report Integration

Wire collectors into v1 report senders:

- `TimeReport` shortly after READY
- `GpsReport` cadence aligned with movement and location updates
- `BatteryReport` on battery change
- `ConnectivityReport` on internet/SOCKS state changes

Keep legacy status push available during transition.

### Phase 5: Retirement Gates

Do not remove legacy `9876` until all gates are met:

- Pi live-client validation over the real AP and LAN host/port succeeds.
- Web-config HTTP theme/wallpaper upload endpoint exists and is integrated.
- Display dimensions come from `SystemStatus.display_width` and
  `display_height` when supplied, with legacy display parsing retained only as
  a runtime fallback through service cutover.
- `ServerHello.server_id` remains persisted for future vehicle-key migration
  without changing existing local vehicle matching in this slice.

## Test Strategy

Phase 1 tests are pure JVM unit tests:

- `ApiCryptoTest`
  - pairing secret uses `SHA256(pin_utf8 || salt)`
  - auth proof uses server-provided nonce
  - hex parsing validates 32-byte secrets

- `ApiFrameCodecTest`
  - serialized `ApiMessage` round-trips
  - TCP frame uses 4-byte big-endian length prefix
  - oversized frames are rejected before parse

- `ApiHandshakeTest`
  - known-client flow:
    `ClientHello(client_id)` -> `AuthRequired(nonce)` ->
    `AuthResponse(client_id, proof)` -> `ServerHello` -> READY
  - pairing flow:
    `ClientHello(pairing_request=true)` -> `PairingChallenge(nonce, salt)` ->
    `PairingResponse(proof)` -> `ServerHello(granted_client_id)` -> READY
  - `AuthReject` is terminal
  - `Error` is terminal
  - reports before READY are rejected by the state machine

- `ApiReportsTest`
  - report envelopes use `request_id = 0`
  - connectivity report includes password when supplied
  - time report includes `timezone_id` when a non-blank IANA timezone id is
    supplied

Repo gate:

`./gradlew :app:testDebugUnitTest :app:assembleDebug`

## Decisions From Gap Inventory

1. Theme/wallpaper transfer moves to future web-config HTTP, not External API.
   Keep legacy `ThemeTransfer.kt` until that endpoint ships.
2. Display dimensions are available from `SystemStatus.display_width` and
   `display_height` when the server supplies them. Keep legacy display parsing
   only as a runtime fallback until service cutover is complete.
3. `TimeReport.timezone_id` should be sent whenever the phone can provide a
   non-blank IANA timezone id.
4. `ServerHello.server_id` is the stable head-unit identity for future
   vehicle-key migration. This slice persists it without changing existing
   local vehicle matching.
5. SOCKS5 username is password-only by convention. Future v1 integration
   should validate only password and accept any username.
6. Head-unit proxy-route status is wishlisted, not v1. Companion UI remains
   based on local proxy state.
7. Reverse bridge remains out of scope.
8. Settings URL advertisement is rejected. Continue constructing
   `http://<host>:8080`.

## Flagged Follow-Ups

- First live companion connect to the Pi is uncharted. Plan a co-debug session
  over the Pi AP and LAN IP after the pure client exists.
- Decide where the v1/legacy mode toggle lives before service integration:
  global developer setting is simplest for live validation; per-vehicle mode is
  better for transition after validation.
- Define HTTP theme upload client only after the head-unit endpoint contract is
  delivered.
