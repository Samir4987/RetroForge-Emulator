# Nintendo DS Android integration status

## Scope and branch safety

This pass is NDS-first. N3DS work remains parked until Nintendo DS is verified.
All current integration changes are on `feature/real-emulator-core-integration`.
Nothing is merged into `main`; do not merge without the project owner's approval.

## Implemented

- Selects the melonDS DS Libretro core for `.nds`, `.ds`, `.dsi` and `.srl` ROMs.
- Adds a pinned Android core build script for `arm64-v8a` and `x86_64`, wired into CI.
- Extends the JNI bridge with video conversion to ARGB_8888, stereo PCM callbacks,
  joypad/pointer input, core-option defaults, system/assets/save directory callbacks,
  Libretro API-version validation, and optional serialize/unserialize entry points.
- Bounds native ROM (512 MiB), video frame (4 million pixels), and save-state
  (256 MiB) allocations and handles allocation failures.
- Stages large ROMs and initializes the native core on an IO dispatcher rather
  than blocking the Compose UI thread.
- Splits the core's default 256x384 vertically stacked frame into top and bottom
  256x192 bitmaps for the dual-screen Compose view.
- Batches NDS frames at the core refresh rate while throttling Compose redraws.
  Fractional speed multipliers such as 1.5x are accumulated without truncation.
- Skips native video conversion for intermediate fast-forward frames and presents
  only the final frame in each batch.
- Decouples native audio callbacks from speaker playback with a tested bounded PCM
  queue and a dedicated non-blocking AudioTrack worker. Old chunks are dropped
  under sustained overload rather than stalling emulation.
- Maps D-pad, A/B/X/Y, Start/Select, L/R, and bottom-screen stylus input.
- Stores NDS save states atomically in app-private files (up to 256 MiB), rather
  than putting large Base64 payloads into Firestore. The save dialog marks these
  slots LOCAL ONLY and skips cloud save loading/deletion for NDS.
- Pauses audio/emulation with the app lifecycle and closes the native core when
  leaving the emulator screen.
- Adds unit tests for core selection/extensions, local save-state storage,
  fractional fast-forward pacing, and bounded PCM queue overflow behavior.

## Validation status

- CI run [#119](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41185519629)
  passed on 2026-10-31 for an earlier integration commit, including core
  build/package, NDS unit tests and debug APK assembly.
- CI run [#128](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41186555037)
  passed on commit `f98c8c194775ca5ac9ee036b277c4f3a92aba6da`, including the
  native joypad-bitmask query fix.
- CI run [#129](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41201246426)
  was cancelled during Android SDK installation before tests or APK assembly.
- The newer bounded audio queue, local save-state store, ROM I/O, frame skipping,
  memory guards and fast-forward changes do not yet have a successful CI result.
  The workflow now includes `NdsSaveStateStoreTest`, `FrameStepAccumulatorTest`
  and `BoundedPcmQueueTest`.
- No physical Android device has verified real homebrew boot, audio, touch,
  battery saves or save-state round trips. Do not describe NDS as verified playable.

## Remaining work

1. Obtain a successful CI run for the latest integration branch head.
2. Verify real homebrew boot, frame layout/order, audio, controller/stylus input,
   battery saves, save/load, pause/resume and repeated load/close on ARM64 Android.
3. Validate firmware requirements and core options on device; DSi-specific files
   and configuration may be needed for DSi mode.
4. Only after these checks pass, resume N3DS-specific core and rendering work.
