# Secure Pairing Credential Upgrade — Implementation Plan

Date: 2026-07-22
Status: COMPLETED 2026-07-22
Design: `docs/plans/2026-07-22-secure-pairing-credential-upgrade-design.md`
Base: `origin/main` at `1a7c985761992501547e8547e94ca4f3d838b93d`

## Execution Rules

- Execute one bounded task and one commit at a time; nobody pushes mid-wave.
- `Tier: opus` tasks use `gpt-5.6-terra`.
- The protocol and credential-migration task is `Tier: main`.
- Do not use GSD or `.planning/`; follow this repository's docs-native workflow.
- Keep the proto byte-identical to Prodigy and additive-only.
- New ideas go to `docs/wishlist.md`; they do not expand this plan.

## Task 1: Activate the Companion half of the wave

**Tier:** main

**Files:**

- Add: `docs/plans/2026-07-22-secure-pairing-credential-upgrade-design.md`
- Add: `docs/plans/2026-07-22-secure-pairing-credential-upgrade-plan.md`
- Modify: `docs/roadmap-current.md`

**Acceptance criteria:**

- Both documents are ACTIVE and grounded on `origin/main`.
- The roadmap identifies the coordinated Prodigy dependency and one-time
  credential retirement.

**Test command:** `git diff --check`

**Out of scope:** Behavior, tests, or publication.

## Task 2: Preserve the validated airplane-mode recovery

**Tier:** sonnet

**Files:** Exact patch from
`1d1487919a397a233a9624acaacd574d28bfce44` only.

**Acceptance criteria:**

- The branch contains the already reviewed per-attempt Wi-Fi `Network`
  re-resolution and required-network socket behavior.
- Patch identity matches the existing `dev` commit; no other `dev` changes are
  imported.

**Test command:**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.NetworkSocketFactoryTest"
git diff origin/main..HEAD --check
```

**Out of scope:** Modifying or extending that fix.

## Task 3: Implement the versioned secure-code protocol and storage migration

**Tier:** main

**Files:**

- Modify: `app/src/main/proto/api/api.proto`
- Modify: `app/src/main/java/org/openauto/companion/data/Vehicle.kt`
- Modify: `app/src/main/java/org/openauto/companion/data/VehicleStorageMigration.kt`
- Modify: `app/src/main/java/org/openauto/companion/data/CompanionPrefs.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/PairingUriParser.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiCrypto.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiHandshake.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiPairingCredentialStore.kt`
- Modify: `app/src/main/java/org/openauto/companion/net/api/ApiPairingCoordinator.kt`
- Modify: `app/src/main/java/org/openauto/companion/service/CompanionService.kt`
- Modify: `app/src/test/java/org/openauto/companion/data/VehicleSerializationTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/data/VehicleStorageMigrationTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/PairingUriParserTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiCryptoTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiHandshakeTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiSessionClientTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiPairingCredentialStoreTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiPairingCoordinatorTest.kt`
- Modify: `app/src/test/java/org/openauto/companion/net/api/ApiRuntimeLoopTest.kt`

**Acceptance criteria:**

- One normalization helper accepts exactly 24 Base32 characters after removing
  allowed separators and uppercasing.
- QR requires `code=` and rejects `pin=`.
- Pairing sends no proof unless `secret_format == BASE32_120`.
- Shared vectors produce the same derived secret as Prodigy.
- New pairings persist generation 2. Storage version 2 retires all missing/
  legacy-generation credentials, is idempotent, and excludes them from runtime.
- Existing typed terminal errors, server identity, report gating, saved reconnect,
  and credential-size checks remain unchanged.

**Test command:**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.data.*" --tests "org.openauto.companion.net.PairingUriParserTest" --tests "org.openauto.companion.net.api.*"
```

**Out of scope:** UI composition, head-unit implementation, TLS/PAKE, or a
legacy compatibility mode.

## Task 4: Update QR/manual pairing UI and orchestration

**Tier:** opus

**Files:**

- Modify: `app/src/main/java/org/openauto/companion/ui/MainActivity.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/PairingScreen.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/QrScanScreen.kt`
- Modify: `app/src/main/java/org/openauto/companion/ui/VehicleListScreen.kt`
- Add or modify: focused Compose/pure UI tests under `app/src/test/` where the
  existing harness supports them

**Acceptance criteria:**

- Copy says “pairing code,” not PIN.
- Manual input accepts and displays six four-character groups and enables Pair
  only for a valid canonical code and nonblank SSID.
- QR passes the canonical code to the same coordinator path.
- After migration removes the last legacy credential, launch enters the normal
  pairing screen with no crash or stale runtime attempt.
- Pairing progress, cancellation, duplicate secure SSID handling, and errors
  retain their existing behavior.

**Test command:**

```bash
./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.PairingUriParserTest" --tests "org.openauto.companion.net.api.ApiPairingCoordinatorTest" :app:assembleDebug
```

**Out of scope:** General UI polish or vehicle-management redesign.

## Task 5: Complete local gate and repository review

**Tier:** main

**Files:** No planned changes. Confirmed review findings receive bounded fix
commits with focused verification.

**Acceptance criteria:**

- Proto matches Prodigy byte-for-byte.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` passes.
- `git diff --check` passes.
- The repository review completes and every finding is fixed or dismissed with
  a recorded reason.

**Out of scope:** Unrelated cleanup found during review.

## Task 6: Coordinated device validation and closure

**Tier:** main

**Files:**

- Modify: `docs/roadmap-current.md`
- Modify: `docs/session-handoffs.md`
- Move: both active documents to the repository's historical plan location or
  mark them completed in place, matching its existing convention

**Acceptance criteria:**

- The reviewed APK passes the required live matrix in the design against the
  matching reviewed Prodigy binary.
- Exactly one Companion handoff records the credential migration, re-pair,
  reconnect, reports, AA-continuity result, and review adjudication.
- Plan status becomes `COMPLETED 2026-07-22` and the roadmap reports only
  verified outcomes.
- The completed branch is pushed and a draft PR to `main` is opened under
  Matthew's standing approval.

**Test command:** `git diff --check`

**Out of scope:** Merge, release tag, or store publication.
