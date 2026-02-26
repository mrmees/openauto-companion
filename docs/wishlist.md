# Project Wishlist

Unranked idea pool for future work. Capture quickly; do not treat this file as execution-ready scope.

## How To Use This File

- This is a capture inbox, not an implementation queue.
- Adding an item here does not authorize engineering work.
- Items must be promoted into roadmap/plans before implementation.

## Promotion Rules

| Item Scope | Promotion Path |
| --- | --- |
| `Companion` | Move into `docs/roadmap-current.md`, then create/update a plan in `docs/plans/` before implementation. |
| `Head Unit` | Create or update `Blocked by Head Unit` in `docs/project-vision.md`; do not proceed past companion-side boundary until dependency status advances. |
| `Cross-App` | Create or update `Blocked by Head Unit` in `docs/project-vision.md`; split execution so companion work continues only where independent. |

## Entry Template

```markdown
## <Short idea title>

- Idea: <what we want>
- Value: <why it matters>
- Dependencies: <systems/constraints/unknowns>
- Scope: Companion | Head Unit | Cross-App
```

## Items

<!-- Add new ideas below using the template above. -->

## SOCKS5 Bridge Warm Start

- Idea: Add a one-tap “Start bridge now” action on Companion that attempts to establish proxy connectivity end-to-end after wake/boot.
- Value: Reduces user friction when the car head unit is already on; improves reliability for recurring drives by making reconnection explicit and fast.
- Dependencies: stable companion-to-headunit signaling path, head unit service state machine to accept resume requests, reliable timeout/error surfaces on failure.
- Scope: Companion

## SOS Tunnel Health Widget

- Idea: Add a compact dashboard tile showing local proxy/route status and last-5-minute connectivity trend.
- Value: Gives users immediate confidence that phone-to-headunit internet sharing is actually active before they begin navigation.
- Dependencies: head-unit metrics endpoint for current route and policy; Companion-safe polling interval and local storage for short history.
- Scope: Cross-App

## Bridge Credential Rotation UX

- Idea: Define a simple flow to rotate/share SOCKS credentials securely if a session fails or is exposed.
- Value: Improves security and recoverability, especially in shared vehicles or long-lived installations.
- Dependencies: head unit key/credential rotation API support, secure storage in companion app, explicit user confirmation UX.
- Scope: Cross-App

## Offline-to-Online Bridge Resume

- Idea: Add logic to queue a reconnect when the phone comes back online so the bridge resumes automatically within a bounded delay.
- Value: Better resilience after phone wake/sleep and cellular handoff events; fewer manual retries.
- Dependencies: reliable foreground/background lifecycle handling, network-state callback behavior on target Android versions, head unit readiness feedback.
- Scope: Companion

## Bridge Health Event Timeline

- Idea: Record notable proxy/connection events (start, stop, auth fail, route unavailable, recovery attempts) in a local timeline.
- Value: Supports faster troubleshooting during support calls and reduces ambiguous "it didn’t work" states.
- Dependencies: event schema on companion, non-PII log policy, optional export path.
- Scope: Cross-App

## Explicit Stop + Drain Behavior

- Idea: Provide an explicit “Stop bridge now” with controlled teardown and optional keep-alive suppression to reduce battery/heat.
- Value: Gives users control in public/shared environments and supports safe shutdown before parking or parking lots.
- Dependencies: companion-side state machine, head unit cleanup handshake, persisted user preference sync.
- Scope: Companion

## Head Unit Tunnel Error Surface

- Idea: Extend head unit error reporting to include actionable categories (DNS fail, proxy auth fail, upstream blocked, route refused).
- Value: Makes Companion UI recovery suggestions accurate and reduces blind retries when the failure is known.
- Scope: Head Unit

## Guided Companion Onboarding

- Idea: Add a first-run onboarding flow that verifies permissions, pairability, and connectivity prerequisites before enabling full features.
- Value: Reduces support friction and ensures users do not start with an unsupported configuration.
- Dependencies: permission prompts, test-and-connect workflow, user-friendly error copy for denied permissions.
- Scope: Companion

## Permission & Capability Health Check

- Idea: Add a diagnostics screen showing status of required system permissions (location, VPN/service/background), with one-tap remediation.
- Value: Prevents silent failures and makes operational readiness visible at a glance.
- Dependencies: Android permission management APIs, non-blocking checks for optional permissions, onboarding handoff copy.
- Scope: Companion

## Stable Foreground Service + Recovery

- Idea: Implement explicit foreground-service lifecycle management with automatic rebind/restart behavior when Android kills the process.
- Value: Keeps bridge features usable on long drives and avoids losing state on OS lifecycle events.
- Dependencies: lifecycle handling strategy, notification UX, platform-specific battery/policy constraints.
- Scope: Companion

## Connection State Machine

- Idea: Introduce a single source of truth for bridge state (idle, connecting, active, degraded, failed) with deterministic transitions and user-visible status.
- Value: Prevents ambiguous behavior and supports clear UX, retries, and telemetry hooks.
- Dependencies: existing connection implementation refactor, centralized state model, persisted session intent.
- Scope: Companion

## Network Change Awareness

- Idea: Add adaptive behavior for Wi-Fi/Cellular transitions (auto-pause, auto-resume, and failover messaging).
- Value: Improves robustness when phone network changes during active use.
- Dependencies: connectivity callbacks, policy for retry/backoff, head unit readiness signals.
- Scope: Companion

## Offline Diagnostics Capture

- Idea: Store a bounded local history of connection events and errors with export/share support for support tickets.
- Value: Enables troubleshooting without remote logs and shortens debugging turnaround.
- Dependencies: retention policy, redaction rules, optional export integration.
- Scope: Companion

## User-Facing Error Language + Recovery Guide

- Idea: Replace generic failure toasts with actionable messages plus next-step buttons (retry, check settings, open system settings).
- Value: Improves usability and trust by turning failures into specific actions.
- Dependencies: error taxonomy, localized string set, UI actions wired to app/system intents.
- Scope: Companion

## App Update and Compatibility Readiness

- Idea: Add a lightweight compatibility-and-version check flow for app updates and required OS/API-level constraints.
- Value: Reduces runtime incompatibility and keeps users on supported feature paths.
- Dependencies: release metadata source, compatibility matrix, migration behavior for changed settings.
- Scope: Companion

## Battery & Data Guardrails

- Idea: Provide user-configurable conserves for background activity, reconnect frequency, and metering visibility.
- Value: Helps users balance bridge reliability with battery/data consumption in real-world usage.
- Dependencies: settings persistence, policy model in background tasks, documented defaults.
- Scope: Companion

## Bidirectional Internet Bridging (Pi-to-Phone)

- Idea: Support an alternate bridge direction where a Raspberry Pi can provide internet to the phone when the phone has no active mobile/data link.
- Value: Covers uncommon but practical vehicle setups (dummy phone, Pi with known-good internet path, passenger scenarios).
- Dependencies: reverse-routing/proxy path in head unit app, policy for route conflict resolution, secure session establishment and teardown.
- Scope: Cross-App

## Multi-Headunit Session Management

- Idea: Add explicit selection and robust per-device state handling when multiple head units exist in discovery/pairing history.
- Value: Avoids ambiguity and ensures predictable behavior when users encounter more than one head unit, even if only one is active at a time.
- Dependencies: discovery identifiers, session persistence, conflict resolution UX for stale or duplicate devices.
- Scope: Companion

## Open Head Unit Settings Page

- Idea: Add a one-touch entry point to open the Pi head unit web settings/configuration page from Companion.
- Value: Reduces friction for advanced option changes and shortens setup/debug sessions without switching contexts.
- Dependencies: discoverable settings endpoint URL, secure deep-link/open-in-browser handling, connectivity checks and fallback behavior.
- Scope: Companion

## Optional Debug Logging Bundle

- Idea: Provide user-selectable app logging with an export package that includes companion-side and head-unit-reported debug context.
- Value: Speeds troubleshooting and support triage while keeping verbose logs off by default for normal users.
- Dependencies: consent-aware collection, event schema agreement, redaction policy, export transport UX.
- Scope: Cross-App

## Remove Hardcoded Network Identity

- Idea: Eliminate assumptions around fixed IP/MAC addresses by resolving all peer addresses dynamically and persisting only validated runtime identity.
- Value: Improves reliability across networks, DHCP changes, and device replacement; reduces setup breakage.
- Dependencies: discovery protocol updates, discovery cache invalidation policy, robust fallback and validation path.
- Scope: Cross-App
