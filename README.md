# OpenAuto Companion

OpenAuto Companion is the Android companion app for an
[OpenAuto Prodigy](https://github.com/mrmees/openauto-prodigy) head unit. It
securely pairs a phone with the head unit, reconnects when the vehicle Wi-Fi is
available, and supplies the phone-side data and controls used by the in-car
experience.

> [!IMPORTANT]
> This project is in active alpha development. Features, setup, and protocol
> compatibility may change between builds.

## What it does

- Pairs with a Prodigy head unit by QR code, with manual SSID and PIN entry as
  a fallback.
- Reconnects automatically to saved vehicles and shows the current connection
  and sharing state.
- Shares time, GPS location, battery level, and connectivity information over
  Prodigy's authenticated External API v1 connection.
- Opens the head unit's web configuration inside the app over the Android Auto
  Wi-Fi network.
- Builds and installs Material 3 themes and wallpapers from the phone.
- Provides per-vehicle controls for audio keep-alive and experimental SOCKS5
  internet sharing.

OpenAuto Companion works locally with the head unit. It does not require a
cloud account or remote service.

## Requirements

- Android 8.0 (API 26) or newer
- An OpenAuto Prodigy head unit with External API v1 support
- Location and nearby-device permissions for Wi-Fi detection and GPS sharing
- Camera permission for QR pairing (optional when using manual pairing)

## Pairing

1. Open the External API pairing page on the Prodigy head unit and start a
   pairing window.
2. In OpenAuto Companion, choose **Add Vehicle** and scan the displayed QR
   code.
3. Approve the requested Android permissions. The app will save the vehicle
   and reconnect automatically when its Wi-Fi network is available.

If QR pairing is unavailable, enter the head unit's Wi-Fi SSID and six-digit
pairing PIN manually.

## Building from source

The project uses Kotlin, Jetpack Compose, and Gradle. You will need JDK 17 and
the Android SDK 35 toolchain (Android Studio is the easiest way to install
both).

```bash
git clone https://github.com/mrmees/openauto-companion.git
cd openauto-companion
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests and build verification gate with:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Project status

The core API v1 pairing, reconnect, telemetry, web configuration, and theme
flows are implemented and have been tested with a Pixel phone and a Prodigy
head unit. Internet sharing is still being stabilized, especially deterministic
head-unit application routing while Android Auto is active.

See the [project vision](docs/project-vision.md) and
[current roadmap](docs/roadmap-current.md) for the product direction and active
work.
