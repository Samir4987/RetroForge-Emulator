# GBA core integration — implementation notes

## Scope and branch safety

This work is isolated to `feature/gba-core-integration`. Do not merge it into
`main` or the existing integration branch without explicit approval.

The existing demo/simulation path should remain available while the native
emulation path is developed and tested.

## Current state observed in the repository

- The GBA candidate now points to `mgba_libretro_android.so` from the
  [Libretro mGBA core](https://github.com/libretro/mgba). Libretro documents
  mGBA as an MPL-2.0-licensed GBA core that also supports GB/GBC and accepts
  `.gba` content: https://docs.libretro.com/library/mgba/.
- The candidate entry is metadata only; it does not package or build the core.
- `LibretroRuntime` checks for both the frontend and core shared libraries,
  loads a ROM, and can call the native frame runner.
- `libretro_jni.cpp` dynamically resolves the expected Libretro API symbols
  and calls `retro_run()`.
- The native video callback now validates frame dimensions and pitch, converts
  Libretro 0RGB1555, XRGB8888, and RGB565 frames to bounded ARGB pixels, and
  retains the last frame when a core submits a null duplicate frame.
- `LibretroNative` exposes frame dimensions/pixel copying, and
  `LibretroRuntime.readVideoFrame()` can return the latest frame as an Android
  bitmap. This capture path is not yet wired into `EmulatorScreen`'s renderer.
- Native audio callbacks now enqueue stereo PCM in a fixed-capacity ring buffer;
  Kotlin drains it through a background `AudioTrack` worker using the core's
  reported sample rate. The queue drops oldest samples under pressure to bound
  latency, and shutdown stops the worker before unloading the native core.
- The input callback reads a thread-safe player-one joypad mask, and Kotlin maps
  the app's A/B/X/Y, D-pad, Start/Select, and L/R state to standard Libretro IDs.
- `EmulatorScreen` now attempts real GBA playback only when a GBA ROM byte array
  and both required native libraries are present. It routes controller state,
  frames, and audio through the runtime, hides simulation-only save-state
  controls during real-core playback, and falls back to demo mode when setup or
  frame execution fails.
- A successful `retro_load_game()` only confirms that the core accepted the
  ROM. It does not confirm that the frontend can present frames or route input
  and audio.

## Required work before calling GBA integration playable

1. Build and pin the selected mGBA Libretro source revision for the supported
   Android ABIs. The documented Libretro Android build route uses the
   `libretro-super` scripts (`libretro-fetch.sh` and
   `libretro-build-android-mk.sh`); verify the resulting ABI libraries and
   record the exact source revision before packaging. Do not assume a core
   exists just because its filename appears in the catalog.
2. Verify the conditional GBA screen integration on a device, including frame
   pacing, audio stability, pause/resume, and cleanup when leaving the screen.
3. Route audio samples through an Android audio output path with bounded
   buffering and correct sample-rate handling.
4. Verify touch/controller release behavior with an actual GBA core and ensure
   no input remains stuck after pause, failure, or screen exit.
5. Verify core/ROM lifecycle cleanup, failure paths, and repeated game loads.
6. Add tests or a reproducible manual test procedure using legally distributable
   homebrew test ROMs. Do not bundle commercial games or proprietary BIOS files.
7. Run the Android build and inspect the complete CI result. A passing APK build
   alone is not evidence that a real GBA core runs correctly on a device.

## Validation checklist

- [x] GBA core candidate selected: mGBA Libretro, MPL-2.0 (source/version not pinned yet)
- [ ] mGBA source revision pinned and core built for supported Android ABIs
- [ ] Core built for each packaged Android ABI
- [ ] Homebrew ROM loads and advances frames on-device
- [x] Native video callback captures bounded ARGB frames and exposes them to Kotlin
- [x] UI frame loop displays captured core frames when a real GBA core is available
- [ ] Real core video is verified on-device
- [x] Native stereo PCM queue and Android AudioTrack output path implemented
- [ ] Audio output is audible and stable on-device
- [x] Native Libretro joypad mask and Kotlin button-ID mapping implemented
- [x] UI frame loop forwards controller state to an available GBA core
- [ ] UI controller state changes gameplay and releases correctly on-device
- [ ] Closing/reloading a ROM releases native resources safely
- [ ] Existing demo mode still works
- [ ] CI build and device smoke-test results recorded

## Current validation status

Native video capture, bounded stereo audio buffering/output, joypad mapping, conditional
GBA screen routing, and unit tests for the input mapping/core candidate have been
committed on this branch. No mGBA core
binary is packaged in the app yet, so the real-core path remains dormant unless
a compatible core is built and added to the APK. The broader integration branch's Android
APK workflow is being checked separately and does not validate these GBA-specific
native changes. No device-level GBA playback has been verified.
