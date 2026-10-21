# Multi-game Step 23 — per-session key mapping

## Changes
- Added a `KeyMapper.SessionMapping` snapshot for each MIDlet ClassLoader. Android-to-MIDP mappings, custom layout conversion, game-action lookup, and key-name lookup are now stored per session rather than relying exclusively on the last globally applied profile.
- Registered the session mapping together with `SessionCanvasConfig` before the MIDlet constructor runs.
- Canvas captures its session ClassLoader at construction and uses it for key conversion and `getKeyCode`, `getGameAction`, and `getKeyName` calls. Key events continue to be delivered by the Canvas/View that received them, so conversion uses that Canvas's profile.
- Released the mapping when `MidletThread` cleans up the corresponding session.
- Kept the legacy static KeyMapper API as fallback for the single-player path and code without a registered session.

## Validation and limitations
- XML parsing and ZIP integrity checks are run for this step.
- Gradle compilation could not be completed in this environment because the wrapper distribution (`gradle-7.3.3`) is not cached and `services.gradle.org` is not resolvable.
- This is a source-level input-isolation improvement, not proof of four concurrent MIDlets. Grid launch wiring, focus/active-slot policy, and device tests remain outstanding.
