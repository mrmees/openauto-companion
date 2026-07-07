# Theme HTTP Install Design

## Goal

Move Companion theme and wallpaper installation from the legacy `9876` theme
chunk protocol to the head-unit web-config HTTP endpoint:

`POST http://<head-unit>:8080/api/theme/install`

This change affects only theme/wallpaper transfer. Legacy `9876` connection,
status reports, display-dimension fallback, SOCKS5 bridge behavior, and other
runtime service behavior remain unchanged.

## Vision Alignment

This change aligns with `docs/project-vision.md` by improving reliability and
surfacing real install failures to the user. The previously blocked web-config
theme/wallpaper endpoint is now delivered by the head unit, so Companion can
remove theme transfer from the legacy socket without waiting on External API v1
blob support.

The implementation must preserve Android Auto stream continuity by avoiding any
changes to active media, SOCKS5, or status-report loops.

## Current Behavior

`ThemeTransfer.kt` currently sends themes over an active `PiConnection` on TCP
`9876`:

- require a legacy session key
- build a legacy `theme` JSON message
- base64 encode wallpaper bytes into 64 KiB `theme_data` chunks
- send all messages through `PiConnection.sendStatus`
- wait for a legacy `theme_ack`

The service refuses theme transfer when the legacy connection is not active, and
the UI treats `theme_ack.accepted=true` as success.

## Target Behavior

`ThemeTransfer.kt` becomes an HTTP installer built on OkHttp. It sends one
multipart request to the web-config endpoint:

- `manifest`: plain form field, no filename, value is the existing theme JSON
  string
- `wallpaper`: optional file part named `wallpaper.jpg`, content type
  `image/jpeg`

The existing theme-building JSON stays camelCase and continues to include the
generated `name`, `seed`, `light`, and `dark` values. No color-role conversion
is done in Companion.

Wallpaper bytes are omitted when empty so color-only themes can use the same
installer. The current UI only enables send after a cropped wallpaper exists;
supporting color-only UI creation can be a later product change.

## Architecture

Keep the boundary in `org.openauto.companion.net.ThemeTransfer` because callers
already consume its `TransferResult` and this is still theme transport logic.

Introduce a host-based send API:

```kotlin
fun send(
    host: String?,
    themeJson: JSONObject,
    wallpaperBytes: ByteArray?,
    client: OkHttpClient = OkHttpClient()
): TransferResult
```

The function normalizes a blank host to `10.0.0.1`, always uses port `8080`,
and posts to `/api/theme/install`.

`CompanionService` tracks the current vehicle web-config host from the start
intent. `sendTheme` resolves that host and calls the HTTP installer on the
existing theme executor. It no longer requires an active `PiConnection` solely
for theme installation.

`MainActivity` and `ThemeBuilderScreen` can keep their existing callback and
result mapping unless implementation shows a small signature cleanup is needed.

## Error Handling

HTTP response handling uses the response body as source of truth:

- `200` plus `{"installed":true,...}` maps to `TransferResult.Success`
- any response with `installed:false` maps to `TransferResult.Failed(error)`
- `400` and `413` are reported directly as payload problems
- `503` is reported as retry-later head-unit unavailability
- `500` is reported as a head-unit import problem
- malformed response JSON maps to an invalid-response failure
- network exceptions map to a transfer failure with the exception message

The UI already displays failed reasons from `TransferResult.Failed`.

## Testing

Replace legacy chunking-focused `ThemeTransferTest` coverage with MockWebServer
tests that verify:

- requests use `POST /api/theme/install`
- multipart body contains `manifest` as a form field with no filename
- multipart body contains `wallpaper` with filename and `Content-Type:
  image/jpeg` when wallpaper bytes are present
- wallpaper is omitted when bytes are absent or empty
- `200 installed=true` returns success
- `400`, `413`, `500`, `503`, `installed=false`, malformed JSON, and network
  failures return failed results with useful messages

Keep existing `ThemeGeneratorTest` coverage for the generated camelCase M3
roles.

## Documentation Updates

Update `docs/project-vision.md` to mark the web-config theme/wallpaper upload
dependency as delivered.

Update `docs/roadmap-current.md` so the External API migration foundation no
longer says theme transfer depends on a future endpoint.

Append a session handoff after implementation with changed files, status,
verification, and next steps.

## Verification

Before completion, run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

If device hardware is available, optional live validation is a manual
follow-up: send a test theme to `10.0.0.1:8080` while the phone is connected to
the head-unit AP and confirm the head unit applies it.
