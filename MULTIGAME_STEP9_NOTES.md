# Multi-game Step 9 — reusable 2x2 view container

This step adds `javax.microedition.shell.multigame.MultiGameGridView`, a reusable Android view component with four slot hosts. Each slot implements `Display.SessionViewHost`, can be bound to a MIDlet using `bind(slot, midlet)`, routes `Displayable` changes into its own container, and presents alerts using its own Android context. Tapping a slot expands it; tapping again or calling `restoreGrid()` returns to the 2x2 layout. `unbind(slot)` detaches the host without implicitly destroying the MIDlet.

## Integration status

This is the first concrete 2x2 presentation component, but it is not yet wired into the app's main launcher. The current `MicroActivity`/`ContextHolder` and MIDlet startup pipeline still assumes one Activity-wide context and does not yet provide a supported way to start four selected games with fully isolated input, overlay, audio, and lifecycle state. The component must not be presented as a finished four-game feature until those runtime dependencies are refactored and the view is attached by a real multi-session Activity.

## Verification

- `git diff --check` / whitespace checks are run before packaging.
- ZIP integrity is checked after packaging.
- Android/Gradle build is not claimed unless a successful build is available in the environment.
