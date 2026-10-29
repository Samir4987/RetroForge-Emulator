# Nintendo DS Android integration status

## Scope

This pass is NDS-first. N3DS work is parked until the Nintendo DS path is verified.
All changes are on `feature/real-emulator-core-integration`; do not merge to
`main` until CI and device checks pass.

## Implemented in the app integration

- Selects the melonDS DS Libretro core for `.nds`, `.dsi`, and `.srl` ROMs.
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
- Adds unit tests for core selection, NDS screen dimensions/extensions, and
  Libretro joypad bit positions.

## Validation status

- CI builds the pinned core and APK; the first run is still being validated.
- No physical Android device has been tested by this code change yet.
- Do not describe NDS as verified playable until a successful CI build and
  real-device run using a legally distributable homebrew ROM have been recorded.
- Firmware and core-specific options may still need device-level validation.
- If a core doesn't expose serialization, NDS save-state actions report failure
  instead of silently saving the placeholder simulation state.

## Follow-up checklist

1. Resolve any native-core build, Kotlin, or C++ compilation failures from CI.
2. Confirm actual frame delivery and touch/control response on a physical device.
3. Test pause/resume, audio continuity, invalid ROMs, repeated load/close, and
   supported save-state round trips.
4. Only after these checks pass, begin the N3DS-specific core and rendering work.
