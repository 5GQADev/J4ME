# Multi-game Step 14 — connect grid visibility to Canvas render policy

## Implemented

- `MultiGameGridView` now owns a `MultiGameRenderController`.
- When a slot's `Displayable` becomes a `Canvas`, the slot registers that Canvas with the controller. Replacing it with a non-Canvas or unbinding the slot unregisters the previous Canvas and resets its cap.
- Entering the 2x2 grid applies the configured 4 FPS presentation limit to registered canvases.
- Maximizing one slot removes the cap for that slot while retaining the 4 FPS cap on other registered canvases; restoring the grid reapplies the grid policy.
- The controller changes only Canvas presentation policy; it does not sleep, pause, or change priority of the MIDlet thread.

## Scope / limitations

This step wires the existing FPS policy into the grid view lifecycle. It does **not** make the app launch four MIDlets. `MultiGameActivity` still has no per-slot runtime creation pipeline, and `ContextHolder`, input, overlays, and parts of `MicroActivity` retain process-global assumptions. Those must be addressed before claiming concurrent multi-game execution.

## Verification

- Source-level checks confirm the grid registers Canvas instances on displayable changes, unregisters them on unbind, and switches policy on maximize/restore.
- ZIP integrity and manifest/layout XML parsing are checked when packaging.
- Full Android build is not claimed unless Gradle can resolve its wrapper distribution and dependencies in the build environment.
