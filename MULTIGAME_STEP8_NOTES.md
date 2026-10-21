# Multi-game Step 8 — Session view-host routing

This step adds a per-MIDlet view-host contract in `javax.microedition.lcdui.Display`.

- `Display.SessionViewHost.onDisplayableChanged(previous, next)` receives display changes on the Android UI thread.
- `Display.SessionViewHost.onAlertRequested(alert)` routes alert presentation to the session's host.
- Hosts are registered using `Display.registerSessionHost(midlet, host)` and detached using either `unregisterSessionHost(midlet, host)` or `unregisterSessionHost(host)`.
- `MicroActivity` implements the contract so existing single-game behavior continues to use its current displayable container.
- `MidletThread` registers the context as host after MIDlet instantiation; `Display.releaseDisplay` clears the per-session mapping.

## Important limitation

This does not yet create the 2x2 grid or provide four independent Android view containers. `MicroActivity` still has one `current` field, and `ContextHolder` still has process-global activity, keyboard, vibration, and overlay state. Do not interpret this step as proof that four games can already run concurrently.

## Verification

- Source whitespace check: passed (`git diff --check`).
- ZIP integrity: checked after packaging.
- Gradle/Android compilation: not verified because Gradle 7.3.3 could not be obtained from `services.gradle.org` in this environment.
