# Nintendo DS Android integration status

## Scope

This pass is NDS-first. N3DS work is parked until the Nintendo DS path is verified.
All implementation changes are on `feature/real-emulator-core-integration`.
Do not merge to `main`; AI 1 handles merges.

## Implemented in the app integration

- Selects the melonDS DS Libretro core for `.nds`, `.ds`, `.dsi`, and `.srl` ROMs.
- Adds a pinned Android core build script for `arm64-v8a` and `x86_64`, wired
  into Android CI.
- Extends the JNI bridge with video conversion to ARGB_8888, interleaved stereo
  PCM callbacks, joypad/pointer input, core option defaults, system/assets/save
  directory callbacks, Libretro API-version validation, and optional serialize/
  unserialize entry points. Native ROM, frame and save-state allocations are bounded.
- Decouples native audio callbacks from speaker playback using a bounded PCM
  queue and a dedicated non-blocking AudioTrack worker. Fast-forward no longer
  waits for real-time speaker consumption; old audio chunks are dropped under
  sustained overload rather than stalling emulation.
- Moves large ROM staging and native core initialization to an IO dispatcher.
- Batches NDS core frames at the core's native refresh rate while limiting
  Compose redraws to the display cadence. Fractional speed multipliers such as
  1.5x are accumulated without drift for both NDS and simulation systems.
- Stores NDS save states atomically in app-private files (up to 256 MiB) instead
  of putting large Base64 payloads into Firestore documents. Save slots are
  explicitly marked LOCAL ONLY in the UI.
- Routes imported NDS ROM bytes through the real core runtime in `EmulatorScreen`.
  The core's 256x384 vertically stacked frame is split into top and bottom
  256x192 bitmaps for the dual-screen Compose view.
- Maps D-pad, A/B/X/Y, Start/Select, L/R, and bottom-screen stylus input.
- Connects the NDS save-slot UI to native serialization when the core supports it.
- Pauses audio/emulation with the app lifecycle and closes the native core when
  leaving the screen.
- Stages ROM files and initializes the native core on an IO dispatcher, not the UI thread.
- Adds local NDS save-state storage with atomic slot replacement and a 256 MiB limit;
  the save dialog identifies these slots as LOCAL ONLY and skips Firestore.
- Batches fast-forward frames at the native core rate while throttling Compose redraws;
  fractional multipliers (including 1.5x) are accumulated without truncation.
- Adds unit tests for core selection, NDS extensions, screen dimensions, joypad
  bit positions, local save-state storage and fractional fast-forward pacing.

## Validation status

- GitHub Actions run [#119](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41185519629)
  passed on commit `f577e2a5fb0a67e0236a8f3c034c61d7a3fce385` (2026-10-31).
  All 17 build job steps completed successfully, including the melonDS DS core
  build/package, NDS integration unit tests, debug APK assembly, APK existence
  verification, and artifact upload. Earlier baseline run [#110](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/40875706361)
  also passed on 2026-10-29.
- CI run [#128](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41186555037)
  passed on commit `f98c8c194775ca5ac9ee036b277c4f3a92aba6da`, including the
  native joypad-bitmask query fix. Run [#123](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41186170992)
  also passed on `0baa2affae73f2faac2de0f36c201b0f3e8ba881`.
- CI run [#129](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41201246426) was cancelled during Android SDK installation before the NDS tests or APK build ran.
- The new audio queue, local save-state store, ROM I/O and fractional fast-forward changes have not yet received a successful CI run. The workflow now includes `NdsSaveStateStoreTest` and `FrameStepAccumulatorTest`.
- This CI result verifies compilation, packaging, and the listed unit tests; it
  does not prove that a ROM boots or that gameplay callbacks work on a device.
- No physical Android device has been tested by this code change yet.
- Do not describe NDS as verified playable until a successful real-device run
  using a legally distributable homebrew ROM has been recorded.
- Firmware and core-specific options may still need device-level validation.
- If a core doesn't expose serialization, NDS save-state actions report failure
  instead of silently saving the placeholder simulation state.

## Follow-up checklist

1. Verify actual ROM boot, frame delivery, audio, and touch/control response on
   a physical Android device.
2. Test pause/resume, audio continuity, invalid ROMs, repeated load/close, and
   supported save-state round trips.
3. Resolve any device-specific native loading, firmware, or core-option failures.
4. Only after these checks pass, begin the N3DS-specific core and rendering work.
