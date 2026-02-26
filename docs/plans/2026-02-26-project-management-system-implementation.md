# Project Management System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Install a balanced, low-overhead project management system that preserves vision continuity across sessions and enforces verification for behavior-changing work.

**Architecture:** Add three durable project docs (`project-vision`, `roadmap-current`, `session-handoffs`) and a short workflow contract in repository instructions. Validate by running the existing Gradle verification gate and ensuring the templates are usable for the next task immediately.

**Tech Stack:** Markdown documentation, git workflow, Gradle (`:app:testDebugUnitTest`, `:app:assembleDebug`).

---

### Task 1: Add Vision Source-of-Truth Document

**Files:**
- Create: `docs/project-vision.md`

**Step 1: Write initial vision skeleton**

Add sections:
- Product intent
- Primary user outcomes
- Design principles
- Constraints
- Non-goals
- Direction change log

Include short placeholders to be filled with current known intent from existing docs and commits.

**Step 2: Verify formatting and readability**

Run:

```bash
sed -n '1,240p' docs/project-vision.md
```

Expected: all required headings present; no TODO ambiguity in top-level purpose.

**Step 3: Commit**

```bash
git add docs/project-vision.md
git commit -m "docs: add project vision source of truth"
```

### Task 2: Add Active Roadmap Document

**Files:**
- Create: `docs/roadmap-current.md`

**Step 1: Write roadmap structure**

Create sections:
- `Now`
- `Next`
- `Later`
- `Deferred`

Each item should include one-line rationale and expected outcome.

**Step 2: Seed with current context**

Use latest commits and existing plans to add initial entries, including the recent reliability/settings work.

**Step 3: Validate structure**

Run:

```bash
sed -n '1,260p' docs/roadmap-current.md
```

Expected: every section populated with at least one item or explicit “none currently”.

**Step 4: Commit**

```bash
git add docs/roadmap-current.md
git commit -m "docs: add current roadmap with now-next-later priorities"
```

### Task 3: Add Session Handoff Log with Required Template

**Files:**
- Create: `docs/session-handoffs.md`

**Step 1: Create append-only log template**

Add:
- file purpose and when entries are required
- reusable entry template with fields:
- Date/time
- What changed
- Why
- Status
- Next steps (1-3)
- Verification commands/results

**Step 2: Add first bootstrap entry**

Record this setup session as initial handoff (status: done) with verification evidence.

**Step 3: Validate template usability**

Run:

```bash
sed -n '1,280p' docs/session-handoffs.md
```

Expected: template clear enough to copy/paste in <2 minutes.

**Step 4: Commit**

```bash
git add docs/session-handoffs.md
git commit -m "docs: add session handoff log for behavior-changing work"
```

### Task 4: Encode Workflow Contract in AGENTS Guidance

**Files:**
- Modify: `AGENTS.md`

**Step 1: Add concise “Project Management Loop” section**

Specify:
- Behavior-changing work must check `docs/project-vision.md`
- Update `docs/roadmap-current.md` when priorities shift
- Run required verification gate before completion claims
- Append handoff entry for behavior changes

**Step 2: Keep instructions minimal and non-duplicative**

Ensure changes complement existing skill instructions instead of conflicting with them.

**Step 3: Validate resulting guidance**

Run:

```bash
rg -n "Project Management Loop|project-vision|roadmap-current|session-handoffs" AGENTS.md
```

Expected: all four requirements present exactly once in guidance.

**Step 4: Commit**

```bash
git add AGENTS.md
git commit -m "docs: codify balanced project management loop in agent guide"
```

### Task 5: End-to-End Verification and Final Handoff

**Files:**
- Modify: `docs/session-handoffs.md`

**Step 1: Run mandatory verification gate**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 2: Append final setup handoff entry**

Document completed setup, decisions made (balanced mode, behavior-only handoffs), and next actions.

**Step 3: Validate git state**

Run:

```bash
git status -sb
git log --oneline -n 6
```

Expected: clean working tree after commits; recent commit history includes setup docs.

**Step 4: Optional squash decision (user-directed only)**

If user prefers, squash the documentation commits into one; otherwise keep granular history.
