# Multi-Headunit Session Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

## Goal

Provide deterministic handling when multiple head units are present, including explicit active-device selection, per-device session state, and safe reconnect behavior while avoiding cross-device ambiguity.

## Acceptance Criteria

- No active Android Auto media stream is interrupted by device switching, selection updates, or reconnect attempts.
- Active-device selection is explicit and persistent; operations never act on an unselected head unit.
- Recovery paths complete without requiring app restarts after stale-session events.

## Scope and Status

- Scope: Companion
- Status: Draft
- Dependency check:
  - Companion discovery + pairing data currently assumes single active target in several paths.
  - Head-unit changes may be optional, but any identity model assumptions must align with emitted discovery data.

## Task 1: Inventory and Normalize Device Identity Inputs

**Files:** companion state and discovery/pairing modules.

1. Inventory all head-unit identity fields used for connect targets (IP, SSID, pin, persisted names, optional MAC).
2. Define a canonical `vehicleSessionKey` (or equivalent) and map current persistence to it.
3. Add a migration-safe fallback for entries that currently rely on implicit position/order.

## Task 2: Add Active Selection State

**Files:** connection state store, UI model, and status/vehicle screens.

1. Store one explicit `activeVehicleId` in app state.
2. Ensure all connect/disconnect actions always resolve via `activeVehicleId`.
3. Persist active selection and restore deterministically across process restarts.

## Task 3: Build Deterministic Multi-Device Flow

**Files:** connection orchestration layer and navigation entry points.

1. When multiple candidates are available, expose an explicit selection UI or list action before starting bridge/session actions.
2. Prevent auto-connect to stale non-selected devices after app restart or network changes.
3. Add a short conflict banner when multiple compatible devices are discovered and no active choice exists.

## Task 4: Session Reconcile and Recovery

**Files:** service orchestrator and background lifecycle handlers.

1. Persist per-device last-known state and last-error for display.
2. On reconnect attempts, verify the intended target matches active selection before sending operations.
3. On mismatch, stop and request user reconfirmation with one-tap switch.

## Task 5: UX and Guardrails

1. Add session list/selector affordance near connection status.
2. Show explicit selected device name/identity in status text.
3. Add recovery actions:
   - “Forget this head unit”
   - “Remove stale sessions”

## Task 6: Validation

1. Unit tests for selection resolution and session key migration.
2. Manual QA:
   - Pair two head units, verify explicit selection is required.
   - Switch active head unit and confirm operations route only to selected target.
   - Simulate stale/disconnected entry and confirm safe recovery path.
   - During an active AA media session, switch head unit selection or reconnect flow and verify playback is uninterrupted.
