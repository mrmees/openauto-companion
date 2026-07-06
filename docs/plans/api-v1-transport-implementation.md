# External API v1 Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add tested TCP and WebSocket External API v1 transports plus a small session client that performs the v1 handshake and drains incoming messages after READY.

**Architecture:** Keep the transport slice inside `org.openauto.companion.net.api` and keep Android service integration out of scope. `ApiTransport` provides a minimal `connect/send/receive/close` interface, concrete transports use the existing `ApiFrameCodec`, and `ApiSessionClient` owns the handshake plus a post-READY read loop.

**Tech Stack:** Kotlin, coroutines, Java sockets, OkHttp WebSocket, MockWebServer, protobuf-javalite, JUnit.

---

## Files

- Create `app/src/main/java/org/openauto/companion/net/api/ApiTransport.kt`
- Create `app/src/main/java/org/openauto/companion/net/api/ApiTcpTransport.kt`
- Create `app/src/main/java/org/openauto/companion/net/api/ApiWebSocketTransport.kt`
- Create `app/src/main/java/org/openauto/companion/net/api/ApiSessionClient.kt`
- Create `app/src/test/java/org/openauto/companion/net/api/ApiTcpTransportTest.kt`
- Create `app/src/test/java/org/openauto/companion/net/api/ApiWebSocketTransportTest.kt`
- Create `app/src/test/java/org/openauto/companion/net/api/ApiSessionClientTest.kt`
- Modify `app/build.gradle.kts` for OkHttp and MockWebServer
- Modify `docs/session-handoffs.md` after verification

## Task 1: TCP Transport

- [x] Write `ApiTcpTransportTest` with a local `ServerSocket`.
- [x] Verify the test fails because `ApiTcpTransport`/`ApiTransport` do not exist.
- [x] Implement `ApiTransport` and `ApiTcpTransport`.
- [x] Verify `ApiTcpTransportTest` passes.

Covered behaviors:

- host/port configurable
- client sends 4-byte big-endian length-prefixed protobuf frames
- client receives length-prefixed protobuf frames
- oversized advertised frame lengths fail before parsing

## Task 2: WebSocket Transport

- [x] Add OkHttp runtime and MockWebServer test dependencies.
- [x] Write `ApiWebSocketTransportTest` with a local WebSocket server.
- [x] Verify the test fails because `ApiWebSocketTransport` does not exist.
- [x] Implement `ApiWebSocketTransport`.
- [x] Verify `ApiWebSocketTransportTest` passes.

Covered behaviors:

- URL configurable
- client sends one serialized `ApiMessage` per binary WebSocket message
- client receives binary WebSocket messages as `ApiMessage`
- text frames are treated as protocol errors and close the transport

## Task 3: Session Client

- [x] Write `ApiSessionClientTest` with fake in-memory transports.
- [x] Verify the test fails because `ApiSessionClient` does not exist.
- [x] Implement `ApiSessionClient`.
- [x] Verify `ApiSessionClientTest` passes.

Covered behaviors:

- `ClientHello` is the first message sent after transport connect.
- known-client auth echoes the server-provided `AuthRequired.nonce`.
- pairing auth uses the server-provided `PairingChallenge.nonce` and `salt`.
- `AuthReject` and `Error` close the transport and return terminal failure.
- after READY, a coroutine keeps reading and forwards incoming messages to a channel.

## Task 4: Verification and Handoff

- [x] Run targeted transport tests:
  `./gradlew :app:testDebugUnitTest --tests "org.openauto.companion.net.api.ApiTcpTransportTest" --tests "org.openauto.companion.net.api.ApiWebSocketTransportTest" --tests "org.openauto.companion.net.api.ApiSessionClientTest"`
- [x] Run repo gate:
  `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [x] Append `docs/session-handoffs.md`.
- [x] Commit the transport slice.

## Out Of Scope

- No `CompanionService` cutover.
- No v1 credential persistence.
- No pairing UI changes.
- No v1.1 fields.
- No theme/wallpaper HTTP client.
