# Project Vision

## Product Intent

OpenAuto Companion is an Android app that securely connects a phone to an OpenAuto Prodigy head unit and continuously shares runtime data needed for in-vehicle experience features (time, GPS, battery, and optional internet/audio support).

## Primary User Outcomes

- Pair a vehicle through live manual PIN entry; keep v1 QR pairing as future
  work until its payload and UX are explicitly approved.
- Reconnect automatically when in range of a paired vehicle SSID.
- See clear connection status and sharing state.
- Open head unit settings from phone with minimal friction.
- Keep optional internet sharing and audio keep-alive under user control per vehicle.

## Design Principles

- Reliability over novelty: connection and reconnection behavior must be predictable.
- Security-first defaults: authenticated handshake and scoped credentials.
- Per-vehicle configuration: settings are stored and applied by vehicle identity.
- Low-friction UX: pairing and operational controls should require minimal steps.
- Evidence before completion: behavior-changing work must include verification output.
- Superpowers-native workflow: agent planning and execution use Superpowers and
  repo docs, not GSD or `.planning` state.
- No-stream interruption: companion networking behavior must not interrupt active Android Auto media streams.

## Constraints

- Android app using Kotlin + Jetpack Compose.
- Companion runtime targets head unit host `10.0.0.1` over External API v1 TCP
  `9810`; web configuration and theme installation remain HTTP on `8080`.
- Web settings UI is expected on `http://<host>:8080`.
- Runtime service depends on location, Wi-Fi, and foreground-service constraints.
- Solo-maintained project: process must remain lightweight.

## Testing Hardware Available

- SSH access is available to the Pi running Prodigy head unit software.
- ADB debug access is available to the phone running the Companion app.

## Product Boundary Policy

- This repository is responsible only for the Prodigy Companion Android app.
- Prodigy head unit app changes are out of scope for this repository and are developed separately.
- If a Companion feature depends on head-unit behavior, it must be tracked in `Blocked by Head Unit` before work proceeds past the dependency boundary.

## Blocked by Head Unit

Use this schema for every dependency:
- `Need`:
- `Why`:
- `Companion impact`:
- `Status`: `Open` | `Requested` | `In Progress` | `Delivered` | `Not Needed`

Example entry format:
- `Need`: <what head-unit capability is required>
- `Why`: <why companion-only solution is insufficient>
- `Companion impact`: <what is blocked or partially deliverable>
- `Status`: `Open` | `Requested` | `In Progress` | `Delivered` | `Not Needed`

Current blockers:
- Reverse bridge direction protocol: start/stop/status command set for Pi-to-phone internet sharing.
  - Need: head-unit reverse routing and route status API.
  - Why: companion cannot safely control reverse bridge behavior without explicit protocol and lifecycle semantics.
  - Companion impact: feature cannot progress beyond UX scaffolding and state handling.
  - Status: Open

- Dynamic identity and endpoint advertisement for settings/connection routing.
  - Need: discovery/pairing payload must include validated host/port and a stable `vehicle_id` value in addition to SSID, with change notifications.
  - Why: hardcoded host/MAC assumptions break under DHCP changes, device replacement, and non-default network layouts; relying on SSID alone breaks after SSID reuse.
  - Companion impact: per-vehicle selection, session routing, and settings-launch routing remain ambiguous without stable vehicle identity.
  - Status: Delivered

- Deterministic Pi desktop/system routing behavior under phone-to-headunit SOCKS bridge.
  - Need: head-unit-side networking policy should ensure expected desktop/system app traffic follows redsocks policy without unintended bypasses while preserving AA service continuity.
  - Why: mobile-side enable/disable and local proxy plumbing are validated, but representative desktop workloads still need proof of policy-compliant routing.
  - Companion impact: end-to-end bridge completion and release criteria cannot be confirmed from companion validation alone.
  - Status: Open

- Web-config theme/wallpaper upload endpoint for theme/wallpaper legacy `9876` retirement.
  - Need: head-unit web-config HTTP endpoint for theme JSON plus wallpaper multipart upload/install.
  - Why: External API v1 deliberately excludes theme/wallpaper blobs and has a 256 KiB frame cap; HTTP is the approved channel.
  - Companion impact: Companion now installs themes through `POST /api/theme/install`; non-theme legacy `9876` runtime traffic is tracked separately.
  - Status: Delivered

- External API v1.1 additive fields needed for full legacy-retirement parity.
  - Need: `SystemStatus` display dimensions, `TimeReport.timezone_id`, and `ServerHello.server_id`.
  - Why: Companion currently gets display dimensions from legacy `hello_ack`, legacy status carries phone timezone, and every head unit AP uses `10.0.0.1` before auth without a stable server identity in v1.
  - Companion impact: no longer head-unit-gated; Companion can integrate `server_id`, `timezone_id`, and display dimensions against live v1.1 hardware.
  - Status: Delivered

- External API v1 listener availability on the head-unit AP.
  - Need: head unit must accept External API v1 TCP on `10.0.0.1:9810` and/or WebSocket on `10.0.0.1:9811`.
  - Why: Companion can reach the Pi AP and legacy `9876`; first app-bound v1 live validation from Pixel initially received `ECONNREFUSED` on both v1 ports.
  - Companion impact: listener availability no longer blocks validation; continue with pairing/known-client auth validation.
  - Status: Delivered

- External API v1 terminal rejection frame delivery.
  - Need: remote invalid-auth/error paths should deliver terminal `AuthReject` or `Error` frames before closing the connection.
  - Why: after the new Prodigy build exposed v1 listeners, a Pixel app-bound invalid known-client TCP probe connected to `9810` but observed connection close before a terminal auth/error frame.
  - Companion impact: normal pairing/known-client validation can proceed, but terminal-frame handling remains unproven against live hardware until the head unit flushes terminal frames or a packet/log capture proves delivery.
  - Status: Open

## Non-Goals

- Building a full backend/cloud service for pairing or account management.
- Supporting non-Android clients in this repository.
- Expanding process overhead into heavyweight issue-tracker workflows.

## Direction Change Log

- 2026-02-26: Added QR pairing endpoint capture and status-screen settings-page launch.
- 2026-02-26: Added socket binding fallback for EPERM and mapped settings open action to web socket port `8080`.
- 2026-02-26: Established balanced project management system (`project-vision`, `roadmap-current`, `session-handoffs`).
- 2026-02-26: Established canonical companion/head-unit boundary policy and `Blocked by Head Unit` tracking model.
- 2026-02-26: Documented available testing hardware access (Pi over SSH, phone over ADB).
- 2026-02-26: Requested head-unit pairing/discovery to provide stable `vehicle_id` so companion-side identity resolution can use deterministic IDs instead of SSID-only matching.
- 2026-02-26: Prodigy head-unit pairing/discovery is now active with stable `vehicle_id` emission; companion-side parser and pairing path consume it as available.
- 2026-02-27: Reframed SOCKS5 bridging milestone as "companion/device control-plane validated; deterministic Pi desktop/system routing is still a pending validation and tuning area."
- 2026-07-06: Began Companion External API v1 migration foundation against the frozen protobuf contract while preserving legacy `9876` runtime behavior.
- 2026-07-06: First Pixel app-bound External API v1 live probe confirmed AP reachability and legacy `9876`, but v1 ports `9810` and `9811` refused connections.
- 2026-07-06: After deploying updated Pi software, Pixel app-bound probes confirmed v1 TCP `9810` and WebSocket TCP `9811` accept connections; invalid known-client TCP auth still closed before a terminal auth/error frame.
- 2026-07-06: Updated Companion workflow memory to use Superpowers, not GSD, and marked External API v1.1 parity fields plus proxy-route teardown behavior as available on the deployed head-unit software.
- 2026-07-07: Migrated Companion theme/wallpaper transfer from legacy `9876` chunks to the delivered web-config HTTP install endpoint.
- 2026-07-13: Cut the foreground runtime and manual PIN pairing over to
  Wi-Fi-bound External API v1 TCP `9810`, added one-time deletion of legacy
  vehicle records, removed the legacy `9876` client/protocol structurally, and
  left v1 QR pairing dormant pending a future approved payload/UX slice.
