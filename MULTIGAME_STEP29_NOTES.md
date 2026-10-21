# Multi-game Step 29 — M3G state scoped per MIDlet

## Changes

- Replaced the process-wide `javax.microedition.m3g.Graphics3D` singleton with a weakly keyed registry indexed by the current thread context ClassLoader.
- Replaced the process-wide M3G `Interface` singleton with a per-ClassLoader registry, so native object handle-to-Java-object tracking is no longer deliberately shared between MIDlet sessions.
- `MicroLoader.releaseSessionBindings()` now releases the M3G registry for the finished session without resetting another session's Graphics3D singleton.
- The legacy single-game path still uses the current context ClassLoader as its registry key and `initGraphics3D()` only resets that key.

## Validation and limits

- XML parse and ZIP integrity checks are run for this archive; Java source brace-balance is checked for the edited files.
- Full Android compilation and device tests remain unverified because this environment cannot fetch Gradle 7.3.3 from `services.gradle.org`.
- This isolates Java-side M3G singleton/registry state only. Native `javam3g` global state and other process-wide runtime services may still conflict, so four-game M3G concurrency is not claimed as verified.
