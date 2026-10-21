# Multi-game Step 34 — Session cleanup and lifecycle failure isolation

## Changes
- Made per-session cleanup idempotent with an atomic guard so competing lifecycle/error paths cannot release the same runtime resources twice.
- Routed MIDlet-constructor initialization failures through the shared cleanup path, including launcher registration, per-classloader maps, Display/event queue, input mappings, keyboard configuration, properties, and session bindings.
- Changed a `pauseApp()` exception in multi-game mode into a slot-scoped failure callback and safe thread shutdown rather than throwing a runtime exception out of the HandlerThread. Legacy single-game behavior continues to report the exception.
- Guarded `destroyApp()` when a session was cancelled before the MIDlet instance existed.

## Validation
- Source-level delimiter checks and ZIP integrity checks were run.
- Android compilation and device tests were not verified in this environment.
