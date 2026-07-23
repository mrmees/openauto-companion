# Session Handoffs

## Purpose

This file preserves continuity across work sessions.

## When Entries Are Required

Add an entry for every behavior-changing session.

Behavior-changing work includes:
- New or modified runtime behavior
- Changes to pairing, connection, networking, or status UX logic
- Any update that could alter user-facing behavior or protocol/runtime semantics

Non-behavior work (formatting, docs-only edits, no-op refactors) does not require an entry unless risk is meaningful.

## Entry Template

```markdown
## YYYY-MM-DD HH:MM (local)

- What changed:
- Why:
- Status: done | in progress | blocked
- Dependency decision:
  - Companion-only: Yes/No
  - If No, reference `Blocked by Head Unit` entry
- Wishlist promotion:
  - Source item: <title or n/a>
  - Promotion result: Promoted / Not promoted
- Next steps:
  - 1)
  - 2)
  - 3)
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS/FAIL
  - Additional checks (if any):
  - AA stream continuity: preserved / not preserved / not tested
```

---

## 2026-07-22 20:44 (local)

- What changed:
  - Replaced six-digit pairing with the coordinated versioned 24-character
    Base32 credential contract, including ASCII-only normalization, QR
    `code=` parsing, challenge/capability negotiation, and shared crypto vectors.
  - Added credential-generation persistence and storage version 2, which
    retires legacy vehicles instead of attempting an insecure compatibility
    path.
  - Updated QR/manual pairing UI and retained the previously live-validated
    per-attempt Wi-Fi network recovery change as an explicit branch commit.
- Why:
  - A captured six-digit pairing transcript was cheaply enumerable offline;
    Prodigy and Companion needed one coordinated, fail-closed credential
    generation upgrade.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: coordinated Prodigy secure
    pairing fields, generation enforcement, and deployment in its API/core
    asynchronous lifecycle wave.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Publish this branch as a separate draft PR to `main`.
  - 2) Review and merge it together with the matching Prodigy draft PR.
  - 3) Continue the existing Companion roadmap after the coordinated merge.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - Prodigy and Companion API proto files -> byte-identical
    - `git diff --check` -> PASS
    - Repository review -> one confirmed normalization edge fixed; full rerun
      LGTM
    - Pixel upgrade -> PASS; legacy record retired and storage version 2 saved
    - QR pairing and generation-2 persistence -> PASS
    - Force-stop/relaunch saved-client reconnect -> PASS without manual entry
    - Battery/GPS/connectivity reports and stable SOCKS route -> PASS
  - AA stream continuity: preserved; Prodigy remained connected with H.265

---

## 2026-07-08 11:22 (local)

- What changed:
  - Replaced the Status screen Settings action's external browser launch with an in-app WebView screen.
  - Added process-network binding around the WebView so the page can use the matched Android Auto/head-unit Wi-Fi `Network`.
  - Added a small binding coordinator with unit tests for bind, restore, missing-network, and failed-bind behavior.
  - Added inline WebView warnings for missing or rejected network binding.
  - Added Superpowers design and implementation plan docs for this feature branch.
- Why:
  - Pixel diagnostics showed the phone had `10.0.0.21/24` on the Prodigy AP and forced Wi-Fi ping to `10.0.0.1` worked, but unbound traffic to `10.0.0.1` routed over cellular. External browsers cannot use Companion's bound network route, so web config must run in app.
- Status: done
- Dependency decision:
  - Companion-only: Yes
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) On Pixel, open Companion -> vehicle status -> Settings while Android Auto is connected.
  - 2) Confirm the web config page loads in app and back navigation returns to vehicle status.
  - 3) Confirm AA audio remains uninterrupted while opening and closing web config.
- Verification:
  - `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:testDebugUnitTest --tests org.openauto.companion.net.ProcessNetworkBindingTest` -> PASS
    - `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:testDebugUnitTest --tests org.openauto.companion.ui.WebConfigBindingWarningTest` -> PASS
    - `ANDROID_HOME=/mnt/e/claude/personal/openautopro/openauto-companion/.gradle/android-sdk ./gradlew :app:assembleDebug` -> PASS
    - `git diff --check` -> PASS
    - `/mnt/e/Android/Sdk/platform-tools/adb.exe -s 39260DLJH000LX install -r app/build/outputs/apk/debug/app-debug.apk` -> PASS
  - AA stream continuity: not tested

## 2026-07-07 19:48 (local)

- What changed:
  - Added an Android network-security config and referenced it from the app manifest.
  - Allowed cleartext HTTP only for the head-unit web-config host `10.0.0.1`, preserving the default block for other destinations.
  - Added a unit test that verifies the manifest uses the config and that cleartext is not enabled app-wide.
- Why:
  - Live Pixel testing showed Android blocked the new HTTP theme install with `cleartext communication to 10.0.0.1 not permitted by network security policy` before the request reached the head unit.
- Status: done
- Dependency decision:
  - Companion-only: Yes
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Decide whether TLS for the web-config API should become a separate Companion/head-unit contract update.
  - 2) Merge or PR `feat/theme-http-install`.
  - 3) Continue External API runtime migration for non-theme legacy `9876` traffic.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `./gradlew :app:testDebugUnitTest --tests org.openauto.companion.net.NetworkSecurityConfigTest` -> PASS
    - `/mnt/e/Android/Sdk/platform-tools/adb.exe -s 39260DLJH000LX install -r app/build/outputs/apk/debug/app-debug.apk` -> PASS
    - `/mnt/e/Android/Sdk/platform-tools/adb.exe -s 39260DLJH000LX shell monkey -p org.openauto.companion 1` -> PASS
    - Live Pixel theme install against the head-unit AP HTTP endpoint -> PASS (user-confirmed)
  - AA stream continuity: not tested (network-security config/build/install only)

## 2026-07-07 18:50 (local)

- What changed:
  - Replaced legacy `9876` theme/wallpaper chunk transfer with one OkHttp multipart POST to the head-unit web-config theme install endpoint.
  - Sent `manifest` as a no-filename form field and wallpaper as optional `image/jpeg` file part `wallpaper.jpg`.
  - Updated theme install result handling so server `installed:false`, HTTP errors, malformed responses, and network failures surface as visible failures.
  - Passed the selected vehicle web-config host into theme installs, falling back to `10.0.0.1`.
  - Bound theme install HTTP requests to the matched Wi-Fi network when available.
  - Marked the delivered head-unit HTTP theme endpoint in project docs and roadmap.
- Why:
  - The head unit now ships the verified HTTP theme install endpoint, so theme transfer no longer needs the legacy `9876` chunk/HMAC/ack protocol.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Web-config theme/wallpaper upload endpoint for theme/wallpaper legacy `9876` retirement.
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
    - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.ProtocolTest"` -> PASS
    - `./gradlew :app:assembleDebug` -> PASS
  - AA stream continuity: not tested (unit/build validation only; no runtime media/session behavior changed)

## 2026-07-07 09:39 (local)

- What changed:
  - Refreshed vendored External API protos to the deployed v1.1 additive contract.
  - Updated API helpers for minor version 1 and optional `TimeReport.timezone_id`.
  - Added vehicle persistence for `ServerHello.server_id` and `SystemStatus` display dimensions.
  - Tightened opt-in live validation ready-path assertions for v1.1 server identity.
  - Kept invalid-auth live validation tolerant of early close while the head-unit terminal rejection frame blocker remains open.
  - Updated migration and roadmap docs for v1.1 field-handling foundation readiness.
- Why:
  - Companion needs v1.1 foundation state before live pairing and service-report cutover work.
- Status: done
- Dependency decision:
  - Companion-only: Yes
  - If No, reference `Blocked by Head Unit` entry: n/a; v1.1 fields are deployed.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Add live pairing UI/service path to acquire and persist v1 credentials.
  - 2) Validate known-client auth, subscribe snapshots, and v1 report sending against the Pi.
  - 3) Plan the runtime report cutover while preserving legacy theme transfer on `9876`.
- Verification:
  - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiV11ProtoAccessTest" --tests "org.openauto.companion.net.api.ApiHandshakeTest" --tests "org.openauto.companion.net.api.ApiReportsTest" --tests "org.openauto.companion.data.VehicleSerializationTest" --tests "org.openauto.companion.net.api.ApiPairingCredentialStoreTest"` -> PASS
  - `./gradlew :app:assembleDebugAndroidTest` -> PASS
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `git diff --check` -> PASS
  - AA stream continuity: not tested (foundation-only; no runtime service cutover)

## 2026-07-06 22:34 (local)

- What changed:
  - Updated `AGENTS.md` to make Superpowers the required agent workflow and to avoid GSD/`.planning` unless explicitly requested by the user.
  - Updated project vision and roadmap memory so External API v1.1 fields and proxy-route teardown are treated as available on the deployed head-unit software.
  - Refreshed the parent Companion API v1.1 handoff prompt so future sessions do not inherit the obsolete "Pi is still on v1.0" caveat.
- Why:
  - Future Companion sessions should resume from Superpowers plus repo docs and test against the real v1.1 head-unit behavior.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: External API v1.1 additive fields needed for full legacy-retirement parity.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Resume Companion API v1.1 integration using Superpowers and the repo docs.
  - 2) Pair against the v1.1 head unit, persist client credentials, and validate known-client auth/subscriptions/reports.
  - 3) Validate proxy-route teardown and reconnect re-apply behavior during SOCKS5 proxy session disconnect/reconnect.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `git diff --check -- AGENTS.md docs/project-vision.md docs/roadmap-current.md docs/session-handoffs.md` -> PASS
    - External handoff prompt inspected directly because it lives outside the Companion git repository.
  - AA stream continuity: not tested (docs/process and dependency-memory update only)

## 2026-07-06 18:52 (local)

- What changed:
  - Reran the Pixel app-bound External API v1 live probes after new Prodigy software was deployed on the Pi.
  - Updated project boundary docs and roadmap now that v1 listeners accept connections.
  - Added a separate blocker for live terminal rejection frame delivery after invalid known-client auth closed before a terminal frame reached Companion.
- Why:
  - The prior blocker was listener availability (`ECONNREFUSED` on `9810`/`9811`). The new Pi build changes that failure mode and unblocks the next pairing/known-client validation step.
- Status: in progress
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: External API v1 terminal rejection frame delivery.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Open the head-unit External API pairing window and run a live pairing probe that requests pairing with the displayed PIN.
  - 2) Persist the granted `client_id` and 32-byte secret, then rerun known-client auth against `9810`.
  - 3) Capture or fix terminal `AuthReject`/`Error` delivery for invalid-auth paths before relying on live terminal-frame UX.
- Verification:
  - `./gradlew` gate: not run (docs-only update; no app code changed in this retry).
  - Additional checks (if any):
    - Pixel Wi-Fi route: `ping -I wlan0 10.0.0.1` -> PASS, 0% packet loss.
    - App-bound port probe `10.0.0.1:9876` with `-e live_api_v1 true` -> PASS.
    - App-bound port probe `10.0.0.1:9810` with `-e live_api_v1 true` -> PASS.
    - App-bound port probe `10.0.0.1:9811` with `-e live_api_v1 true` -> PASS.
    - App-bound invalid known-client TCP handshake probe on `9810` -> FAIL, connection closed before a terminal auth/error frame.
  - AA stream continuity: not tested (validation harness only; no service/runtime cutover)

## 2026-07-06 17:52 (local)

- What changed:
  - Added an opt-in instrumentation live probe for External API v1 TCP validation from the app context.
  - Reinstalled the debug and androidTest APKs on the Pixel and ran app-bound Wi-Fi port checks against the Pi AP host.
  - Updated project boundary docs and roadmap with the discovered head-unit v1 listener blocker.
- Why:
  - Shell TCP probes cannot use the no-internet AP route, so live validation needs the same app-visible Wi-Fi `Network.socketFactory` model used by Companion runtime code.
- Status: blocked
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: External API v1 listener availability on the head-unit AP.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Verify the deployed Prodigy build on the Pi starts External API v1 listeners on `9810` and/or `9811`.
  - 2) Rerun the Pixel live probe after the head-unit listener is available.
  - 3) Only after v1 live validation reaches auth/READY, proceed toward live pairing and service report cutover planning.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` -> PASS
  - Additional checks (if any):
    - Opt-in guard: `am instrument ... ApiV1LiveValidationTest#tcpPortAcceptsConnectionsOverWifiNetwork` without `-e live_api_v1 true` -> SKIPPED/OK.
    - Pixel Wi-Fi route: `ping -I wlan0 10.0.0.1` -> PASS, 0% packet loss.
    - App-bound port probe `10.0.0.1:9876` with `-e live_api_v1 true` -> PASS.
    - App-bound port probe `10.0.0.1:9810` with `-e live_api_v1 true` -> FAIL, `ECONNREFUSED`.
    - App-bound port probe `10.0.0.1:9811` with `-e live_api_v1 true` -> FAIL, `ECONNREFUSED`.
  - AA stream continuity: not tested (validation harness only; no service/runtime cutover)

## 2026-07-06 14:48 (local)

- What changed:
  - Extended `Vehicle` persistence with optional External API v1 credentials: `api_client_id`, `api_secret_hex`, and `api_mode`.
  - Added migration-safe defaults so existing vehicle JSON remains legacy mode with null v1 credentials.
  - Added `ApiPairingCredentialStore` to persist `ApiSessionClient` pairing credentials as client id plus 32-byte secret hex for a matched vehicle.
  - Added unit tests for vehicle serialization defaults and pairing-result credential persistence.
  - Updated the API v1 migration plan and roadmap wording to include the credential-storage foundation.
- Why:
  - Companion needs per-vehicle v1 auth material beside the legacy `sharedSecret` before any future live pairing or service report cutover can be safely wired.
- Status: done
- Dependency decision:
  - Companion-only: Yes
  - If No, reference `Blocked by Head Unit` entry: n/a; first Pi live-client validation remains a future integration step before service cutover.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Run first live Pi client validation with the v1 session client before service report cutover.
  - 2) Add an explicit v1 pairing entry point only after deciding how v1 mode is selected during transition.
  - 3) Keep theme/wallpaper on legacy `9876` until the future web-config HTTP upload endpoint exists.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest" --tests "org.openauto.companion.net.api.ApiPairingCredentialStoreTest"` -> PASS
    - `git diff --check` -> PASS
    - New API helper scan found no v1.1 field references in app code.
  - AA stream continuity: not tested (no service/runtime cutover in this slice)

## 2026-07-06 14:20 (local)

- What changed:
  - Added `ApiTransport`, `ApiTcpTransport`, `ApiWebSocketTransport`, and `ApiSessionClient` under `org.openauto.companion.net.api`.
  - Added OkHttp WebSocket runtime dependency and MockWebServer test dependency.
  - Added loopback TCP transport tests, MockWebServer WebSocket tests, and fake-transport session client tests.
  - Added `docs/plans/api-v1-transport-implementation.md` and updated roadmap wording for the now-covered transport foundation.
- Why:
  - External API v1 needs both TCP `9810` and WebSocket `9811` adapters before live Pi validation or service integration can proceed.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: first Pi live-client validation is still pending; legacy `9876` retirement remains gated by web-config theme upload and v1.1 parity fields.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Add per-vehicle v1 credential storage beside legacy `sharedSecret`.
  - 2) Add v1 pairing/auth integration path without removing the legacy connection path.
  - 3) Run Pi live-client validation over AP `10.0.0.1` and LAN host/port before service report cutover.
- Verification:
  - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.*"` -> PASS
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - New API code scan found no references to v1.1-only fields.
    - Legacy `PiConnection`, `Protocol`, `ThemeTransfer`, and `CompanionService` runtime files were not changed.
  - AA stream continuity: not tested (no service/runtime cutover in this slice)

## 2026-07-06 13:05 (local)

- What changed:
  - Added `docs/plans/api-v1-migration.md` with the legacy `9876` inventory, v1 target architecture, phased migration plan, test strategy, and gap-decision record.
  - Vendored the frozen External API v1 protos from `../openauto-prodigy/proto/api/` into `app/src/main/proto/api/` with a source-of-truth README.
  - Added protobuf-lite Gradle codegen and runtime dependency.
  - Added pure JVM-tested v1 protocol foundation classes under `org.openauto.companion.net.api`: `ApiCrypto`, `ApiFrameCodec`, `ApiReports`, and `ApiHandshake`.
  - Updated roadmap and project boundary docs for the API v1 foundation and legacy-retirement head-unit gates.
- Why:
  - Head-unit External API v1 is merged and frozen, so Companion can build against the contract while preserving existing legacy runtime behavior until live Pi validation.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Web-config theme/wallpaper upload endpoint for legacy `9876` retirement; External API v1.1 additive fields needed for full legacy-retirement parity.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Add TCP `9810` and WebSocket `9811` transport adapters behind the pure codec, with fake local server tests.
  - 2) Add v1 credential storage and pairing integration alongside the existing legacy `sharedSecret` path.
  - 3) Schedule Pi live-client validation over the AP and LAN host/port before any service cutover.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.*"` -> PASS
    - `./gradlew :app:generateDebugProto` -> PASS
    - New API code scan found no references to v1.1-only fields.
  - AA stream continuity: not tested (no service/runtime cutover in this slice)

## 2026-02-27 10:14 (local)

- What changed:
  - Updated roadmap outcomes to mark SOCKS5 bridge control-plane work as complete for companion/device-side flow, with explicit outstanding work for deterministic desktop-app routing through the Pi proxy path.
- Why:
  - Validation evidence now suggests step-1 objectives are met, but routing guarantees for Pi desktop/system processes are still an open requirement and must be separately owned on the Prodigy side.
- Status: in progress
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Reverse bridge direction protocol and other remaining networking exceptions remain required for complete cross-app routing behavior.
- Wishlist promotion:
  - Source item: SOCKS5 Bridging MVP
  - Promotion result: Partially promoted (phone-side bridge controls validated; desktop traffic routing remains)
- Next steps:
  - 1) Define a representative desktop/system app traffic matrix to validate under active AA while SOCKS bridge is enabled.
  - 2) Confirm required Pi-side iptables/redsocks exception tuning and update Prodigy tasking accordingly.
  - 3) Close the desktop-routing gap and then promote SOCKS5 Bridging from partial to complete in roadmap.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - Companion-side real-device toggling remains verified.
  - AA stream continuity: preserved in prior in-vehicle companion validation; re-validate during desktop-routing matrix when test execution resumes.

## 2026-02-26 17:10 (local)

- What changed:
  - Confirmed Prodigy head unit now emits stable `vehicle_id` in pairing/discovery payloads.
  - Updated `docs/project-vision.md` blocker status for dynamic identity from `Requested` to `Delivered`.
- Why:
  - Remove dependency ambiguity and unblock deterministic ID-based companion routing behavior.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Dynamic identity and endpoint advertisement for settings/connection routing.
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Perform one live QR pairing with `vehicle_id` and verify persisted vehicle id is preserved.
  - 2) Validate reconnect/route behavior after SSID rename/reuse scenario.
  - 3) Confirm no stream interruption during the above flow.
- Verification:
  - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PairingUriParserTest"` -> PASS
  - `./gradlew :app:testDebugUnitTest` -> PASS
  - `./gradlew :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - Prodigy live pairing + reconnect test pending your environment run.
  - AA stream continuity: not tested (docs/dependency status update only)

## 2026-02-26 18:28 (local)

- What changed:
  - Completed daemon-side SOCKS bridge validation against `openauto-system` on Pi (`set_proxy_route` IPC + iptables/redsocks path + curl-to-google success while route enabled).
  - Confirmed route teardown removes proxy chain and returns to `disabled` state.
- Why:
  - The implementation path is functionally sound at daemon level, but we still need verification of the full companion-led workflow in the real user path.
- Status: in progress
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Android app pairing/session path and AA continuity checks during live bridge toggling still pending manual run.
- Wishlist promotion:
  - Source item: SOCKS5 Bridging MVP
  - Promotion result: Not promoted (runtime path validated, real-world acceptance not yet complete)
- Next steps:
  - 1) Run live companion app flow: pairing, connection, enable/disable internet sharing toggle, and return-to-AA continuity check.
  - 2) Validate bridge works with both test domain(s) requested in the lab checklist and confirm user-visible status updates in CompanionSettings.
  - 3) Record command/log evidence and promote this goal in roadmap only after uninterrupted AA session continuity is proven.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS (baseline companion checks)
  - Additional checks (if any):
    - Pi socket IPC: `set_proxy_route` enabled/disabled toggles work; iptables chain lifecycle correct.
    - Curl test: `https://www.google.com` succeeded over active route.
    - Real-device end-to-end validation with phone UI + AA continuity: REQUIRED
- AA stream continuity: not tested (real-world companion workflow pending)

## 2026-02-26 18:31 (local)

- What changed:
  - Ran a live Android-side verification pass with the phone connected to `OpenAutoProdigy` Wi-Fi (`10.0.0.26`).
  - Confirmed service startup/handshake to `10.0.0.1:9876` still works and that local status/socks startup path responds to persisted vehicle preference.
  - Confirmed local SOCKS5 listener on `127.0.0.1:1080` can reach https://www.google.com when `socks5_enabled=true`.
  - Confirmed `socks5_enabled=false` disables startup after restart; proxy endpoint is unreachable until the app/service is relaunched.
  - Confirmed toggling `Internet Sharing` in app updates persisted `socks5_enabled` immediately in preferences.
- Why:
  - Need confirmation of no-regenression behavior of companion-side bridge controls from real device state transitions.
- Status: in progress
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: AA session continuity and in-vehicle reconnect behavior still pending direct validation.
- Wishlist promotion:
  - Source item: SOCKS5 Bridging MVP
  - Promotion result: Not promoted (phone-side behavior pass complete; real AA stream continuity still pending)
- Next steps:
  - 1) Execute a true companion-to-head-unit AA stream run during enable/disable to verify stream is not interrupted.
  - 2) Add service-restart behavior clarification around preference toggles (if expected, document; if not, implement hot-restart of proxy state).
  - 3) Add Pi-side capture evidence for route enable/disable and DNS reachability once SSH/call path is available.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS (existing baseline from prior run)
  - Additional checks (if any):
    - Android device on `OpenAutoProdigy`, wifi SSID matched and companion connected to 10.0.0.1.
    - `curl --socks5-hostname 127.0.0.1:1080 --proxy-user oap:56faf64d -I https://www.google.com` returned HTTP 200 when socks enabled.
    - After forcing `socks5_enabled=false` and relaunching, service skipped proxy startup and proxy connection failed as expected.
  - AA stream continuity: not tested (no live AA session run yet)

## 2026-02-26 18:32 (local)

- What changed:
  - Ran repository verification gate and confirmed build/tests are green.
  - Re-checked device connectivity and confirmed the test phone is currently on a cellular route (no active route to `10.0.0.1`), so no live AA continuity/proxy-on-head-unit check could be executed in this pass.
- Why:
  - Keep roadmap execution honest: code-level validation is complete; real-world bridging validation still requires the active OpenAuto AP network path.
- Status: blocked
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: AA continuity and real-car headunit session validation remain manual-path dependent.
- Wishlist promotion:
  - Source item: SOCKS5 Bridging MVP
  - Promotion result: Not promoted (end-to-end AA/route validation blocked by environment at test moment)
- Next steps:
  - 1) Reconnect phone to `OpenAutoProdigy` SSID and rerun companion pairing + route-toggle flow.
  - 2) Re-run Pi-side curl via SOCKS from phone and via Pi itself while AA stream is active.
  - 3) Capture stream continuity evidence during route enable/disable.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `adb shell 'ip route get 10.0.0.1 && ping -c 1 -W 1 10.0.0.1'` -> route points to rmnet_data7, ping FAILED (100% packet loss).
    - `adb shell 'ps -A | grep -i openauto'` -> `org.openauto.companion` running.
  - AA stream continuity: not tested (phone not on OpenAuto network)

## 2026-02-26 18:41 (local)

- What changed:
  - Executed step-1 validation: connected phone to `OpenAutoProdigy` (`10.0.0.26/24`) and performed companion SOCKS toggling end-to-end from phone.
  - Verified proxy lifecycle from preference state:
    - `socks5_enabled=true` starts proxy and allows outbound curl over `127.0.0.1:1080`.
    - `socks5_enabled=false` disables proxy and `curl` fails to connect to local SOCKS listener.
- Why:
  - Confirm the companion app’s control plane (enable/disable persistence + runtime behavior) is functioning on-device before relying on it for Pi-side proxy routing.
- Status: in progress
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: AA stream continuity and active in-vehicle companion flow still require live Android Auto playback session.
- Wishlist promotion:
  - Source item: SOCKS5 Bridging MVP
  - Promotion result: Partially promoted (phone-side enable/disable behavior confirmed; full live stream continuity still pending)
- Next steps:
  - 1) Keep phone on `OpenAutoProdigy` and run the same enable/disable pass during an active AA session.
  - 2) Capture logs from phone + head unit for route state transitions and post-proxy connectivity.
  - 3) Verify user-visible status text updates while toggling Internet Sharing.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `ip -4 -o addr show wlan0` -> `10.0.0.26/24`.
    - `ping -I wlan0 10.0.0.1` -> `1/1` packets received.
    - `curl --socks5-hostname 127.0.0.1:1080 --proxy-user oap:56faf64d -I https://www.google.com` -> `HTTP/1.1 200 OK` (socks enabled).
    - After forcing `socks5_enabled=false` + app restart -> `curl ... 127.0.0.1:1080` -> `curl: (7) Failed to connect`.
  - AA stream continuity: preserved (run was performed during active Android Auto session; no stream loss observed by user during this pass)

## 2026-02-26 15:48 (local)

- What changed:
  - Updated pairing URI parsing to accept and persist `vehicle_id` when present, with fallback to legacy `id`.
  - Propagated parsed `vehicleId` from QR scan callback into the companion pairing flow and vehicle persistence.
- Why:
  - Enable deterministic per-headunit identity independent of SSID collisions/changes for stable routing and settings/page behavior.
- Status: done
- Dependency decision:
  - Companion-only: No
  - If No, reference `Blocked by Head Unit` entry: Dynamic identity and endpoint advertisement for settings/connection routing.
- Wishlist promotion:
  - Source item: Dynamic identity for deterministic headunit matching
  - Promotion result: Promoted (app-side readiness implementation)
- Next steps:
  - 1) Live pair one head unit and verify persisted `Vehicle.id` stores payload `vehicle_id`.
  - 2) Validate reconnect behavior when SSID changes while `vehicle_id` stays constant.
  - 3) Perform AA session continuity check for QR pairing and pairing-triggered route selection.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
    - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PairingUriParserTest"` -> PASS
  - AA stream continuity: not tested (unit/build validation only)

## 2026-02-26 15:53 (local)

- What changed:
  - Added duplicate-vehicle detection during pairing using both `vehicle.id` and SSID.
  - Updated manual/QR pairing flows to reject duplicate pairing attempts and show a clear `Pairing Skipped` message.
- Why:
  - Prevent accidental duplicate pairing entries for the same head unit while preserving existing runtime behavior for new pairings.
- Status: done
- Dependency decision:
  - Companion-only: Yes
  - If No, reference `Blocked by Head Unit` entry
- Wishlist promotion:
  - Source item: prevent accidental duplicate pairing
  - Promotion result: Promoted (in-app safeguard)
- Next steps:
  - 1) Validate second scan of same QR code is skipped and `vehicles_json` remains unchanged.
  - 2) Add UI message for pairing list-cap reached case if needed.
  - 3) Confirm no stream interruption during repeated pairing attempts.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - Additional checks (if any):
  - AA stream continuity: not tested (unit/build validation only)

## 2026-02-26 14:34 (local)

- What changed:
  - Established isolated worktree on branch `chore/project-management-setup`.
  - Added balanced management-system design and implementation plan docs.
  - Added project-level worktree ignore rule (`.worktrees/`) to repository `.gitignore`.
- Why:
  - Create a reliable, low-overhead workflow for maintaining design vision and continuity across sessions.
- Status: done
- Next steps:
  - 1) Add `project-vision.md` and `roadmap-current.md`.
  - 2) Encode workflow contract in `AGENTS.md`.
  - 3) Run final verification gate and append completion handoff.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS (BUILD SUCCESSFUL in worktree baseline)
  - `git worktree add .worktrees/project-management-setup -b chore/project-management-setup` -> PASS

## 2026-02-26 14:37 (local)

- What changed:
  - Added `docs/project-vision.md` as stable direction source of truth.
  - Added `docs/roadmap-current.md` with `Now / Next / Later / Deferred` priorities.
  - Added local `AGENTS.md` workflow contract enforcing vision check, roadmap updates, verification gate, and behavior-change handoffs.
- Why:
  - Ensure design intent stays current across sessions with minimal process overhead for a solo development workflow.
- Status: done
- Next steps:
  - 1) Use this loop for the next behavior-changing feature task from intake through handoff.
  - 2) Keep `roadmap-current.md` updated whenever sequencing changes.
  - 3) Add CI enforcement of verification gate when ready.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS (BUILD SUCCESSFUL)
  - `rg -n "Project Management Loop|project-vision|roadmap-current|session-handoffs" AGENTS.md` -> PASS

## 2026-02-26 16:14 (local)

- What changed:
  - Added project-priority constraint for preserving Android Auto stream continuity during networking feature work.
  - Updated roadmap `Now` section to include AA stream continuity verification as a priority.
  - Updated `docs/project-vision.md` with the non-negotiable no-stream-interruption principle.
  - Updated `docs/session-handoffs.md` verification template with explicit `AA stream continuity` outcome field.
- Why:
  - Keep all future bridge/networking work constrained by operational safety around active AA sessions.
- Status: done
- Dependency decision:
  - Companion-only: Yes
  - If No, reference `Blocked by Head Unit` entry
- Wishlist promotion:
  - Source item: n/a
  - Promotion result: Not promoted
- Next steps:
  - 1) Add AA continuity checks to 1–2 in-progress implementation plans as explicit acceptance criteria.
  - 2) Keep AA continuity column populated in each future behavior-change handoff.
  - 3) Begin execution of the highest-priority backlog item once head-unit responses are available.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> NOT RUN (docs/process-only changes)
  - Additional checks (if any):
    - AA stream continuity: not tested (docs/process change)

## 2026-07-13 12:44 (local)

- What changed:
  - Replaced the foreground service's combined legacy JSON/HMAC sender with a
    single-generation External API v1 runtime using only Wi-Fi-bound TCP
    `9810`.
  - Added READY-session lifecycle handling, bounded reconnect backoff,
    `server_id` enforcement, serialized/conflated reports, `TOPIC_SYSTEM`
    subscription, and display-dimension persistence.
  - Split cellular upstream ownership from SOCKS5, generated a random proxy
    password per vehicle generation, kept the local listener across transient
    API reconnects, and made the vehicle-scoped UI toggle immediate.
  - Added live manual six-digit PIN pairing. A vehicle is saved only after a
    complete READY result supplies a nonblank client id and 32-byte secret.
    The QR route/button is dormant.
  - Added a schema-gated synchronous migration that deletes legacy vehicle
    records and old single-vehicle keys. Runtime reads still filter invalid
    records if the commit must retry.
  - Deleted `PiConnection`, `Protocol`, legacy tests, `Vehicle.sharedSecret`,
    and `Vehicle.ApiMode`; production code has no path to TCP `9876`.
  - Updated opt-in instrumentation for TCP `9810`, optional guarded `9876`
    refusal, and optional real known-client credentials without sending
    reports.
- Why:
  - Complete the approved API v1 runtime cutover with manual pairing first,
    delete legacy records during migration, and make transport exclusivity
    structural before the live bench.
- Sender mapping used:
  - legacy time/timezone -> `TimeReport` after READY and on time/timezone change
  - legacy GPS fields -> real-fix-only `GpsReport` at approximately 1 Hz
  - legacy battery fields -> sticky/change-driven `BatteryReport`
  - legacy SOCKS state -> upstream-aware `ConnectivityReport`
  - legacy hello/display metadata -> authenticated v1 handshake plus
    `TOPIC_SYSTEM` / `SystemStatus`
- Status: in progress (implementation complete; live Pixel/Prodigy bench
  pending)
- Dependency decision:
  - Companion-only: No
  - Head-unit prerequisites are delivered; the remaining work is coordinated
    hardware validation and the already-documented terminal-frame diagnostic.
- Next steps:
  - 1) Attach the Pixel via Windows ADB, install the debug APK, and run the
    no-flag instrumentation guard.
  - 2) Open a Prodigy pairing window and complete manual PIN pairing, known
    client reconnect, report/display, and bridge toggle checks.
  - 3) Run the Prodigy section 7 continuity scenarios, including API listener
    restart and active Android Auto media continuity, then record evidence.
- Verification:
  - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.*"`
    -> PASS
  - `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.Socks5ServerTest" --tests "org.openauto.companion.data.*" --tests "org.openauto.companion.service.*"`
    -> PASS
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - `./gradlew :app:assembleDebugAndroidTest` -> PASS
  - Structural scans -> no production `PiConnection`, legacy status/theme
    builders, `ApiMode`, or `9876`; `shared_secret`/`api_mode` occur only in
    `VehicleStorageMigration`.
  - `/mnt/e/Android/Sdk/platform-tools/adb.exe devices` -> no attached device,
    so the no-flag instrumentation selector was not run.
- AA stream continuity: not tested in this implementation session; required in
  Task 12 before completion.

## 2026-07-13 15:43 (local)

- What changed:
  - Completed the live Pixel/Prodigy External API v1 cutover bench with
    Prodigy's legacy listener disabled.
  - Fixed live pairing on the Android Auto-owned Wi-Fi network. Android exposed
    the network and direct `10.0.0.0/24` route but redacted its SSID from the
    synchronous capability read, causing a false "connect to Wi-Fi" failure.
    `WifiNetworkResolver` now keeps exact SSID matching first and falls back to
    a direct-route match for the configured API host.
  - Added an opt-in live resolver regression to the existing guarded API v1
    instrumentation suite.
- Why:
  - Manual API pairing could reach `10.0.0.1:9810` from the app context but
    failed before opening a session because network selection depended only on
    SSID metadata that Android redacts for the AA-owned network.
- Status: done; External API v1 runtime cutover and live validation complete.
- Live results:
  - Migration deleted the prior legacy record/keys and opened manual pairing.
  - Manual PIN pairing reached READY and saved one API client id, 32-byte
    secret, and server id; no legacy secret remained.
  - Prodigy config persisted `companion.enabled: false`; startup logged the
    disabled state, `ss` showed TCP `9810` only, and the guarded phone-side
    `9876` refusal check passed.
  - Saved-client reconnect passed after Prodigy restart and after Companion
    force-stop/relaunch without another pairing window.
  - Prodigy API IPC reported a real GPS fix, battery `62%` and charging,
    connectivity active, and the phone SOCKS endpoint. A controlled UTC
    mismatch was restored to `America/Chicago` by the API `TimeReport` and
    recorded in the journal.
  - Internet Sharing off immediately cleared the API proxy and SystemService
    route; live verification returned disabled with listener/iptables/upstream
    all false. Re-enable restored active with all three checks true.
  - Companion force-stop immediately cleared connected/GPS/battery/proxy owner
    state and the route; relaunch replayed reports and the route once. Pixel
    logs showed no legacy fallback or `9876` attempt.
  - The same established AA TCP `5277` socket remained present through bridge
    toggles, Companion force-stop, and saved-client reconnect.
- Operational note:
  - Editing `~/.openauto/config.yaml` before `systemctl restart` is overwritten
    by Prodigy's clean-shutdown config flush. The reliable order is stop the
    service, edit `companion.enabled`, then start it. A timestamped pre-change
    backup remains on the Pi.
- Next steps:
  - 1) Keep Prodigy `companion.enabled: false`; do not restore a legacy
    fallback.
  - 2) Continue the existing deterministic Pi desktop/system routing priority.
  - 3) Treat API QR pairing as a future convenience slice; manual PIN pairing
    remains the supported path.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
  - `./gradlew :app:assembleDebugAndroidTest` -> PASS
  - No-flag `ApiV1LiveValidationTest` -> PASS (`OK`, all tests safely skipped)
  - Guarded live `ApiV1LiveValidationTest` with API/SSID/legacy-refusal flags ->
    PASS
  - Pi `ss -ltnp` -> `9810` listening; no `9876`
  - SystemService `get_proxy_status {verify:true}` -> correct active/disabled/
    active transitions with no error
  - AA stream continuity: PASS by stable established TCP `5277` session across
    Companion lifecycle and bridge operations

## 2026-07-13 21:02 (local)

- What changed:
  - Activated the existing CameraX/ML Kit scanner from the pairing screen while
    retaining manual SSID/name/PIN entry.
  - Replaced the dormant legacy QR parser with defensive parsing for the
    approved additive `prodigy://pair` contract: required host, TCP/WebSocket
    ports, six-digit PIN, and percent-decoded SSID; unknown fields are ignored.
  - Routed valid scans through the existing live API v1 challenge/response
    coordinator without changing credential derivation or persistence timing.
  - Added persisted `api_tcp_port` with a backward-compatible `9810` default
    and carried it through pairing, Wi-Fi monitor service startup, and runtime
    reconnects.
  - Preserved structured External API error codes through the handshake/session
    seam and added a clean retryable pairing-window-closed UI result, with an
    `AuthReject` reason fallback for compatibility.
  - Removed raw QR/PIN logs, made scanner completion single-shot, surfaced
    camera startup errors, and unbound camera resources on exit.
  - Added the QR design/implementation artifacts and updated vision/roadmap
    alignment. Logged the Prodigy SSID/terminal-frame dependency as In Progress.
- Why:
  - Deliver zero-input API v1 pairing against the newly shipped head-unit QR
    while keeping manual pairing reliable and ensuring advertised live TCP
    endpoints also work after reconnect.
- Status: Companion implementation complete; coordinated live bench pending the
  updated Prodigy QR build and confirmed closed-window terminal frame.
- Dependency decision:
  - Companion-only: No
  - Head-unit reference: External API v1 QR pairing SSID and terminal expiry
    contract (`docs/project-vision.md`, In Progress).
- Next steps:
  - 1) Deploy the Prodigy build that adds percent-encoded `ssid` to the QR and
    report its exact closed-window protobuf frame.
  - 2) Install the Companion debug APK on the attached Pixel and bench
    scan-to-READY plus saved-client reconnect/report flow.
  - 3) Repeat after the 120-second window expires, then mark the dependency and
    implementation plan complete if the clean error and manual fallback pass.
- Verification:
  - Focused QR/pairing/storage/handshake/session unit suite -> PASS.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS (`BUILD SUCCESSFUL`).
  - `./gradlew :app:assembleDebugAndroidTest` -> PASS (`BUILD SUCCESSFUL`).
  - Structural log scan -> PASS; no raw QR value or PIN logging remains.
  - `/mnt/e/Android/Sdk/platform-tools/adb.exe devices` -> Pixel
    `39260DLJH000LX` attached.
  - AA stream continuity: not tested; no APK was installed and no live runtime
    was changed during this implementation session.

## 2026-07-13 21:36 (local)

- What changed:
  - Installed the QR-pairing build on the attached Pixel and completed a
    physical scan of the Prodigy bench QR with no manual entry.
  - Verified that the advertised SSID, live TCP port, API host, and resulting
    credentials were persisted; force-stop/relaunch reconnected as a saved
    client without reopening a pairing window.
  - Changed handshake messages to use the protocol-required nonzero request ID
    (`1`) after the first closed-window probe exposed the prior zero value.
  - Added an exact codec regression for the documented 29-byte
    `ERROR_CODE_PAIRING_WINDOW_CLOSED` payload and its four-byte TCP length
    prefix.
  - Updated the QR plan and project dependency status to distinguish completed
    Companion behavior from the remaining Prodigy live terminal-frame issue.
- Why:
  - Close the physical zero-input pairing acceptance criteria and determine
    whether the documented closed/expired-window behavior is actually present
    on the deployed remote TCP path.
- Status: partial. Companion QR pairing and saved-client reconnect pass. The
  closed-window acceptance criterion is blocked by the deployed Prodigy TCP
  path closing before the promised typed error frame is received.
- Live results:
  - Head-unit QR -> Companion scan -> READY succeeded without typing an SSID,
    endpoint, or PIN.
  - The saved vehicle contains `Prodigy_e57d`, API host `10.0.0.1`, and TCP port
    `9810`; credential presence was verified without exposing credential
    values.
  - Force-stop/relaunch returned the vehicle to Connected without a new pairing
    window. The final phone state was Connected with Time and GPS reports
    active.
  - A no-window attempt using the original request ID `0` ended at EOF before
    the four-byte TCP prefix. After correcting Companion to use request ID `1`,
    the same live attempt again ended at EOF before any terminal frame.
  - Companion's transport regression receives a terminal frame written,
    flushed, and immediately followed by close, so the remaining mismatch is
    in the deployed Prodigy remote-socket delivery path rather than the
    Companion decoder or close handling.
- Next steps:
  - 1) Have the Prodigy maintainer inspect and fix the deployed remote TCP
    terminal-frame write/flush path, then redeploy it to the bench Pi.
  - 2) Repeat never-opened and expired-window attempts and verify code `5`,
    message `Pairing window closed`, request-ID echo, and clean close.
  - 3) Confirm head-unit-side report reception and, if required for the release
    gate, Android Auto continuity during the final rerun; then mark the QR plan
    and dependency complete.
- Verification:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
    -> PASS (`BUILD SUCCESSFUL`).
  - Exact protobuf payload/prefix regression -> PASS.
  - Debug APK install on Pixel `39260DLJH000LX` -> PASS.
  - Physical QR scan-to-READY -> PASS; no manual input.
  - Saved-client reconnect after force-stop/relaunch -> PASS.
  - Closed-window typed error against deployed Prodigy TCP -> FAIL/BLOCKED;
    EOF occurred before the length prefix for request IDs `0` and `1`.
  - AA stream continuity: not specifically tested in this QR bench session.

## 2026-07-13 22:13 (local)

- What changed:
  - Re-ran the closed-window Companion path against Prodigy's deployed
    real-socket flush fix (`707be98`, `829f247`; pushed through `6c89903`).
  - Reopened the pairing window through the local trusted External API,
    navigated the head unit to its External API page, and completed a fresh
    physical scan of the QR rendered there.
  - Marked the QR pairing plan, roadmap item, SSID/expiry dependency, and
    terminal-frame delivery dependency complete.
- Why:
  - Verify the previously failing production-lifetime TCP path on the same
    Pixel/Prodigy bench and close the final QR acceptance criteria.
- Status: complete. Both the typed closed-window path and zero-input physical
  QR pairing pass against the fixed deployed Prodigy service.
- Live results:
  - With no window open, Companion received typed code `5` and displayed
    `Pairing window closed. Start a new pairing window and scan again.` No
    vehicle or credentials were persisted for the rejected attempt.
  - The Prodigy External API page visibly rendered its active-window PIN and
    QR. Companion's already-open camera recognized that physical QR, completed
    pairing, and showed `Pairing Successful`; the server closed the window.
  - The saved vehicle persisted `Prodigy_e57d`, host `10.0.0.1`, and TCP port
    `9810`, with credential presence verified without logging their values.
  - Force-stop/relaunch reconnected as a saved client without a new window.
    Final Companion state is Connected with Time, GPS, Battery, and SOCKS5 all
    Active.
  - Prodigy route state changed to disabled on Companion force-stop and active
    again after relaunch/reconnect.
- Next steps:
  - 1) Treat API v1 QR pairing as complete and include the Companion changes in
    the normal review/commit workflow.
  - 2) Continue the existing deterministic Pi desktop/system routing priority.
  - 3) Run a dedicated AA-continuity observation only if that separate release
    criterion still requires a QR-specific repetition.
- Verification:
  - Deployed no-window attempt -> PASS; specific typed-error UI observed.
  - Physical head-unit QR scan-to-READY -> PASS; no manual pairing input.
  - Persisted endpoint/credential-presence check -> PASS.
  - Saved-client reconnect after force-stop/relaunch -> PASS.
  - Final Companion status -> Connected; Time/GPS/Battery/SOCKS5 Active.
  - Prodigy route teardown/replay journal evidence -> PASS.
  - Previous full Gradle gate remains PASS; no Companion code changed during
    this rerun.
  - AA stream continuity: not specifically observed during this final rerun.

## 2026-07-14 07:30 (local)

- What changed:
  - Added an explicit monitor lifecycle seam that distinguishes quiet monitor
    replacement from full runtime teardown.
  - `CompanionApp.startWifiMonitor()` now unregisters the previous monitor
    without stopping `CompanionService`, installs the new monitor, and lets the
    service's existing same-vehicle/same-network idempotence guard preserve the
    live External API session.
  - Kept real `WifiMonitor.onLost()` teardown unchanged. Explicit app-driven
    refreshes used for pairing, unpairing, and vehicle-list changes still stop
    the old service before starting the replacement monitor.
  - Added JVM regression coverage for both quiet replacement and explicit
    service-stopping teardown.
- Why:
  - Activity recreation reran monitor startup. The previous unconditional
    monitor stop killed the correctly surviving foreground service, causing a
    TCP reconnect and transient clearing of GPS/battery/connectivity state on
    Prodigy whenever the phone rotated or the Activity was recreated.
- Status: complete and live-validated on the Pixel/Prodigy bench.
- Next steps:
  - 1) Commit and push the Companion lifecycle fix after review.
  - 2) Keep head-unit owner-disconnect clearing immediate; no Prodigy debounce
    or stale-state masking is needed.
  - 3) Continue the existing deterministic Pi desktop/system routing priority.
- Verification:
  - Red regression: focused test initially failed to compile because the
    monitor lifecycle seam did not exist.
  - `MonitorSlotTest` after implementation -> PASS.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
    (`BUILD SUCCESSFUL`).
  - Debug APK install on Pixel `39260DLJH000LX` -> PASS.
  - Pi API TCP peer before rotation -> `[::ffff:10.0.0.21]:58062`.
  - After forced rotation 1 -> same peer/source port `58062`.
  - After forced rotation 2 -> same peer/source port `58062`.
  - Prodigy journal during the two-rotation window -> no owner disconnect or
    route teardown.
  - Prodigy `companion_status` after rotations -> API source connected with
    live GPS, battery, and internet state.

## 2026-07-14 08:40 (local)

- What changed:
  - Classified API terminal failures by typed error code. `AuthReject` and
    codes `NOT_AUTHENTICATED`/`AUTH_FAILED` require re-pairing; other
    connection-level errors retry, while request-scoped errors remain on the
    live session for their request owner.
  - Added a five-second end-to-end handshake deadline and wired it to TCP's
    socket read timeout, preventing accepted-but-stalled peers from pinning
    pairing or runtime state indefinitely.
  - Refined Activity recreation handling so the Application-owned Wi-Fi
    monitor is reused instead of unregistered/re-registered. Explicit refresh
    and real `onLost` paths still stop the runtime; shared callback state is
    volatile, and redundant starts cannot kill a healthy generation when the
    Wi-Fi handle is transiently unavailable.
  - Preserved literal `+` characters in RFC 3986 pairing-query values while
    retaining the frozen `%2B` QR contract.
  - Closed failed SOCKS upstream sockets, applied idle timeouts to both relay
    sides, and made timeout failures release the paired sockets and connection
    slot while retaining normal half-close behavior.
  - Made replayed GPS age advance from monotonic capture time rather than
    replaying stale fixes as newly captured.
  - Added focused JVM/socket regressions for each corrected path. The current
    roadmap sequence did not change, so `docs/roadmap-current.md` was left
    unchanged.
- Why:
  - Address the consolidated PR #4 review blocker and reliability findings
    before moving the External API v1 migration out of draft review.
- Status: complete, full-gate verified, and the refined rotation lifecycle is
  live-validated on the attached Pixel. Product/security sign-off is resolved:
  API credentials remain eligible for Android Auto Backup because convenient
  restore is worth the low risk for this product; no legacy-record migration
  notice is needed before there are users; and the plaintext LAN/offline-PIN
  risk is accepted as minimal for a moving-vehicle installation.
- Next steps:
  - 1) Complete PR #4 review and merge when approved.
  - 2) Continue the existing deterministic Pi desktop/system routing priority.
- Verification:
  - Red phase: the focused suite initially failed at the new monitor,
    handshake-timeout, and GPS freshness seams before implementation.
  - Focused regression set (API session/runtime/reporting, URI parser, monitor,
    SOCKS, and location mapper) -> PASS (53 tests in that focused run).
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
    (`BUILD SUCCESSFUL`; 163 unit tests, zero failures).
  - `git diff --check` -> PASS.
  - Debug APK install on Pixel `39260DLJH000LX` -> PASS.
  - API TCP peer after reconnect -> `[::ffff:10.0.0.21]:39004`; after forced
    portrait/landscape rotations and restoring auto-rotate -> unchanged source
    port `39004` throughout.

## 2026-07-14 18:05 (local)

- What changed:
  - Changed foreground API runtime socket creation to re-resolve the matched
    Wi-Fi `Network` from the saved vehicle SSID and host on every connection
    attempt instead of retaining the startup handle across retries.
  - Added a required-network socket path that fails fast when the matched
    Wi-Fi is unavailable and never falls back to an unbound/default-network
    socket. The existing EPERM fallback remains available to the separate
    pairing flow.
  - Added regressions proving per-socket re-resolution, fast failure without a
    network, and no fallback after a bound-socket denial.
- Why:
  - After an airplane-mode cycle, the runtime repeatedly used the dead Android
    `Network` handle from before the cycle. Its unbound fallback then routed
    toward cellular and consumed about 35 seconds per failed attempt, so the
    companion never reconnected even after the Prodigy Wi-Fi returned.
- Status: done and live-validated on the Pixel/Prodigy bench.
- Dependency decision:
  - Companion-only: Yes
- Wishlist promotion:
  - Source item: n/a (live bench defect handoff)
  - Promotion result: Not promoted
- Next steps:
  - 1) Review and commit the Companion fix on `dev`, then push when approved.
  - 2) Record the successful self-heal rerun in Prodigy's bench handoff if that
    repository's result ledger should be closed in the same release cycle.
  - 3) Continue the existing deterministic Pi desktop/system routing priority.
- Verification:
  - Focused regression RED: test compilation failed because the new
    required-network factory seam did not exist.
  - `NetworkSocketFactoryTest` after implementation -> PASS.
  - `git diff --check` -> PASS.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` -> PASS
    (`BUILD SUCCESSFUL`; 166 unit tests, zero failures).
  - Debug APK install on Pixel `39260DLJH000LX` -> PASS; initial API reports
    and `socks5://::ffff:10.0.0.21:1080` route active.
  - Airplane ON at 17:58:39 -> Prodigy expired the reporting session after
    31,626 ms at 17:59:09, cleared owned companion state, and disabled the
    proxy route.
  - Airplane OFF at 17:59:30 -> Wi-Fi connected at 17:59:34 and Android Auto
    connected at 17:59:36. Companion reporting, live GPS/battery, and the
    SOCKS route recovered unattended at 18:00:08-09, within 35 seconds of
    Wi-Fi rejoin and 39 seconds of airplane OFF.
  - Companion PID remained `13160` across the complete cycle; no force-stop,
    process restart, or Activity relaunch was used.
  - AA stream continuity: the airplane cycle necessarily interrupted the
    transport; AA reconnected automatically and the `:5277` session was
    established after recovery.
