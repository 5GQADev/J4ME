# Multi-game Step 17 — per-slot Canvas overlay

## Implemented
- Added a per-ClassLoader `OverlayView` binding to `ContextHolder` with `setSessionOverlay`, `getSessionOverlay`, and cleanup in `releaseSession`.
- Each `MultiGameGridView.SlotHost` now owns an overlay surface above its game content and below its header, and binds that overlay to its MIDlet classloader.
- `Canvas.getDisplayableView()` now resolves its Android `Context` through the session binding instead of requiring a `MicroActivity` instance to construct `CanvasView`/`GlesView`.
- Canvas input callbacks and soft-key overlay resolve the overlay for the active session, rather than directly reading `MicroActivity.binding.overlayView`.

## Limits / next work
- This is an isolation foundation, not proof of four-game runtime correctness. `Displayable` widgets and services elsewhere still use legacy global `ContextHolder.getActivity()` paths; virtual keyboard/input routing and lifecycle also need further isolation.
- Build and device execution must be validated in an Android/Gradle environment. Do not treat ZIP/XML checks as a successful APK build.
