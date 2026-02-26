# Project Management System Design

## Goal

Establish a lightweight, repeatable management system for a solo developer that keeps product/design intent current across sessions while maintaining delivery speed.

## Selected Operating Mode

- Mode: Balanced
- Rationale: maintain enough rigor to prevent drift across sessions without introducing heavy ceremony that slows solo development.

## Scope

- Applies to behavior-changing work by default.
- Non-behavior changes (refactors with no behavior impact, docs-only edits, formatting) can use a lighter path and do not require a session handoff entry unless risk is non-trivial.

## Core Artifacts

### 1) Vision Document

- File: `docs/project-vision.md`
- Purpose: durable source of truth for product direction.
- Contents:
- Product intent and user outcomes
- Design principles and constraints
- Explicit non-goals
- Change log section for major directional updates

### 2) Current Roadmap

- File: `docs/roadmap-current.md`
- Purpose: active priorities and sequencing.
- Contents:
- `Now / Next / Later` sections
- Priority ordering with short rationale
- Explicit deferred items and why

### 3) Session Handoffs (Append-Only)

- File: `docs/session-handoffs.md`
- Purpose: continuity between sessions with minimal overhead.
- Required for: behavior-changing sessions.
- Entry template:
- Date/time
- What changed
- Why it changed
- Current status (done / in progress / blocked)
- Next 1-3 steps
- Verification commands and results

## Operating Workflow

### Intake and Alignment

1. For each behavior-changing request, quickly map task to `project-vision.md`.
2. If priorities or sequencing changed, update `roadmap-current.md` before implementation.

### Design and Planning

1. If work introduces behavior or architecture changes, add a design note in `docs/plans/YYYY-MM-DD-<topic>-design.md`.
2. For multi-step work, create implementation plan in `docs/plans/YYYY-MM-DD-<topic>-implementation.md`.

### Implementation

1. Prefer small, reviewable commits.
2. Use test-first or test-aligned changes when practical.
3. Keep scope tight to approved goal (YAGNI).

### Completion Gate (Required)

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

No “done” claims without successful verification output.

### Session Handoff (Required for Behavior Changes)

Append one entry to `docs/session-handoffs.md` with the defined template.

## Guardrails

- Vision drift prevention: no behavior change lands without explicit check against `project-vision.md`.
- Priority drift prevention: roadmap must reflect active order of operations.
- Session continuity: every behavior-changing session leaves a handoff trail.
- Evidence-first completion: verification commands are mandatory before success claims.

## Success Criteria

- New session can resume work in under 5 minutes using roadmap + latest handoff.
- Behavior-changing tasks always include a verification record.
- Product direction and non-goals remain explicit and current.

## Out of Scope

- Heavy workflow tooling, issue tracker migration, or CI policy overhaul.
- Mandatory handoffs for purely non-behavioral edits.
