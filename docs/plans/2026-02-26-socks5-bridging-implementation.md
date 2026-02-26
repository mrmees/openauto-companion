# SOCKS5 Bridging Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Achieve verified end-to-end SOCKS5 internet bridging from phone to head unit with stable reconnect behavior for drive sessions.

## Acceptance Criteria

- No active Android Auto media stream is interrupted during bridge startup, reconnect, or stop operations.
- End-to-end browse traffic continues through bridge for primary success case.
- Failure and retry behavior leaves existing AA sessions intact and visible.

**Architecture:** Tighten the bridge path in three layers: (1) connection/auth observability in `PiConnection` + `CompanionService`, (2) SOCKS5 runtime correctness and toggling behavior, and (3) explicit manual validation for browsing and drive stability. Use small TDD loops around parser/connection/proxy helpers and keep behavior gates green after each task.

**Tech Stack:** Kotlin, Android Service lifecycle, JVM unit tests (JUnit4), Gradle (`:app:testDebugUnitTest`, `:app:assembleDebug`), Markdown docs.

---

### Task 1: Baseline and Observability Test Coverage

**Files:**
- Modify: `app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/PiConnection.kt`
- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`

**Step 1: Write failing tests for failure-reason helpers**

Add/extend tests for:
- `decodeHexKey("")` returns null
- `decodeHexKey` rejects odd-length/invalid hex (if not already complete)
- `shouldFallbackToUnboundSocket` returns true for nested-cause EPERM message

```kotlin
@Test
fun shouldFallbackToUnboundSocket_checksNestedCause() {
    val cause = RuntimeException("Operation not permitted")
    val err = RuntimeException("socket bind failed", cause)
    assertTrue(shouldFallbackToUnboundSocket(err))
}
```

**Step 2: Run targeted test to verify red/green behavior**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PiConnectionParsingTest"
```
Expected: fail before implementation change if new case is missing.

**Step 3: Implement minimal helper adjustments in `PiConnection.kt`**

Ensure helper logic handles nested causes consistently and keeps failure reasons actionable.

**Step 4: Add explicit connect failure logging in `CompanionService.kt` if missing any branch**

Guarantee all connect failure paths log `lastFailureReason` or exception message.

**Step 5: Re-run targeted tests**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PiConnectionParsingTest"
```
Expected: PASS.

**Step 6: Commit**

```bash
git add app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt \
        app/src/main/java/org/openauto/companion/net/PiConnection.kt \
        app/src/main/java/org/openauto/companion/service/CompanionService.kt
git commit -m "test: harden pi connection fallback and failure-reason coverage"
```

### Task 2: SOCKS5 Runtime Correctness and State Reporting

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/Socks5Server.kt`
- Test: `app/src/test/java/org/openauto/companion/net/Socks5ServerTest.kt`

**Step 1: Write failing test(s) for SOCKS5 server state transitions**

Cover start/stop idempotency and active-state correctness (including disabled path).

**Step 2: Run targeted tests to verify failure**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.Socks5ServerTest"
```
Expected: FAIL for new scenario(s).

**Step 3: Implement minimal runtime fix**

Adjust `Socks5Server` and/or `CompanionService.startSocks5` so active-state and logging are accurate for start/stop/failure flows.

**Step 4: Re-run targeted tests**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.Socks5ServerTest"
```
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/service/CompanionService.kt \
        app/src/main/java/org/openauto/companion/net/Socks5Server.kt \
        app/src/test/java/org/openauto/companion/net/Socks5ServerTest.kt
git commit -m "fix: stabilize socks5 runtime state transitions and logging"
```

### Task 3: Reconnect Stability Behavior

**Files:**
- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`
- Modify: `app/src/main/java/org/openauto/companion/service/WifiMonitor.kt`
- Test: `app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt` (or new focused unit test)

**Step 1: Add failing tests around reconnect-related helper behavior**

If direct service tests are not practical in JVM, extract pure helpers for retry/backoff decisions and test those.

**Step 2: Run targeted tests to verify fail state**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PiConnectionParsingTest"
```
(or new test class)

**Step 3: Implement minimal reconnect hardening**

Ensure retry scheduling avoids duplicate loops and resets state correctly on reconnect success.

**Step 4: Re-run targeted tests**

Run same command; expected PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/org/openauto/companion/service/CompanionService.kt \
        app/src/main/java/org/openauto/companion/service/WifiMonitor.kt \
        app/src/test/java/org/openauto/companion/net/PiConnectionParsingTest.kt
git commit -m "fix: harden reconnect stability for socks5 bridging sessions"
```

### Task 4: Lab Validation Checklist for Browsing Acceptance

**Files:**
- Create: `docs/plans/2026-02-26-socks5-bridging-lab-checklist.md`
- Modify: `docs/roadmap-current.md`
- Modify: `docs/session-handoffs.md`

**Step 1: Create deterministic lab checklist**

Include:
- device/network prerequisites
- 3-site browsing check through bridge
- expected logs per stage
- pass/fail capture table

**Step 2: Update roadmap item to reference checklist artifact**

Mark the checklist as the source for MVP connectivity acceptance.

**Step 3: Append handoff entry for behavior-changing validation run**

Include command evidence and observed results.

**Step 4: Commit**

```bash
git add docs/plans/2026-02-26-socks5-bridging-lab-checklist.md \
        docs/roadmap-current.md \
        docs/session-handoffs.md
git commit -m "docs: add socks5 bridging lab validation checklist"
```

### Task 5: End-to-End Verification Gate and Drive Test Readiness

**Files:**
- Modify: `docs/session-handoffs.md`

**Step 1: Run full verification gate**

Run:
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

**Step 2: Record drive-test execution plan in handoff**

Capture:
- route/session duration target (30+ min)
- reconnect trigger scenario
- pass/fail conditions

**Step 3: Validate clean git state**

Run:
```bash
git status -sb
git log --oneline -n 10
```
Expected: clean working tree after commits and clear history.
