# Multi-game Step 37 — lifecycle handoff and final test package

- Fixed a lifecycle race: Activity pause/resume can happen while a slot has a launch reservation but its MIDlet thread has not yet been registered.
- The launch reservation now stores paused state and forwards it when the thread becomes available. `MidletThread` already gates `START` on the bound-host and paused flags, so the state is preserved until the next resume.
- Added `MULTIGAME_STEP37_TEST_PLAN.md` with build commands and a device smoke-test checklist.
- Added `tools/verify-multigame-source.sh` to check key lifecycle/rendering invariants before packaging.

## Validation
- Source invariant script: see result when run from project root.
- XML and ZIP checks: expected to pass as packaging checks.
- Android Gradle compile: not verified in this environment because Gradle 7.3.3 cannot be downloaded from `services.gradle.org` (DNS unavailable).
- Device testing: not performed. The four-game and 4 FPS requirements must be measured on the target device.
