# RetroForge Emulator — NDS Core Integration Audit

**Status:** Audit only; no native core has been added by this change.
**Branch:** `feature/nds-core-integration-audit`
**Audit date:** 2026-10-27
**Base:** `main` at the current branch tip when this branch was created.

## Scope and evidence

This document records a source-level review of files retrieved from the GitHub repository. It is not a local checkout, native build, APK build, or on-device test. Do not treat it as proof that any system is playable.

Reviewed:
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/example/emulator/EmulatorCore.kt`
- `app/src/main/java/com/example/emulator/RetroSystem.kt`
- `app/src/main/java/com/example/emulator/EmulatorInput.kt`
- `app/src/main/java/com/example/emulator/AudioSynthesizer.kt`
- `app/src/main/java/com/example/ui/components/RetroScreenView.kt`
- `.github/workflows/build.yml`
- Open pull request #1: https://github.com/Samir4987/RetroForge-Emulator/pull/1

## Findings

### Existing frontend is currently simulation-oriented

The retrieved `EmulatorCore.kt` implements app-side state, Android bitmap buffers, simulated entity state, N64-like 3D state, GBA racing state, and NDS touch-target state. Its comments and state fields do not demonstrate execution of real console instructions.

The current `RetroSystem.kt` maps NDS to a 256x192 display with a dual-screen flag, and N64 to 320x240 with an analog-stick flag. These are UI/configuration metadata, not proof of a native emulator backend.

The current `EmulatorInputState` includes d-pad/buttons, N64 C-buttons, analog stick values, and touch coordinates. This is a useful starting point for input translation, but there is no evidence in the reviewed files that those values are sent to a Libretro core.

The current `AudioSynthesizer.kt` synthesizes app-side sound effects. It is not evidence of native game audio output.

The current `RetroScreenView.kt` can display primary and secondary Android bitmaps and has touch handling. It could be adapted to display real NDS output, but it is not itself an emulator.

### Native integration status

- `app/build.gradle.kts` configures an Android Compose app with minSdk 24 and compileSdk 36.
- The reviewed app Gradle file does not declare an `externalNativeBuild` CMake configuration or `ndkVersion`.
- The expected path `app/src/main/cpp/CMakeLists.txt` was not found by the GitHub contents API.
- The reviewed Android manifest has no native-core-specific configuration.
- The repository's open PR #1 explicitly describes itself as an integration-start/draft change and says it does not yet bundle core binaries or implement a Libretro frontend bridge.

This audit did not enumerate every repository path, so absence of one expected CMake file does not prove no native code exists anywhere. Search and inspect the full tree before concluding that definitively.

## Recommended NDS core

Candidate: **melonDS DS** (Libretro core).

- Upstream: https://github.com/JesseTG/melonds-ds
- Libretro docs: https://docs.libretro.com/library/melonds_ds/
- Libretro API documentation: https://docs.libretro.com/development/cores/
- Verify the exact source revision, license, Android build flags, dependency licenses, and redistribution obligations before bundling.

Use a pinned upstream revision; do not download an arbitrary prebuilt library and assume ABI or provenance.

## Implementation boundary

Implement a small, explicit Libretro host behind JNI rather than adding emulation logic to the Compose screen. Keep the existing simulation available as a demo/fallback.

Suggested responsibilities:
1. Kotlin session controller: start, pause, resume, stop, state and errors.
2. JNI bridge: load/unload a selected core, pass content bytes/path, translate inputs, and expose frame/audio/state operations.
3. Native Libretro host: `dlopen`/symbol resolution or a statically linked core, callback registration, environment negotiation, game loading, frame stepping, and teardown.
4. Video: initially support the core's software-frame callback into a well-defined pixel format; document stride, pixel format, dimensions, and buffer ownership. Add hardware rendering only after software rendering is stable.
5. Audio: queue callback samples through a bounded Android audio output path; avoid one coroutine or AudioTrack allocation per callback.
6. Input: map buttons to Libretro joypad IDs and map touch coordinates only to the bottom screen's normalized 0..1 coordinates.
7. Persistence: keep battery-backed game saves separate from save states. Do not promise save-state support until serialization and restore are verified.
8. Lifecycle: ensure no callback touches a destroyed Compose view; stop the emulation thread and unload the core before releasing native buffers.

## NDS acceptance checklist

- [ ] Pin and document upstream commit and all licenses.
- [ ] Build the core for Android `arm64-v8a` with a documented NDK/toolchain.
- [ ] Verify exported Libretro entry points and ABI dependencies.
- [ ] Load a legally obtained homebrew `.nds` ROM and handle invalid content cleanly.
- [ ] Receive real frames from the core; verify 256x192 screen output and pixel format.
- [ ] Render top and bottom displays with selectable vertical/horizontal/single-screen layouts.
- [ ] Map touch gestures to the bottom display only; test bounds and aspect-ratio letterboxing.
- [ ] Map buttons and d-pad to Libretro input state.
- [ ] Hear actual core audio; test pause/mute/resume and bounded buffering.
- [ ] Verify battery-backed save persistence and save-state round trip separately where supported.
- [ ] Test repeated start/stop, activity background/foreground, and ROM replacement for leaks/crashes.
- [ ] Run Gradle unit tests and `assembleDebug`; retain actual logs and result.
- [ ] Test gameplay on a physical Android device before describing NDS as playable.

## Safe next steps

1. Inspect the full repository tree and latest commit/branch tips again before editing shared files.
2. Review the existing draft PR and other developers' feature branches; do not overwrite their work.
3. Build melonDS DS from its pinned upstream source in an isolated native target.
4. Add a minimal JNI/Libretro smoke test before connecting it to the UI.
5. Keep this work on `feature/nds-core-integration-audit`; use a separate implementation branch if the scope grows.
6. Open a draft PR for review; never merge without the owner's explicit approval.

## Limitations of this audit

GitHub source-file inspection was available, but no local checkout, Android SDK/NDK execution, APK build, or physical device was available through this workflow. No build or gameplay result is claimed.
