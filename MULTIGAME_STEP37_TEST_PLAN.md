# Multi-game Step 37 — final build and device-test handoff

## Source change
- `LaunchReservation` now records `hostPaused`, not only cancellation.
- `setHostPaused()` updates the reservation while the launcher is still preparing a runtime and forwards the state to a pending thread when available.
- `registerSession()` carries a recorded paused state into a newly registered MIDlet thread. This closes a lifecycle gap where Android could background the Activity before `createSession()` registered its thread.

## Build on a machine with Android SDK and network/dependencies available

1. Open this project root in Android Studio and allow Gradle sync to finish.
2. Use JDK 11 for this legacy Gradle 7.3.3 / Android Gradle Plugin project if the installed Android Studio does not automatically select a compatible JDK.
3. Build the open-source debug flavor:
   - Windows: `gradlew.bat :app:assembleOpenDebug`
   - macOS/Linux: `./gradlew :app:assembleOpenDebug`
4. If you want a compile-only check first:
   - Windows: `gradlew.bat :app:compileOpenDebugJavaWithJavac`
   - macOS/Linux: `./gradlew :app:compileOpenDebugJavaWithJavac`
5. Install the resulting APK from `app/build/outputs/apk/open/debug/` (the configured output filename may be customized by the project).

## Device smoke test

- [ ] App opens and the multi-game screen shows four slots.
- [ ] Select, prepare and convert one test JAR, then launch it in slot 1.
- [ ] Repeat for slots 2–4 with known-compatible games.
- [ ] Verify each game accepts input only when its slot is focused/selected according to the UI behavior.
- [ ] Background the app during JAR preparation and during MIDlet initialization; verify a game does not start invisibly and resumes after returning.
- [ ] Stop a slot while its MIDlet is initializing; verify the slot can launch again.
- [ ] Stop one running slot; verify the other three continue.
- [ ] Maximize a slot and restore the 2×2 grid repeatedly.
- [ ] Check thumbnail cadence with a profiler/screen recording; do not assume the 4 FPS target is met without measurement.
- [ ] Check CPU, memory, audio focus and process stability for at least 10 minutes with four games.

## Validation status in the authoring environment

- Source-level lifecycle assertions and archive integrity can be checked locally.
- Android Gradle compilation is **not verified here**: Gradle Wrapper requires Gradle 7.3.3 from `services.gradle.org`, which is unreachable in the current environment, and no cached wrapper distribution is available.
- Four concurrent MIDlets, per-game input/audio isolation, and measured 4 FPS thumbnail rendering require device testing.
