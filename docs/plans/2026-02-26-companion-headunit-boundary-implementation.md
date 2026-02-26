# Companion and Head Unit Boundary Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Operationalize the companion-only scope policy so future feature work consistently tracks and respects head-unit dependencies.

**Architecture:** Keep `docs/project-vision.md` as the canonical policy and add lightweight enforcement touchpoints in planning and handoff docs. Use docs-first checks instead of code-level automation to maintain low process overhead.

**Tech Stack:** Markdown docs, git workflow, session handoff discipline.

---

### Task 1: Add a Reusable Blocker Entry Template in Vision

**Files:**
- Modify: `docs/project-vision.md`

**Step 1: Add a concrete blocker example format under `Blocked by Head Unit`**

```markdown
- Need: <what head-unit capability is required>
- Why: <why companion-only solution is insufficient>
- Companion impact: <what is blocked or partially deliverable>
- Status: Open|Requested|In Progress|Delivered|Not Needed
```

**Step 2: Verify section clarity**

Run:
```bash
sed -n '1,260p' docs/project-vision.md
```
Expected: policy and template are clear without additional interpretation.

**Step 3: Commit**

```bash
git add docs/project-vision.md
git commit -m "docs: add reusable blocked-by-head-unit entry template"
```

### Task 2: Align Active Roadmap with Boundary Policy

**Files:**
- Modify: `docs/roadmap-current.md`

**Step 1: Add a `Dependency Check` line to active SOCKS5 roadmap item**

Ensure the item states that any head-unit dependency must be logged in `Blocked by Head Unit`.

**Step 2: Verify roadmap references canonical policy location**

Run:
```bash
rg -n "Blocked by Head Unit|project-vision" docs/roadmap-current.md
```
Expected: roadmap includes explicit pointer to canonical policy.

**Step 3: Commit**

```bash
git add docs/roadmap-current.md
git commit -m "docs: align roadmap with companion-headunit boundary checks"
```

### Task 3: Add Session Handoff Prompt for Dependency Decisions

**Files:**
- Modify: `docs/session-handoffs.md`

**Step 1: Extend handoff template with one optional prompt**

Add:
```markdown
- Dependency decision:
  - Companion-only: Yes/No
  - If No, reference `Blocked by Head Unit` entry
```

**Step 2: Verify template remains concise**

Run:
```bash
sed -n '1,320p' docs/session-handoffs.md
```
Expected: template still copy/paste-friendly (<2 min to fill).

**Step 3: Commit**

```bash
git add docs/session-handoffs.md
git commit -m "docs: add dependency decision prompt to session handoff template"
```

### Task 4: Verification and Readiness Check

**Files:**
- None (validation only)

**Step 1: Validate policy is referenced by all management artifacts**

Run:
```bash
rg -n "Blocked by Head Unit|companion-only|project-vision" docs/project-vision.md docs/roadmap-current.md docs/session-handoffs.md
```
Expected: references present in all intended docs.

**Step 2: Validate clean git state**

Run:
```bash
git status -sb
git log --oneline -n 8
```
Expected: clean working tree with traceable documentation commits.
