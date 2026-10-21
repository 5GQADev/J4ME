# Multi-game Step 36 — stale UI callback isolation

## Changes
- Guard queued alert callbacks with the current `Display.SESSION_HOSTS` registration. Alerts from a detached/replaced session are discarded instead of appearing over a different slot/session.
- Guard the initial display synchronization posted by `Display.registerSessionHost`; if the host was unregistered or replaced before the UI event executes, the stale synchronization is discarded.
- Preserve the existing per-loader callback guard for normal displayable changes.

## Validation
- Source inspection and structural checks only. Android Gradle compilation and device testing are not confirmed in this environment.
- Exercise rapid stop/relaunch in one slot and trigger an alert while switching slots on a device before treating this as runtime-verified.
