# Multi-game Step 24 — focused input routing

This step adds a small input router for the multi-game host:

- Each bound slot registers its MIDlet ClassLoader and a key-event target.
- Touch-down and key events from a Canvas update the focused session.
- `MultiGameActivity.dispatchKeyEvent()` offers hardware key events to the focused slot before falling back to normal Activity dispatch.
- Slot unbind removes the session route and selects another registered target if needed.
- Key delivery temporarily restores the owning MIDlet's thread context ClassLoader while resolving its Displayable view.

Limitations:
- The multi-game UI still does not launch four independent MIDlet runtimes, so this routing path is infrastructure until slot launch is connected.
- Hardware keyboard/gamepad behavior and Android view focus must be verified on a device.
- This step does not prove simultaneous multi-game execution or logic-loop behavior at 4 FPS render.
