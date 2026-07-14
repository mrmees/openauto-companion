# External API v1 QR Pairing Implementation Plan

**Goal:** Activate zero-input QR pairing over the existing External API v1 TCP
handshake without removing manual pairing.

**Design:** See
`docs/superpowers/specs/2026-07-13-api-v1-qr-pairing-design.md`.

## Tasks

- [x] Replace the dormant legacy QR parser with defensive `prodigy://pair`
  parsing for host, TCP/WebSocket ports, PIN, and percent-decoded SSID.
- [x] Add focused parser coverage for additive fields and malformed inputs.
- [x] Carry the scanned TCP port through pairing, vehicle persistence, Wi-Fi
  monitor startup, and the foreground runtime transport.
- [x] Preserve TCP `9810` as the default for manual and existing pairings.
- [x] Preserve structured External API error codes through the handshake client
  and normalize pairing-window-closed failures for the UI.
- [x] Add a secondary `Scan QR` action and route valid scans into the existing
  pairing coordinator.
- [x] Release camera resources on scanner exit and remove raw QR/PIN logging.
- [x] Run focused unit tests.
- [x] Run the full repository verification gate.
- [x] Confirm the deployed Prodigy payload, percent-encoding edge, and typed
  `ERROR_CODE_PAIRING_WINDOW_CLOSED` contract match Companion unit coverage.
- [x] Bench physical QR pairing and saved-client reconnect successfully.
- [x] Receive the typed closed-window frame over the deployed remote TCP path;
  the fixed server now delivers code `5` before clean close and Companion shows
  the specific retryable message.
- [x] Bench the final QR and expired-window flows with the updated Prodigy
  build, then record live results.

## Live acceptance

- Closed/no-window attempt: PASS on the Pixel against the deployed Prodigy
  service; typed `ERROR_CODE_PAIRING_WINDOW_CLOSED` mapped to the intended UI.
- Head-unit External API page QR -> Companion camera -> READY: PASS with no
  manual SSID, endpoint, or PIN entry.
- Persistence and saved-client reconnect after force-stop/relaunch: PASS.
- Final status: Connected; Time, GPS, Battery, and SOCKS5 reports Active.
