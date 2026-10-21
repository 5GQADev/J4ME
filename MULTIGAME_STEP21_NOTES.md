# Multi-game Step 21 — per-Canvas graphics configuration snapshot

## Changes

- Converted Canvas graphics options into process-wide defaults used only when a Canvas instance is constructed.
- Each Canvas snapshots bitmap filtering, touch input, graphics mode, shader settings, parallel redraw, fullscreen preference, FPS display/limit, background color, scaling mode/ratio/gravity, and raw screenshot mode.
- Each Canvas also snapshots the configured virtual width and height, so a later configuration update does not resize an already-created Canvas through the shared `Displayable` fields.
- Runtime rendering, input hit-testing, GL setup, scaling, background clearing, and FPS display now read the instance snapshot rather than repeatedly consulting mutable global defaults.
- Existing static setters remain as a compatibility layer for the current single-game launcher.

## Limitations

- `MicroLoader.applyConfiguration()` still sets global defaults before a Canvas is created. This snapshot reduces cross-talk after construction but does not make concurrent runtime initialization race-free; a future session launcher must supply settings directly to each session before its Canvas is constructed.
- Other process-wide systems (including Font settings, key mapping, EventQueue mode, M3G state, storage, permissions, and lifecycle/UI integrations) are not fully isolated.
- This is source-level work. APK compilation and four-game device testing remain unverified in this environment.
