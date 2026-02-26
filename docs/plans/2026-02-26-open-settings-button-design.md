# Open Settings Page Button Design

## Goal

Add a button in the companion app that opens the head unit web settings page in the phone browser.

## Approved UX

- Screen: `StatusScreen` for a selected vehicle
- Placement: directly under the connection status card
- Style: full-width primary button
- Label: `Open Settings Page`
- Disabled behavior: button remains visible but disabled when disconnected
- Disabled helper text: none

## URL and Data Rules

- URL source priority:
- Per-vehicle `host` and `port` captured from QR pairing payload
- Fallback to `http://10.0.0.1:8080` if host/port is missing
- Button is enabled only when that specific vehicle is currently connected

## Data Model Changes

Extend `Vehicle` with optional settings endpoint fields:

- `settingsHost: String? = null`
- `settingsPort: Int? = null`

Persist these fields in JSON serialization and deserialization.

## Pairing Flow Changes

- Parse `host` and `port` from QR payload (`openauto://pair?...`)
- Save parsed values into the paired `Vehicle`
- Keep existing manual pairing path unchanged (falls back to default URL)

## Runtime Action

On button tap:

1. Build URL from `Vehicle.settingsHost/settingsPort` or fallback
2. Launch `Intent(ACTION_VIEW, Uri.parse(url))`
3. If no browser handler exists, show short user-facing failure message (toast/snackbar)

## Non-Goals

- No global app settings screen for endpoint overrides
- No duplicate button on vehicle list screen
- No additional permissions
