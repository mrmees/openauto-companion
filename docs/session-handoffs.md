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
