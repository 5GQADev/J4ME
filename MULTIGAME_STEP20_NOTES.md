# Multi-game Step 20 — MIDlet property isolation

## Changes

- Replaced the unsynchronized global MIDlet property map with concurrent maps.
- Added ClassLoader-scoped MIDlet properties to `MidletSystem`; lookups resolve the calling thread's MIDlet ClassLoader first, then use legacy/global fallbacks.
- Added `MicroLoader.applySessionProperties(ClassLoader)` to copy locale, file-connection paths, custom profile properties, and encoding into a session-scoped property view without changing process-wide `System` properties.
- Registered the scoped properties after a MIDlet's ClassLoader is known and removed them when that session is cleaned up.
- Retained the existing legacy property APIs for the normal single-game player.

## Validation

- XML parse and ZIP integrity checks are separate packaging checks, not proof of runtime correctness.
- Android/Gradle compilation must be verified in an environment with the configured Gradle distribution and Android dependencies available.
- Four-runtime launch, per-session graphics configuration, input routing, and device tests remain unfinished. `MicroLoader.applyConfiguration()` still modifies process-wide graphics and Java system settings and must not be treated as safely callable four times concurrently.
