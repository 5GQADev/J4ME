# Multi-game Step 22 — per-session Canvas configuration

## Changes
- Added `SessionCanvasConfig`, an immutable snapshot of the profile fields used to initialize a Canvas.
- Register the snapshot for the MIDlet's defining ClassLoader before invoking its constructor, in both loader branches.
- Canvas now prefers the configuration registered for its current thread context ClassLoader and falls back to the legacy static defaults for the single-game player.
- Release the snapshot when the MIDlet session is destroyed.

## Scope and limitations
- This prevents a later call to process-wide Canvas setters from changing the settings captured by a Canvas that is created under the correct session ClassLoader.
- It does not remove the global setters from `MicroLoader.applyConfiguration()` and does not make all runtime state session-safe. ClassLoader context must be propagated to threads that construct Canvas instances.
- Four-slot launch wiring and device verification are still outstanding. Source and ZIP checks do not prove concurrent runtime behavior.
