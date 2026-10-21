# Multi-game Step 32 — defer MIDlet start until the slot host is ready and foregrounded

## Changes

- Split `MidletThread` construction from queuing its `INIT` message.
- Preserve the legacy single-game order by assigning `instance` before starting initialization.
- Register a multi-game host/thread pair before queuing `INIT`, closing the window where Stop or Activity lifecycle callbacks could miss a newly created session.
- Remember Activity pause/resume state on a pending session even if its MIDlet has not yet been constructed or attached to the slot.
- Gate `START` until the slot host callback has completed and while the multi-game Activity is backgrounded.
- Have each slot propagate pause/resume to both the pending-session registry and the MIDlet owner when one exists.

## Validation and limits

- XML parsing and source-structure checks are run for this archive; ZIP integrity is checked after packaging.
- Android Gradle compilation is not claimed unless it succeeds in the current environment.
- No emulator/device run is available here. The change still needs runtime tests for launch/background/resume/stop races.
- This does not prove four simultaneous MIDlets are stable or that every game's logic remains unaffected by thumbnail render throttling.
