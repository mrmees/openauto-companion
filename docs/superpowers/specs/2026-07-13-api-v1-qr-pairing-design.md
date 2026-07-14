# External API v1 QR Pairing Design

## Goal

Activate the dormant Companion QR scanner so a phone already connected to the
Prodigy AP can pair without typing while preserving manual PIN pairing.

## Approved QR Contract

```text
prodigy://pair?host=10.0.0.1&tcp=9810&ws=9811&pin=123456&ssid=OpenAutoProdigy-A3F2
```

`host`, `tcp`, `ws`, `pin`, and `ssid` are required. Ports must be in
`1..65535`, the PIN must be exactly six ASCII digits, and the SSID must be
nonblank after URI query decoding. Unknown query parameters are ignored so the
contract can grow additively.

The scanner must never log the raw QR value or PIN.

## Pairing and Persistence

The QR route feeds the existing External API v1 pairing coordinator. The
coordinator resolves the Android Wi-Fi network using the QR SSID and host, then
connects to the QR `host:tcp` endpoint. The challenge/response handshake and
credential derivation are unchanged.

The paired vehicle stores the TCP endpoint with the same client id, secret,
server id, host, and SSID already saved by manual pairing. Existing/manual
records default to TCP `9810`. Runtime reconnect uses the stored TCP port.
WebSocket remains parsed and validated but is not selected because Companion's
approved runtime transport is TCP.

## UI and Failure Handling

`PairingScreen` keeps manual SSID/name/PIN entry and adds a secondary `Scan QR`
action. Scanning a valid payload immediately begins pairing. Cancellation
returns to manual entry.

Prodigy emits `Error{code: ERROR_CODE_PAIRING_WINDOW_CLOSED, message: "Pairing
window closed"}` with the `ClientHello.request_id` echoed, then closes cleanly.
Companion matches the typed code and treats the following close as expected
teardown. A compatible `AuthReject` reason remains a defensive fallback for
older builds. Invalid QR values remain in the scanner without invoking pairing.
Camera setup failures surface in the scanner instead of leaving a permanent
preview/progress state.

The live 2026-07-13 bench received EOF before a frame prefix on both request id
`0` and protocol-correct request id `1`. The typed contract remains the target,
but completion requires the deployed remote socket path to flush it before
teardown.

## Acceptance

- A valid QR pairs without typed input and starts the normal runtime.
- The scanned host and TCP port are used for pairing and later reconnects.
- A closed pairing window produces a clean retryable message.
- Manual pairing still defaults to `10.0.0.1:9810`.
- QR payloads and PINs do not appear in logs.
- Unit tests and `./gradlew :app:testDebugUnitTest :app:assembleDebug` pass.
