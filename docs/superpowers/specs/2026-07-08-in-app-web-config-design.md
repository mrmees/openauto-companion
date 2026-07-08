# In-App Web Config Design

## Goal

Open the head-unit web config page from Companion even when Android routes normal browser traffic over cellular while Android Auto owns the Prodigy Wi-Fi network.

## Context

Pixel diagnostics showed the phone associated to the Prodigy AP as `10.0.0.21/24`, forced Wi-Fi ping to `10.0.0.1` succeeded, and unbound traffic to `10.0.0.1` routed over cellular. Theme upload works because Companion binds its OkHttp client to the matched Wi-Fi `Network`. The existing settings action launches an external browser, which cannot use Companion's bound socket or process routing.

## Design

Replace the external browser settings action with an in-app WebView screen. When the screen is shown, Companion asks `WifiMonitor` for the matched Wi-Fi `Network`, temporarily binds the app process to that network, and loads the web-config URL from `SettingsUrlBuilder`. On exit, the screen restores the previous process binding.

The screen stays intentionally small: a Material top app bar with a back action, a progress indicator while pages load, inline load-error text, and the WebView filling the rest of the screen. The WebView enables JavaScript and DOM storage because the web-config panel is a local app UI, not a static document.

## Error Handling

If no matched Wi-Fi network is available, the screen still opens and attempts the configured URL, but shows a short warning that Android may route it incorrectly. If process binding fails, it shows the same warning and continues loading. WebView navigation errors surface as an inline message with the failing URL.

## Testing

Unit tests cover URL selection for the in-app web-config entry point and process-binding restoration through a small testable coordinator. Full WebView behavior is verified with `:app:assembleDebug` and on-device install because local JVM tests cannot execute Android WebView networking.
