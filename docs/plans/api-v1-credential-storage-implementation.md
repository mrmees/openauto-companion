# External API v1 Credential Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add per-vehicle External API v1 credential storage and a small pairing-result persistence helper without changing the legacy foreground service path.

**Architecture:** Keep v1 credentials as additive fields on `Vehicle` beside the existing legacy `sharedSecret`. Keep pairing-result persistence in `org.openauto.companion.net.api` so it can consume `ApiSessionClient.ConnectResult.Ready` and write updated vehicle lists through injected persistence callbacks.

**Tech Stack:** Kotlin, org.json, protobuf-javalite, JUnit.

---

## Files

- Modify `app/src/main/java/org/openauto/companion/data/Vehicle.kt`
- Modify `app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt`
- Create `app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt`
- Create `app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt`
- Modify `docs/plans/api-v1-migration.md`
- Modify `docs/roadmap-current.md`
- Modify `docs/session-handoffs.md`

## Task 1: Vehicle v1 Credential Fields

- [x] Write `VehicleSerializationTest` coverage for v1 credential round-trip, legacy migration defaults, and unknown API mode fallback.
- [x] Verify the test fails because `Vehicle.apiClientId`, `Vehicle.apiSecretHex`, and `Vehicle.ApiMode` do not exist.
- [x] Add optional `apiClientId`, `apiSecretHex`, and `apiMode` fields to `Vehicle`.
- [x] Persist JSON keys `api_client_id`, `api_secret_hex`, and `api_mode` only when present or explicitly non-legacy.
- [x] Verify `VehicleSerializationTest` passes.

## Task 2: Pairing Result Persistence Helper

- [x] Write `ApiPairingCredentialStoreTest` coverage for storing successful pairing credentials, rejecting missing credentials, rejecting missing vehicles, and rejecting malformed secret sizes.
- [x] Verify the test fails because `ApiPairingCredentialStore` does not exist.
- [x] Implement `ApiPairingCredentialStore` with injected load/save callbacks.
- [x] Preserve the existing legacy `sharedSecret` and update only the matched vehicle's v1 credential fields.
- [x] Verify `ApiPairingCredentialStoreTest` passes.

## Task 3: Verification and Handoff

- [x] Run targeted tests:
  `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.VehicleSerializationTest" --tests "org.openauto.companion.net.api.ApiPairingCredentialStoreTest"`
- [x] Run repo gate:
  `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [x] Append `docs/session-handoffs.md`.
- [x] Commit the credential storage slice.

## Out Of Scope

- No `CompanionService` v1 report cutover.
- No removal of legacy `9876`.
- No v1.1 fields.
- No theme/wallpaper transfer changes.
- No SOCKS5 behavior change.
