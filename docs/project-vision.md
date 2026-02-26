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
