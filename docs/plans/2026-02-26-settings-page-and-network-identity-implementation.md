# Settings Page and Dynamic Identity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

## Goal

Ensure Companion opens the head-unit settings UI using validated runtime endpoint identity and does not depend on hardcoded IP/MAC assumptions.

## Acceptance Criteria

- No active Android Auto media stream is interrupted by identity refresh, endpoint updates, or settings-page launch failures.
- Settings page launch always uses runtime-validated identity and never hardcoded fallbacks in steady state.
- Stale endpoint cases are surfaced without forcing service restarts or disrupting media playback.

## Scope and Status

- Scope: Companion + Cross-App coordination
- Status: Draft
- Dependency check:
  - If runtime endpoint identity is not available from discovery/pairing, head-unit changes are needed.

## Head-Unit Dependency Block

- Need: reliable endpoint advertisement in pairing/discovery payloads for settings host/port and identity refresh.
- Why: companion currently depends on fallback host constants and can drift from runtime network conditions.
- Companion impact: settings navigation and other network operations remain brittle on DHCP/reconfiguration changes.
- Status: Open

## Task 1: Inventory Hardcoded Identity Paths

1. Identify all hardcoded references to `10.0.0.1`, `8080`, and static MAC parsing across settings, bridge, and connection flows.
2. Replace each use with identity sources in precedence order:
   - active discovered peer identity
   - paired metadata
   - explicit user override or pairing payload
3. Remove assumptions that silently resolve to fixed defaults.

## Task 2: Extend Vehicle and Pairing Data Contracts

1. Add explicit validated fields for settings host/port and identity source metadata where needed.
2. Preserve backward compatibility for already paired entries.
3. Add migration logic for records that currently rely on default values.

## Task 3: Settings Launcher Robustness

1. Resolve settings endpoint from validated runtime identity only.
2. Add explicit user feedback when endpoint is unavailable or stale:
   - disable launch button with reason
   - provide refresh/re-pair action
3. Guard against invalid URLs and non-browser handlers.

## Task 4: Discovery and Revalidation

1. Revalidate endpoint identity on reconnect and pairing events.
2. Update stored endpoint values only when validated.
3. Handle stale endpoint case by:
   - pausing auto-open
   - flagging status detail
   - prompting user to re-scan or refresh

## Task 5: Validation

1. Unit tests for endpoint selection precedence and stale identity handling.
2. Manual QA:
   - launch settings with valid runtime identity
   - launch with missing identity -> disabled + remediation
   - network change -> stale endpoint refresh path triggers safely
   - with AA playback active, open settings/refresh identity paths and verify playback remains uninterrupted.
