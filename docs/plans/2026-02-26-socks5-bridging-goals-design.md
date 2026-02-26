# SOCKS5 Bridging Goals Design

## Objective (Next 60 Days)

Deliver SOCKS5 internet bridging between phone and head unit with a balanced execution style: ship usable connectivity quickly, then harden for real drive stability.

## Approved Priorities

- Primary outcome: SOCKS5 bridging of internet access from phone to head unit.
- Success tier targeted now: 
  - Functional browsing works through bridge.
  - Stable operation for typical drives without manual restart.
- Deferred: full production polish and deep diagnostics beyond immediate stability essentials.

## Goal Strategy

### Phase 1: MVP Connectivity

- Prove end-to-end SOCKS5 path from head unit through phone network.
- Acceptance: head unit can browse common web pages through bridged connection.

### Phase 2: Drive Stability

- Harden reconnect, timeout handling, and long-session operation.
- Acceptance: stable drive session(s) without manual restart, including disconnect/reconnect recovery.

### Phase 3: Production Hardening (Later)

- Improve diagnostics and edge-case handling after MVP + stability are validated.

## Measurable Success Criteria

### Connectivity Acceptance

- Head unit resolves DNS and opens at least 3 mainstream HTTPS sites via the bridge.

### Stability Acceptance

- At least one 30+ minute drive session without manual restart.
- Automatic reconnect succeeds after at least one disconnect/reconnect event.

### Safety Acceptance

- Verification gate remains green:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

### Observability Minimum

Logs must clearly indicate:
- Connection attempt start
- Authentication success/failure
- SOCKS5 proxy start state
- Failure reason when a connect path fails

## Execution Architecture (Balanced)

Track a single focused roadmap item: `SOCKS5 Bridging` with two workstreams.

### Workstream A: Functional Path

- Validate/repair routing and auth path until browsing works reliably in controlled testing.

### Workstream B: Stability Path

- Improve reconnect and timeout behavior for sustained sessions and real-drive reliability.

## Workflow Contract for Behavior Changes

For each behavior-changing step:
- Align scope with `docs/project-vision.md`
- Update `docs/roadmap-current.md` if priority/sequence changes
- Run verification gate before completion claims
- Append entry to `docs/session-handoffs.md`

## Validation Sequence

1. Lab check: browse 3 sites through bridge
2. Drive check: 30+ minute stable session
3. Regression check: unit/build gate
