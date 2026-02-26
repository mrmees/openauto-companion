# Current Roadmap

Governance: capture new ideas in `docs/wishlist.md`; only promoted items should appear in this roadmap.

## Now

- SOCKS5 Bridging: deliver companion-side internet bridge MVP, then stabilize reconnect behavior.
  - Rationale: this is the highest-priority product goal for the next 60 days.
  - Dependency check: if head-unit changes are required, log them under `Blocked by Head Unit` in `docs/project-vision.md` before proceeding past the boundary.
  - Outcome: validated browsing via bridge and stable drive-session behavior.
- Verify recent connection reliability fixes on-device.
  - Rationale: latest behavior change included Wi-Fi socket fallback (`EPERM`) and should be validated in real network conditions.
  - Outcome: confidence that fallback path and reconnect logic behave as expected across devices.
- Operate from the balanced management loop docs.
  - Rationale: continuity across sessions is now a core workflow requirement.
  - Outcome: all behavior-changing sessions leave vision alignment + handoff evidence.

## Next

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
