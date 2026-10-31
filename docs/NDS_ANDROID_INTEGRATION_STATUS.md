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
  directory callbacks, and optional Libretro serialize/unserialize entry points.
- Routes imported NDS ROM bytes through the real core runtime in `EmulatorScreen`.
  The core's 256x384 vertically stacked frame is split into top and bottom
  256x192 bitmaps for the dual-screen Compose view.
- Maps D-pad, A/B/X/Y, Start/Select, L/R, and bottom-screen stylus input.
- Connects the NDS save-slot UI to native serialization when the core supports it.
- Pauses audio/emulation with the app lifecycle and closes the native core when
  leaving the screen.
- Adds unit tests for core selection, all supported NDS extensions, screen
  dimensions, and Libretro joypad bit positions.

## Validation status

- GitHub Actions run [#110](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/40875706361)
  passed on commit `3c20f68ceb177b54d569b1ea9b7727d6355f6601` (2026-10-29).
  The run successfully built and packaged the melonDS DS core, ran the NDS
  integration unit tests, assembled the debug APK, and uploaded the APK artifact.
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
