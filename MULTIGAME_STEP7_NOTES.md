# Multi-game Step 7 — per-MIDlet lifecycle routing

This step builds on the Step 6 source and makes MIDlet-originated lifecycle callbacks session-aware.

## Changes

- `MidletThread` now maintains a registry keyed by each MIDlet's `ClassLoader`.
- `MIDlet.notifyPaused()`, `MIDlet.notifyDestroyed()`, and `MIDlet.resumeRequest()` route to the thread associated with that MIDlet's loader.
- The handler thread adopts the MIDlet classloader as its context classloader after MIDlet construction, improving per-session resource/event context.
- Destroying one MIDlet releases only that MIDlet's `Display` and registry entry.
- Removed `Process.killProcess()` and the delayed force-kill timer from normal MIDlet destruction, so one game ending no longer intentionally terminates the whole Android process.
- Existing no-argument host methods remain as a compatibility fallback for the original single-game `MicroActivity` flow.

## Important limitations

This is a runtime isolation step, not the completed four-window feature. `MicroActivity`, `ContextHolder`, `ViewHandler`, input dispatch, and the visible view hierarchy still have process-wide/single-activity assumptions. The 2x2 game selection UI, independent per-slot rendering surfaces, and slot-specific input/focus routing are not yet implemented. Do not treat this archive as a verified multi-game APK.

## Build status

The Gradle wrapper could not run because the environment could not resolve `services.gradle.org` to download Gradle 7.3.3. Compilation and device testing remain unverified.
