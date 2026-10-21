# Multi-game implementation status

The current work contains a 2x2 slot presentation, per-slot render throttling hooks, session bindings and input routing. Step 27 adds an in-slot launch path using a separate `MicroLoader` and `MidletThread` per slot.

**Not yet verified / incomplete:** successful Android build, full isolation of every process-global runtime service, and device-level simultaneous gameplay. The launch path is implemented but has not been verified on an Android device; do not treat it as a confirmed four-game release.


## Step 26 update — touch cancellation on focus change

The multi-game input router now has a slot-side cancellation path: when a slot loses input focus, its Canvas cancels overlay/virtual-keyboard pointer state and emits pointer-release events for active MIDP pointers. Android `ACTION_CANCEL` uses the same path. XML and ZIP structural checks pass; Android build and real-device testing remain unverified. Four independent MIDlet launches are still not confirmed.


## Step 27 update — 2026-10-22

- Connected the slot Launch button to a new `MultiGameSessionLauncher`; each slot now attempts to construct its own `MicroLoader` and `MidletThread` and host the MIDlet in the corresponding `SlotHost`. The button changes to Stop while a session is running.
- Added session-only loader initialization (avoids the legacy display reset and shared cache clear), per-loader MIDlet descriptor properties, pre-construction Display host registration, separate dex optimization directories, and per-slot virtual-keyboard attachment/cleanup.
- XML parsing (104 files) and Java brace-balance checks (574 files) passed.
- Full Java/Android compilation could not be verified because the wrapper could not download Gradle 7.3.3 (`services.gradle.org` DNS failure). No device/emulator test was available.
- **Important:** this is the first wired in-slot launch path, not a verified four-game release. Process-wide M3G Graphics3D and some other global services remain potential cross-game conflicts.


## Step 28 update — multi-MIDlet selection

- JAR import now records all declared `MIDlet-N` entries and validates their class paths. When more than one MIDlet is declared, the UI requires choosing one before conversion.
- The selected class is persisted per slot and passed to the runtime launcher; the launcher checks it against the converted descriptor and reports a clear error if it is missing.
- Re-import/re-prepare disables conversion until a fresh MIDlet selection is made, preventing stale class metadata from being used.
- Build and device validation remain outstanding. Process-global M3G Graphics3D and other shared services remain unresolved risks; four-game concurrency is not yet verified.

## Step 29 update — M3G singleton isolation

- `Graphics3D` and `Interface` now use per-context-ClassLoader registries rather than a single process-wide Java singleton. Session cleanup removes only that session's M3G registry.
- This change isolates Java-side M3G instance and object-proxy bookkeeping; native-library global state may still be shared and requires device testing.
- Full Android build and four-game device validation remain outstanding.

## Step 30 update — Android host lifecycle

- When `MultiGameActivity` leaves the foreground, it requests per-MIDlet pause and synthesizes key-up events for keys held in any slot. On resume it requests per-session resume through the matching MIDlet thread.
- This avoids routing lifecycle events through the legacy singleton and reduces invisible background execution / stuck-key risk. Asynchronous start timing and device-specific lifecycle behavior still require real-device validation.
- Android build and four-game concurrency remain unverified.


## Step 31 update — startup cancellation

The multi-game launcher now tracks pending session threads by their slot host. Stop and Activity teardown can cancel a session before `onSessionCreated()` assigns the MIDlet owner, and the posted UI attach callback skips a session already destroyed. The registry is released on normal cleanup and initialization failure. This is source-level lifecycle hardening only; Android build and four-game device testing remain unverified.

## Step 32 — defer session start while the host Activity is backgrounded

- `MidletThread` no longer queues `INIT` from its constructor. The single-game path assigns its compatibility singleton before queuing INIT; the multi-game path registers the host/thread pair before queuing INIT.
- Multi-game sessions now remember host pause state even before the MIDlet constructor completes or the slot owner is attached.
- `START` is ignored until the slot host has been bound and while the Activity is backgrounded. The host callback queues another START after binding, so an early resume message cannot start a MIDlet before its Display host exists.
- Slot pause/resume now updates the launcher's pending-session registry as well as notifying an already-bound MIDlet.
- This is source-level lifecycle hardening only; Android build/device validation is still required.

## Step 33 — per-slot launch reservation and pending cancellation

- Added an atomic launch reservation per slot to reject duplicate/concurrent launches.
- The slot button can request cancellation while initialization is pending; the reservation carries the cancellation flag across thread creation and UI attachment.
- Reservations are removed on launch failure or once the session thread is registered, and slot registries remain keyed by the exact host.
- Structural checks and ZIP integrity are validated for the archive; Android compilation and device-level concurrent gameplay remain unverified.


## Step 34 — session cleanup and failure isolation
- Added idempotent session cleanup to prevent duplicate resource release across lifecycle/error races.
- Initialization and pause failures now use the multi-game slot failure path and safely stop the affected runtime thread.
- Added `MULTIGAME_STEP34_NOTES.md`.
- Validation is source-level only; Android build and device testing remain unverified.

## Step 35 — render-policy transition safety
- Reset non-GL and GL presentation throttle windows when a slot changes between grid and maximized mode.
- Added policy generations so delayed callbacks queued under an older policy are ignored after rapid mode changes.
- The limiter remains presentation-only; it does not intentionally throttle MIDlet logic or event delivery.
- Source-level validation only; Android build and device frame-rate tests remain unverified.


## Step 36 — stale UI callback isolation
- Queued alerts are now dropped if their session host registration has been removed or replaced before delivery.
- Initial displayable synchronization is now guarded against host replacement before the UI callback runs.
- This reduces the chance that an old session's queued UI work leaks into a reused slot; source-level validation only, with Android build/device testing still required.

## Step 37 — final build/test handoff
- Launch reservations now retain Activity paused state when lifecycle callbacks occur before a session thread is registered.
- Added `MULTIGAME_STEP37_TEST_PLAN.md` with build commands and a device smoke-test checklist.
- Added `tools/verify-multigame-source.sh` for source invariant checks.
- Android build and device behavior remain unverified in this environment; see the Step 37 test plan.
