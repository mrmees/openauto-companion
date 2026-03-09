# Theme Builder & Wallpaper Transfer — Design

## Summary

Add a native Material You theme builder to the companion app. Users pick a wallpaper image, the app extracts a seed color and generates a full Material 3 color scheme, and the theme + wallpaper are transferred to the head unit over the existing authenticated socket connection.

## User Flow

1. User opens Theme Builder screen (available when connected to a head unit)
2. Picks an image from the device gallery
3. Crops the image to the head unit's display aspect ratio (learned from `hello_ack`)
4. App extracts top 5 dominant colors from the cropped image
5. Most dominant color is pre-selected as the seed; user can pick an alternative or choose a custom color
6. Material 3 palette (light + dark) is generated live from the seed
7. Preview shows wallpaper + palette swatches + a mini head unit mockup
8. User names the theme (auto-default: "Theme from [date]")
9. User taps "Send to Head Unit"
10. Theme JSON + wallpaper image bytes transferred over socket
11. Success/failure shown to user

## Protocol Changes

### Display resolution in hello_ack

Extend the existing `hello_ack` message:

```json
{
  "type": "hello_ack",
  "accepted": true,
  "session_key": "...",
  "display": { "width": 1024, "height": 600 }
}
```

Companion stores display dimensions per-vehicle. Falls back to 1024x600 if the field is missing (older head unit versions).

### New message: theme

Sent from companion to head unit. Contains the full color scheme and metadata about the wallpaper that follows.

```json
{
  "type": "theme",
  "theme": {
    "version": 1,
    "name": "Midnight Blue",
    "seed": "#1A237E",
    "light": {
      "primary": "#6750A4",
      "onPrimary": "#FFFFFF",
      "primaryContainer": "#EADDFF",
      "onPrimaryContainer": "#21005D",
      "secondary": "#625B71",
      "onSecondary": "#FFFFFF",
      "secondaryContainer": "#E8DEF8",
      "onSecondaryContainer": "#1D192B",
      "tertiary": "#7D5260",
      "onTertiary": "#FFFFFF",
      "tertiaryContainer": "#FFD8E4",
      "onTertiaryContainer": "#31111D",
      "error": "#B3261E",
      "onError": "#FFFFFF",
      "errorContainer": "#F9DEDC",
      "onErrorContainer": "#410E0B",
      "background": "#FFFBFE",
      "onBackground": "#1C1B1F",
      "surface": "#FFFBFE",
      "onSurface": "#1C1B1F",
      "surfaceVariant": "#E7E0EC",
      "onSurfaceVariant": "#49454F",
      "outline": "#79747E",
      "outlineVariant": "#CAC4D0",
      "inverseSurface": "#313033",
      "inverseOnSurface": "#F4EFF4",
      "inversePrimary": "#D0BCFF",
      "surfaceDim": "#DED8E1",
      "surfaceBright": "#FFFBFE",
      "surfaceContainerLowest": "#FFFFFF",
      "surfaceContainerLow": "#F7F2FA",
      "surfaceContainer": "#F3EDF7",
      "surfaceContainerHigh": "#ECE6F0",
      "surfaceContainerHighest": "#E6E0E9"
    },
    "dark": {
      "primary": "#D0BCFF",
      "onPrimary": "#381E72",
      "primaryContainer": "#4F378B",
      "onPrimaryContainer": "#EADDFF",
      "secondary": "#CCC2DC",
      "onSecondary": "#332D41",
      "secondaryContainer": "#4A4458",
      "onSecondaryContainer": "#E8DEF8",
      "tertiary": "#EFB8C8",
      "onTertiary": "#492532",
      "tertiaryContainer": "#633B48",
      "onTertiaryContainer": "#FFD8E4",
      "error": "#F2B8B5",
      "onError": "#601410",
      "errorContainer": "#8C1D18",
      "onErrorContainer": "#F9DEDC",
      "background": "#1C1B1F",
      "onBackground": "#E6E1E5",
      "surface": "#1C1B1F",
      "onSurface": "#E6E1E5",
      "surfaceVariant": "#49454F",
      "onSurfaceVariant": "#CAC4D0",
      "outline": "#938F99",
      "outlineVariant": "#49454F",
      "inverseSurface": "#E6E1E5",
      "inverseOnSurface": "#313033",
      "inversePrimary": "#6750A4",
      "surfaceDim": "#1C1B1F",
      "surfaceBright": "#3B383E",
      "surfaceContainerLowest": "#0F0D13",
      "surfaceContainerLow": "#1C1B1F",
      "surfaceContainer": "#211F26",
      "surfaceContainerHigh": "#2B2930",
      "surfaceContainerHighest": "#36343B"
    }
  },
  "wallpaper": { "format": "jpeg", "size": 184320, "chunks": 3 },
  "mac": "..."
}
```

29 color roles per mode — the full Material 3 system color set.

### New message: theme_data

Wallpaper image sent as base64-encoded chunks (~64KB per chunk).

```json
{
  "type": "theme_data",
  "chunk": 0,
  "data": "<base64 encoded chunk>",
  "mac": "..."
}
```

### New message: theme_ack

Head unit confirms receipt.

```json
{
  "type": "theme_ack",
  "accepted": true
}
```

All messages are HMAC-signed with the session key, consistent with existing protocol.

## Companion App UI

### ThemeBuilderScreen

New Compose screen accessible from the status screen when connected to a head unit.

**Layout (top to bottom):**

1. **Wallpaper section** — "Choose Wallpaper" button → system image picker → crop tool locked to head unit aspect ratio (width:height from `hello_ack`)
2. **Seed color bar** — 5 tappable circles showing dominant colors extracted via `androidx.palette`. Most dominant pre-selected. "Custom" circle opens a full color picker (hue wheel + saturation/brightness).
3. **Palette preview** — Grid of labeled Material 3 color swatches. Light/dark toggle or tabs. Updates live when seed changes.
4. **Wallpaper + theme preview** — Mini mockup of the head unit screen with wallpaper and UI element overlays tinted in theme colors.
5. **Name field** — Text input for theme name. Auto-default: "Theme from [date]".
6. **Apply button** — "Send to Head Unit". Progress indicator during transfer, then success/failure.

### Navigation

- Entry point: button on StatusScreen (only enabled when connected)
- Back navigation returns to StatusScreen

## Dependencies

New libraries to add:

| Library | Purpose |
|---------|---------|
| `com.google.material:material-color-utilities` | Seed → full M3 scheme generation (same algorithm as Material Theme Builder website) |
| `androidx.palette:palette-ktx` | Dominant color extraction from bitmaps |
| Image cropping library (TBD — e.g. `com.vanniktech:android-image-cropper`) | Aspect-ratio-locked wallpaper cropping |

## Data Storage

- Display resolution stored per-vehicle in `CompanionPrefs` (new fields on Vehicle)
- No theme persistence on the companion side in v1 — theme is generated and sent, not saved locally

## Scope

### In scope
- Wallpaper picker + aspect-ratio crop
- Dominant color extraction + seed selection
- M3 palette generation (light + dark, 29 roles each)
- Theme naming
- Preview UI
- Theme + wallpaper transfer over authenticated socket
- Display resolution exchange via `hello_ack`
- Protocol messages: `theme`, `theme_data`, `theme_ack`

### Out of scope
- Head unit theme application (Prodigy/Qt side)
- Head unit theme persistence
- Dark/light mode switching logic on the head unit
- Multiple saved themes on the companion
- Theme sharing/export between users
- Theme presets or bundled themes
- Editing an already-sent theme (regenerate and resend instead)
