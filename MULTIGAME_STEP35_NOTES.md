# Multi-game Step 35 — render-policy transition safety

## Scope

This step hardens presentation throttling when a slot switches between 2x2 grid mode (4 FPS) and maximized mode (unrestricted presentation).

## Changes

- Reset the non-GL flush throttle window whenever a Canvas render policy changes.
- Version each non-GL delayed flush with a policy generation; callbacks from an earlier policy are ignored, including rapid 4 -> 0 -> 4 transitions.
- Reset the GL renderer's throttle window on policy changes and version delayed render callbacks so stale callbacks cannot interfere with a newer policy.
- Keep the policy limited to presentation requests; this code does not sleep, skip, or change the MIDlet/game logic thread.

## Validation

Source-level structural checks and ZIP integrity checks were run. Android compilation and device-level frame-rate/gameplay tests were not available in this environment, so runtime FPS and four-game behavior remain unverified.
