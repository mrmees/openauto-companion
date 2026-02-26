# Companion vs Head Unit Boundary Design

## Goal

Establish a strict product-boundary baseline so this repository remains focused on the Prodigy Companion Android app, while head-unit changes are requested and developed separately.

## Canonical Policy Location

- Canonical source of truth: `docs/project-vision.md`
- Rationale: product boundaries should live in product vision, not execution-only instructions.

## Policy

- This repository owns only Companion app development.
- Head unit app development is out of scope for this repository.
- If a Companion goal depends on head-unit behavior, that dependency must be explicitly tracked as a blocker.

## Blocker Tracking Model

In `docs/project-vision.md`, maintain a `Blocked by Head Unit` section.

Each blocker entry requires:
- `Need`
- `Why`
- `Companion impact`
- `Status`

Allowed status values:
- `Open`
- `Requested`
- `In Progress`
- `Delivered`
- `Not Needed`

## Decision Rule

- Default assumption: solve in Companion app only.
- Declare a head-unit dependency only when companion-only options are exhausted or clearly impossible.
- When declared, create one blocker entry and move it through the status lifecycle.

## Workflow Impact

- For any behavior-changing feature, run a boundary check first.
- If no head-unit dependency: proceed normally.
- If head-unit dependency exists: track it under `Blocked by Head Unit`, complete companion-side work up to the boundary, and stop at the blocker.

## Approved

Approved during collaborative goal-setting on 2026-02-26.
