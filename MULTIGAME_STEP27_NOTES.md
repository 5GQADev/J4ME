# Multi-game Step 27 — per-slot MIDlet launch path

## Implemented

- Added `MultiGameSessionLauncher`, which loads the saved profile and descriptor for a converted slot package and creates a separate `MidletThread` for its selected MIDlet entry.
- Connected each slot's Launch button to run the MIDlet inside its own `SlotHost`; the same button requests destruction when that slot is running.
- `MicroLoader` now has a session initialization path that does not call the legacy `Display.initDisplay()`, reset the process-wide M3G singleton, or clear the shared cache directory.
- A slot host is registered by ClassLoader before MIDlet construction, then bound to the constructed MIDlet on the Android main thread before `startApp()` is sent.
- Added per-ClassLoader MIDlet descriptor properties so two packages cannot overwrite one another's `MIDlet.getAppProperty()` values.
- Each slot gets its own dex optimization directory; starting a later game no longer clears the same optimization directory used by an already-running game.
- The per-slot virtual keyboard is attached/removed on the UI thread and is cleaned up with the session.
- MIDlet initialization/start failures are reported to the slot UI, and slot destruction releases the Display, scoped properties, Canvas config, key mappings and Context bindings.

## Validation

- Parsed all 104 XML files successfully.
- Checked brace balance for all 574 Java source files; no structural imbalance found.
- Attempted `bash gradlew :app:compileDebugJavaWithJavac --offline`; Gradle Wrapper still needs Gradle 7.3.3 from `services.gradle.org`, but DNS/network is unavailable in this environment.
- No Android device/emulator run was available, so runtime behavior and 4-game concurrency remain unverified.

## Known limitations / risks

- The runtime still contains other process-wide services, notably M3G `Graphics3D`, parts of resource/file-connection handling, and some font/configuration state. Games that depend on these may interfere with one another.
- This step selects the first MIDlet entry in a multi-MIDlet JAR; a per-slot MIDlet chooser is not implemented.
- 4 FPS throttling applies to rendering/presentation, not a guarantee of game-specific logic behavior. Device testing is still required to prove all four game loops continue correctly.
