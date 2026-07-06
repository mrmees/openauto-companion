# Project Vision

## Product Intent

OpenAuto Companion is an Android app that securely connects a phone to an OpenAuto Prodigy head unit and continuously shares runtime data needed for in-vehicle experience features (time, GPS, battery, and optional internet/audio support).

## Primary User Outcomes

- Pair a vehicle quickly (manual entry or QR scan).
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
- No-stream interruption: companion networking behavior must not interrupt active Android Auto media streams.

## Constraints

- Android app using Kotlin + Jetpack Compose.
- Companion protocol currently targets head unit host `10.0.0.1` over companion socket `9876`.
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

- Web-config theme/wallpaper upload endpoint for legacy `9876` retirement.
  - Need: head-unit web-config HTTP endpoint for theme JSON plus wallpaper multipart upload/install.
  - Why: External API v1 deliberately excludes theme/wallpaper blobs and has a 256 KiB frame cap; HTTP is the approved channel.
  - Companion impact: theme transfer must stay on legacy `9876` until the HTTP endpoint contract ships and is integrated.
  - Status: Requested

- External API v1.1 additive fields needed for full legacy-retirement parity.
  - Need: `SystemStatus` display dimensions, `TimeReport.timezone_id`, and `ServerHello.server_id`.
  - Why: Companion currently gets display dimensions from legacy `hello_ack`, legacy status carries phone timezone, and every head unit AP uses `10.0.0.1` before auth without a stable server identity in v1.
  - Companion impact: v1 foundation can proceed, but final legacy retirement and some multi-headunit/display fallbacks remain gated.
  - Status: Requested

- External API v1 listener availability on the head-unit AP.
  - Need: head unit must accept External API v1 TCP on `10.0.0.1:9810` and/or WebSocket on `10.0.0.1:9811`.
  - Why: Companion can reach the Pi AP and legacy `9876`, but first app-bound v1 live validation from Pixel received `ECONNREFUSED` on both v1 ports.
  - Companion impact: service report cutover and live pairing validation remain blocked beyond local protocol/transport tests until a deployed head-unit build exposes a v1 listener.
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
