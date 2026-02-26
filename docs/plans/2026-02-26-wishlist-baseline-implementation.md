# Wishlist Baseline Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Operationalize an unranked mixed-scope wishlist with a lightweight promotion workflow that preserves companion/head-unit boundaries.

**Architecture:** Keep `docs/wishlist.md` as the capture inbox, tie promotion rules to existing governance docs (`project-vision`, `roadmap-current`, `session-handoffs`), and avoid heavy process tooling. Enforce consistency with documentation-only checkpoints.

**Tech Stack:** Markdown docs, git workflow.

---

### Task 1: Add Workflow Rules to Wishlist File

**Files:**
- Modify: `docs/wishlist.md`

**Step 1: Add explicit “How to use this file” section**

Include:
- capture-only statement
- no execution authorization
- promotion requirement before implementation

**Step 2: Add promotion decision table**

Define:
- `Scope: Companion` -> roadmap/plan path in this repo
- `Scope: Head Unit` and `Scope: Cross-App` -> update `Blocked by Head Unit` in `docs/project-vision.md`

**Step 3: Validate readability**

Run:
```bash
sed -n '1,260p' docs/wishlist.md
```
Expected: rules are clear in <2 minutes.

**Step 4: Commit**

```bash
git add docs/wishlist.md
git commit -m "docs: add wishlist usage and promotion workflow rules"
```

### Task 2: Link Wishlist in Roadmap Governance

**Files:**
- Modify: `docs/roadmap-current.md`

**Step 1: Add one governance line referencing wishlist intake**

Include short policy:
- new ideas captured in `docs/wishlist.md`
- roadmap only contains promoted items

**Step 2: Verify references**

Run:
```bash
rg -n "wishlist|promoted|roadmap" docs/roadmap-current.md
```
Expected: governance line present.

**Step 3: Commit**

```bash
git add docs/roadmap-current.md
git commit -m "docs: link roadmap governance to wishlist intake"
```

### Task 3: Add Handoff Prompt for Wishlist Promotions

**Files:**
- Modify: `docs/session-handoffs.md`

**Step 1: Extend handoff template with optional promotion note**

Add:
```markdown
- Wishlist promotion:
  - Source item: <title or n/a>
  - Promotion result: Promoted / Not promoted
```

**Step 2: Validate template remains concise**

Run:
```bash
sed -n '1,220p' docs/session-handoffs.md
```
Expected: still quick to fill.

**Step 3: Commit**

```bash
git add docs/session-handoffs.md
git commit -m "docs: add wishlist promotion prompt to session handoffs"
```

### Task 4: End-State Validation

**Files:**
- None (validation only)

**Step 1: Cross-doc consistency check**

Run:
```bash
rg -n "wishlist|Blocked by Head Unit|promotion|Scope" docs/project-vision.md docs/roadmap-current.md docs/session-handoffs.md docs/wishlist.md
```
Expected: policy references are consistent and non-contradictory.

**Step 2: Git state check**

Run:
```bash
git status -sb
git log --oneline -n 8
```
Expected: clean working tree after task commits and clear traceability.
