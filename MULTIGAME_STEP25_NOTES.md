# Multi-game Step 25 — focused slot and key-release safety

## Changes
- `MultiGameInputRouter` now tracks hardware key-down state per ClassLoader.
- Changing the focused slot synthesizes key-up events for keys still held by the old slot, reducing stuck movement/actions after switching games.
- Unregistering a slot releases its outstanding keys before removing the route.
- `KeyTarget` receives focus-change callbacks.
- `MultiGameGridView.SlotHost` selects itself on touch-down, displays an `INPUT` indicator for the active slot, and refreshes the indicator when focus changes.

## Verification
- XML parse and ZIP integrity checks should be run when packaging.
- Android/Gradle compilation and device-level input behavior remain unverified in this environment.
- This step improves input routing only. It does not complete independent four-MIDlet launch or prove four games run simultaneously.
