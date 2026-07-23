# Secure Pairing Credential Upgrade — Design

Date: 2026-07-22
Status: COMPLETED 2026-07-22
Grounded on: `origin/main` at `1a7c985761992501547e8547e94ca4f3d838b93d`
Coordinated head-unit baseline: `openauto-prodigy`
`42f6aa4344fec17f275122cdf76ced8a6fb3b369`

## Goal

Move Companion and Prodigy from offline-enumerable six-digit pairing secrets to
an explicitly versioned 120-bit random pairing code while preserving QR as the
normal zero-input path and manual entry as a fallback. Retire existing weak
credentials once, pair again, and keep all runtime/reporting behavior unchanged.

This is the Companion half of Prodigy's active API/core lifecycle wave. Each
repository has its own branch, commits, tests, review, and draft pull request;
the protocol and deployment are coordinated.

## Contract

- The head unit generates 24 RFC 4648 Base32 characters (`A-Z2-7`), 120 random
  bits. Canonical form is uppercase without separators; display form is six
  groups of four.
- QR payloads use `code=`. Companion rejects the former `pin=` payload rather
  than silently treating six digits as the new contract.
- Manual entry accepts lower/upper case plus spaces or hyphens and normalizes
  through the same pure helper used by QR pairing.
- Secret derivation remains `SHA256(code_utf8 || salt)` and proof remains
  `HMAC-SHA256(secret, nonce)`. `PairingChallenge.secret_format` at additive
  field 7 must be `BASE32_120` before Companion derives or sends a proof.
- `Capabilities` field 13 confirms secure-code support on the resulting
  session. The duplicated proto file stays byte-identical to Prodigy's.
- Stored vehicles carry credential generation 2. Missing/older generations are
  weak credentials and are retired by storage migration; they do not reach the
  runtime reconnect path.
- Prodigy also rejects legacy credentials with an additive typed
  `AuthReject.code` at field 6. Human-readable reasons are not the migration
  control surface.

The longer manual code is an intentional security/usability tradeoff: QR is the
primary product path, and 120 random bits clears NIST SP 800-63B's current
112-bit random-value strength guidance without introducing a custom PAKE.

## Branch and Existing Runtime Fix

The branch starts from `origin/main`, not from the long-lived `dev` branch.
Commit `1d1487919a397a233a9624acaacd574d28bfce44` is nevertheless carried as an
explicit first commit because it is already live-validated and prevents the new
APK from regressing airplane-mode recovery. No other unrelated `dev` history is
included.

## Acceptance Criteria

- QR and manual inputs normalize to the same 24-character canonical code.
- Invalid alphabet, wrong length, old `pin=` payloads, absent/legacy/unknown
  challenge formats, and malformed endpoints fail before proof transmission.
- Kotlin and C++ derive the same 32-byte secret for shared vectors.
- A successful pairing stores generation 2 with the granted client ID and
  secret; only generation-2 records enter the runtime.
- Migration retires pre-upgrade credentials deterministically and idempotently,
  causing the existing pairing screen to be shown when no secure records remain.
- QR scan reaches READY, saved-client reconnect reaches READY, and time/GPS/
  battery/connectivity reporting plus SOCKS settings behave as before.
- The already validated per-attempt Wi-Fi network re-resolution behavior remains
  present in the built APK.

## Out of Scope

- Supporting new six-digit pairings, transparently upgrading a weak secret,
  cloud/account pairing, TLS, or implementing a PAKE.
- Head-unit lifecycle fixes outside the matching Prodigy branch.
- SOCKS routing feature work, settings WebView work, theme transfer, notification
  features, Android Auto transport, Bluetooth pairing, or HFP behavior.

## Verification and Live Matrix

### Required locally

- Pairing URI, crypto, handshake, session-client, coordinator, credential-store,
  vehicle serialization/migration, and runtime tests.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- `git diff --check`, proto parity with Prodigy, and a repository review with
  every finding adjudicated.

### Required after coordinated deployment

- Install the reviewed APK without clearing unrelated phone data.
- Confirm the legacy record is retired and the app enters pairing.
- Scan the physical Prodigy QR, reach READY, then force-stop/relaunch and prove
  saved-client reconnect without manual entry.
- Confirm time/GPS/battery/connectivity reports and configured SOCKS state
  resume while Android Auto remains connected.

### Optional

- Pair once through grouped manual code entry.
- Repeat on a second phone.

### Not required

- Bluetooth re-pairing/restart, HFP calls, deliberate head-unit clock stepping,
  AA protocol capture, or unrelated Companion feature validation.

Matthew has pre-approved scoped device deployment and draft-PR publication for
the completed wave.
