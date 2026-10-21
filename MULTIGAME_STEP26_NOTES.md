# Multi-game Step 26 — cancel touch/virtual-keyboard state on focus changes

## Changes

- `Canvas` now tracks active pointer IDs and their last valid MIDP coordinates.
- `Canvas.cancelSessionInput()` cancels the current overlay (including virtual-keyboard pressed-key state) and posts `POINTER_RELEASED` events for active MIDP pointers.
- `MultiGameGridView.SlotHost.onInputFocusChanged(false)` calls the cancellation method before the slot loses input focus, preventing stuck touch directions when the user taps another slot.
- Android `ACTION_CANCEL` uses the same cancellation path.

## Validation

- Parsed all XML files under `app/src/main`: 104 files passed.
- Reviewed modified Java source for balanced braces and patch placement.
- ZIP integrity check passed after packaging.
- Android/Gradle compilation and device runtime testing are not confirmed in this environment. This step does not claim four independent MIDlets are already running simultaneously.
