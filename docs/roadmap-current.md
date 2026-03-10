# Current Roadmap

Governance: capture new ideas in `docs/wishlist.md`; only promoted items should appear in this roadmap.

## Now

- SOCKS5 Bridging: deliver companion-side internet bridge MVP, then stabilize reconnect behavior.
  - Rationale: this is the highest-priority product goal for the next 60 days.
  - Dependency check: if head-unit changes are required, log them under `Blocked by Head Unit` in `docs/project-vision.md` before proceeding past the boundary.
  - Outcome: companion-side enable/disable + status behavior and local phone-side SOCKS operation are validated; daemon-side redsocks/iptables path and status propagation are validated. Remaining validation target is deterministic desktop-app routing on Pi through the bridge while preserving AA continuity.
- Preserve Android Auto stream continuity during all bridge and networking changes.
  - Rationale: AA playback must remain uninterrupted; networking features are additive.
  - Outcome: all connection/retry/recovery actions are tested for no interruption to active AA stream sessions.
- Verify recent connection reliability fixes on-device.
  - Rationale: latest behavior change included Wi-Fi socket fallback (`EPERM`) and should be validated in real network conditions.
  - Outcome: confidence that fallback path and reconnect logic behave as expected across devices.
- Operate from the balanced management loop docs.
  - Rationale: continuity across sessions is now a core workflow requirement.
  - Outcome: all behavior-changing sessions leave vision alignment + handoff evidence.

## Next

- Validate deterministic desktop-app routing when phone-to-headunit bridge is enabled.
  - Rationale: current verification confirms SOCKS listener and representative phone-side traffic, but Pi desktop/system processes must also be guaranteed to honor proxy routing.
  - Dependency check: likely requires Prodigy-side routing exception tuning (protocol/process scope, DNS handling, and service-specific bypass rules).
  - Outcome: representative desktop/system traffic on Pi is shown to traverse the SOCKS path and remains stable during active AA use.

- Support reverse-direction bridge for Pi-to-phone internet sharing.
  - Rationale: enables unusual but valid setups where phone data is unavailable and Pi has upstream connectivity.
  - Dependency check: requires head unit protocol support for reverse proxy/routing intent before companion-side controls can be implemented.
  - Outcome: companion can initiate and monitor Pi-to-phone internet sharing with clear state.

- Remove hardcoded network identity usage across companion discovery and connection flows.
  - Rationale: fixed IP/MAC assumptions are brittle across networks, DHCP changes, and hardware replacement.
  - Dependency check: requires discovery data containing authoritative runtime host and identity.
  - Outcome: dynamic resolution for peer identity with persistence only after validation.

- Add robust multi-headunit selection and session management.
  - Rationale: users may have multiple paired/discoverable head units; app should avoid ambiguous targeting.
  - Outcome: deterministic active-headunit selection, per-device session history, and clear reconnection path.

- Expand unit test coverage around connection failure modes.
  - Rationale: current parsing/fallback coverage is solid, but additional failure-path assertions reduce regression risk.
  - Outcome: clearer guardrails for future network-layer refactors.
- Add lightweight manual QA checklist for pairing + status flows.
  - Rationale: key user paths span camera, permissions, service lifecycle, and intents.
  - Outcome: repeatable validation before release builds.

## Later

- Add CI automation for `:app:testDebugUnitTest` and `:app:assembleDebug` on pull requests.
  - Rationale: local verification is good; CI makes quality gates persistent.
  - Outcome: less risk of broken branches and faster review confidence.
- Evaluate UI polish pass for status and pairing screens.
  - Rationale: core behavior is improving; UX clarity can now be refined without changing architecture.
  - Outcome: cleaner in-app guidance and reduced pairing friction.

## Deferred

- Cloud-backed account syncing or remote vehicle management.
  - Rationale: outside current product scope and adds major system complexity.
  - Outcome: explicitly postponed to protect focus on local companion reliability.
- Non-Android companion clients in this repository.
  - Rationale: platform expansion is not currently aligned with active constraints.
  - Outcome: avoids splitting effort before Android baseline is hardened.
