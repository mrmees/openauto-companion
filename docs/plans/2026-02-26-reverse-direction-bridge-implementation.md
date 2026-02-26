# Reverse Bridge Direction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

## Goal

Enable companion-initiated internet bridging where the Raspberry Pi provides upstream connectivity to the phone when the phone itself lacks data access, while keeping default behavior as phone-to-headunit sharing when available.

## Acceptance Criteria

- No active Android Auto media stream is interrupted by bridge direction transitions or reconnect attempts.
- Feature remains safe when phone has no upstream and when both upstream paths are briefly unavailable.
- State transitions are deterministic and visible to users in all expected failure modes.

## Scope and Status

- Scope: Cross-App (companion-side controls + head-unit feature dependency)
- Status: Draft
- Dependency check:
  - Need head-unit support for reverse-path request + endpoint negotiation.
  - Need clarity on policy for auth, route selection, and teardown.

## Head-Unit Dependency Block

- Need: reverse-path endpoint/command contract and runtime status events from head unit.
- Why: companion cannot safely drive Pi-to-phone routing without server-side support and explicit state reporting.
- Companion impact: local UI and orchestration can be prepared, but final control flow is blocked until head-unit API behavior is known.
- Status: Open

## Task 1: Define Protocol Contract

**Files:** docs + companion protocol modules.

1. Request head-unit API contract for:
   - start reverse bridge request
   - stop reverse bridge request
   - current route/health status
2. Capture required parameters (token/session id, target network mode, fallback behavior).
3. Define validation and timeout expectations for each endpoint.

2. Model capability flags so companion can hide the feature unless capability is advertised.

## Task 2: App-Level State and Intent Flow

**Files:** connection/service state layer.

1. Add bridge direction enum/state: `PHONE_TO_HEADUNIT`, `PI_TO_PHONE`, `AUTO`, `OFF`.
2. Add command transitions with guard rails:
   - refuse PI_TO_PHONE when phone already has validated internet and no override.
   - refuse when target head unit lacks reverse capability.
3. Add explicit user action + state persistence for chosen direction.

## Task 3: User Controls and Status

**Files:** status/control screens.

1. Add control surface for reverse direction enablement only when capability is present.
2. Show simple state indicator:
   - Inactive
   - Connecting
   - Active
   - Failed with retry action
3. Add one-tap stop action for quick tear-down.

## Task 4: Error Handling and Safety

1. Add timeout and retry policy for reverse start/stop calls.
2. Add explicit warning for metered/low-confidence network states.
3. Prevent both bridge directions from being active simultaneously.

## Task 5: Validation

1. Unit tests for direction state transitions and guard rules.
2. Manual QA with dummy phone/no-mobile scenario and known-good Pi internet path.
3. Confirm fail-safe behavior:
   - unsupported head unit → control hidden or disabled with explanation
   - route drops → state transitions to failed with one-tap retry
