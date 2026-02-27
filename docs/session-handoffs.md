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
