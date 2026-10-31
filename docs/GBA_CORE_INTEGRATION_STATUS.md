# GBA core integration — implementation notes

## Scope and branch safety

This work is isolated to `fix/gba-robolectric-sdk-35`, based on
`feature/gba-core-integration`. Do not merge it into the integration branch
without explicit approval.

The existing demo/simulation path remains available as a fallback while the
native emulation path is developed and tested.

## Current implementation

- The GBA core candidate is Libretro mGBA, licensed under MPL-2.0:
  https://github.com/libretro/mgba
- `scripts/build-mgba-android.sh` builds the pinned mGBA revision
  `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` for
  `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, then stages the
  shared libraries and license for APK packaging.
- The Android CI workflow runs unit tests, builds/packages the native core,
  assembles the debug APK, verifies it, and uploads it as a workflow artifact.
- `libretro_jni.cpp` dynamically resolves the Libretro API, loads a ROM,
  advances frames, maps joypad input, converts 0RGB1555/XRGB8888/RGB565 video
  frames to bounded ARGB pixels, and buffers stereo PCM in a fixed-capacity
  ring buffer.
- `LibretroRuntime` loads the frontend and selected core, validates ROM
  extensions, advances frames, exposes the latest video frame as a Bitmap,
  forwards controller state, and manages the audio output worker.
- `EmulatorScreen` attempts the real GBA path only when a GBA ROM byte array
  and both native libraries are present. It forwards input, advances frames
  away from the Compose UI thread, presents captured frames, and falls back to
  the demo engine if native startup or frame execution fails.
- Simulation save-state controls are hidden during real-core playback; native
  mGBA save-state serialization is not implemented by this integration.

## CI validation achieved

Latest successful workflow run:
https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41185344674

Validated commit: `f8dc93fa5aaeb80a368dc29d841d55857e4e6fef`

All 16 build steps completed successfully, including Firebase-backed unit
tests, pinned mGBA native build/package, debug APK assembly, APK verification,
and artifact upload.

Artifact: `RetroForge-Android-APK` (GitHub Actions artifact ID
`13036823451`, approximately 30 MiB; available from the workflow run page).

This validates the CI build and packaging path. It does not prove that the APK
installs or that a game boots, renders, plays audio, or responds to input on a
physical Android device.

## Remaining validation before calling GBA playable

1. Install the generated APK on an Android device or a compatible Android
   emulator and confirm the app launches.
2. Use a legally distributable GBA homebrew test ROM; verify ROM loading,
   frame presentation, frame pacing, audio, and all controller buttons.
3. Verify pause/resume, leaving the emulator screen, repeated ROM loads, and
   cleanup after core errors.
4. Confirm the packaged mGBA library is present for the target device ABI.
5. Add reproducible automated smoke tests where feasible. Do not bundle
   commercial games or proprietary BIOS files.
6. Implement and test real-core save states separately; current save-state UI
   behavior is not evidence of mGBA serialization support.

## Validation checklist

- [x] Select mGBA Libretro as the GBA core candidate.
- [x] Pin upstream mGBA source revision.
- [x] Build mGBA for Android ABIs in CI.
- [x] Package native core libraries into the debug APK.
- [x] Build and verify the Android debug APK in CI.
- [x] Upload the APK as a workflow artifact.
- [x] Implement native video capture and Kotlin Bitmap access.
- [x] Wire captured video frames into the GBA screen loop.
- [x] Implement bounded stereo PCM buffering and AudioTrack output.
- [x] Implement Libretro joypad mapping and forward controller state.
- [ ] Verify installation and real GBA gameplay on an Android device.
- [ ] Verify audible/stable audio and responsive controls on-device.
- [ ] Verify pause/resume and lifecycle cleanup on-device.
- [ ] Implement and verify native mGBA save states.
- [ ] Record a repeatable gameplay smoke-test result.

## Current status

The pinned mGBA Android core and debug APK build successfully in GitHub
Actions, and the unit-test step passes. The project has a native frontend path
for loading GBA ROMs, advancing frames, presenting video, routing audio, and
forwarding controller input. However, no Android device gameplay test has
been completed, so this is a successful build milestone—not yet a verified
playable GBA emulator.
