# AGENTS.md

## Project Management Loop

For behavior-changing work in this repository:

1. Check alignment with `docs/project-vision.md` before implementation.
2. Update `docs/roadmap-current.md` when priorities or sequencing change.
3. Before claiming completion, run:
   - `./gradlew :app:testDebugUnitTest :app:assembleDebug`
4. Append a handoff entry to `docs/session-handoffs.md` including:
   - what changed
   - why
   - status
   - next 1-3 steps
   - verification commands/results

## Scope Note

This local file defines repo-specific workflow expectations. Platform-level safety and skill instructions still apply.
