# Multi-game Step 28 — multi-MIDlet JAR selection and launch validation

## Implemented

- JAR inspection now reads every contiguous `MIDlet-N` entry in `META-INF/MANIFEST.MF`, validates each declared class entry, and retains the title/class pair.
- If a JAR contains multiple MIDlets, the Prepare workflow asks which MIDlet should be used for that slot before enabling conversion. Single-MIDlet JARs continue without an extra dialog.
- The chosen MIDlet title and class are persisted per slot. The launch path passes the chosen class to `MultiGameSessionLauncher`, which selects that exact class from the converted runtime descriptor rather than silently always starting the first entry.
- If the selected class is missing from the converted descriptor, launch fails with an actionable message asking the user to re-convert the JAR.
- Re-preparing a JAR disables conversion until the MIDlet selection is valid, avoiding accidental use of stale selection metadata.

## Validation and limits

- Source-level checks: XML parsing, Java brace-balance scan, and ZIP integrity check.
- Android/Gradle compilation is not confirmed because Gradle 7.3.3 is not cached and network DNS to `services.gradle.org` is unavailable in this environment.
- No Android device/emulator run was available.
- This step improves MIDlet selection and launch correctness; it does not isolate process-global M3G/Graphics3D services and does not establish verified four-game concurrency.

- Validation performed for this archive: all 104 XML files parsed successfully; a lexical brace scan of all 574 Java files found no unbalanced braces; archive integrity checked with `unzip -t`.
