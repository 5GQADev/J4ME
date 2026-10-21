# Multi-game Step 13 — runtime smoke-test bridge

## Implemented

- Added a per-slot `Test launch in standard player` action after a runtime package has been converted.
- Before launching, the action verifies the expected `converted.dex`, `res.jar`, and descriptor files are present.
- Creates a default `configs/<runtime-directory>/config.json` using the existing `ProfileModel` defaults if no configuration exists, then opens the existing `MicroActivity` through `Config.startApp`.
- The action is intentionally labeled as a standard-player smoke test. It does not claim to launch inside the 2x2 slot.
- Restoring the multi-game screen enables the test-launch action only if the stored runtime package still exists. Selecting a new source disables the old runtime launch action.

## Important limitation

This is a bridge to test the conversion output through the existing single-game player. It is not concurrent multi-game execution. `MicroActivity`, `ContextHolder`, global configuration, input routing and `MidletThread` still have shared process-level state, so starting four runtimes in one process remains unsafe until those are refactored.

## Verification

- Source inspection and ZIP integrity checks performed.
- XML parse checks performed.
- Full Android Gradle build not verified in this environment because the Gradle wrapper distribution is not available offline and `services.gradle.org` cannot be reached.
