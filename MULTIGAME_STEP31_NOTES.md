# Multi-game Step 31 — cancel sessions during startup/teardown

## Changes

- Added a host-to-`MidletThread` registry in `MultiGameSessionLauncher`, so a slot can request cancellation before its MIDlet constructor finishes and before `SlotHost.owner` is assigned.
- Added a session-thread stop request and cleanup of the registry when a session exits or fails initialization.
- Guarded the UI-thread session-attach callback against a startup that has already been destroyed, avoiding late binding of a dead MIDlet after Stop or Activity teardown.
- Changed `SlotHost.stopSession()` to cancel a pending startup as well as a running session.
- Changed `MultiGameActivity.onDestroy()` to request stop for every slot, including slots that are still initializing and therefore have no owner yet.

## Validation and limits

- Source-level checks: XML parsing, Java brace-balance checks, and ZIP integrity.
- Android Gradle compilation is not confirmed in this environment; the Gradle wrapper previously could not download Gradle 7.3.3 from `services.gradle.org` due to network/DNS failure.
- No Android device/emulator test was available. Startup cancellation and lifecycle ordering still need runtime validation.
- This addresses a lifecycle race; it does not prove four concurrent MIDlets are stable or that 4-FPS thumbnails leave every game's logic unaffected.
