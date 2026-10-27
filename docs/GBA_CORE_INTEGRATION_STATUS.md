# GBA core integration — implementation notes

## Scope and branch safety

This work is isolated to `feature/gba-core-integration`. Do not merge it into
`main` or the existing integration branch without explicit approval.

The existing demo/simulation path should remain available while the native
emulation path is developed and tested.

## Current state observed in the repository

- `LibretroCoreSpec` lists `skyemu_libretro_android.so` as a candidate for GBA,
  but a candidate entry is metadata; it does not package or build that core.
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
- The audio callbacks still emit no sound and input callbacks still report no
  buttons. The frontend is therefore not a playable emulator yet.
- A successful `retro_load_game()` only confirms that the core accepted the
  ROM. It does not confirm that the frontend can present frames or route input
  and audio.

## Required work before calling GBA integration playable

1. Select a maintained, compatible GBA core and document its license and build
   process. Build from source for supported Android ABIs; do not assume a
   prebuilt library exists just because its filename appears in the catalog.
2. Route the new bounded video-frame capture path into the existing Android
   renderer, including frame-loop scheduling and lifecycle/threading checks.
3. Route audio samples through an Android audio output path with bounded
   buffering and correct sample-rate handling.
4. Map the app's controller/touch input to Libretro joypad state, including
   polling and release behavior.
5. Verify core/ROM lifecycle cleanup, failure paths, and repeated game loads.
6. Add tests or a reproducible manual test procedure using legally distributable
   homebrew test ROMs. Do not bundle commercial games or proprietary BIOS files.
7. Run the Android build and inspect the complete CI result. A passing APK build
   alone is not evidence that a real GBA core runs correctly on a device.

## Validation checklist

- [ ] Maintained GBA core source, version/commit, and license recorded
- [ ] Core built for each packaged Android ABI
- [ ] Homebrew ROM loads and advances frames on-device
- [x] Native video callback captures bounded ARGB frames and exposes them to Kotlin
- [ ] Real core video is visible in the app
- [ ] Audio output is audible and stable
- [ ] Touch/controller input changes gameplay and releases correctly
- [ ] Closing/reloading a ROM releases native resources safely
- [ ] Existing demo mode still works
- [ ] CI build and device smoke-test results recorded

## Current validation status

Native frame capture and Kotlin bitmap conversion have been committed on this
branch. The broader integration branch's Android APK workflow is currently
running after its runtime lifecycle fix; that result does not validate this
branch's native changes. No GBA core binary is packaged, audio/input callbacks
remain placeholders, the captured frame is not yet connected to the app screen,
and device-level GBA playback has not been verified.
