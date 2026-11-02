# NDS melonDS DS integration

## Pinned upstream core

- Upstream source: https://github.com/JesseTG/melonds-ds
- Release: `v1.4.0`, source revision `f394adb` (2026-09-27).
- Android Release archive: https://github.com/JesseTG/melonds-ds/releases/download/v1.4.0/melondsds_libretro-android-Release.zip
- Published archive SHA-256: `555568396dd5cf230030e4dd0ff0b0c6ca7118231fe562bc50680fefa7ad8594`.
- Expected library: `melondsds_libretro_android.so`.
- Android ABI: `arm64-v8a`.
- License: GPLv3+.

The upstream Android build targets Android API 24 / arm64-v8a and disables the OpenGL renderer. The compute-shader renderer is not supported on Android. The upstream release page publishes the archive checksum; `scripts/fetch-melonds-ds-core.sh` verifies the downloaded ZIP against that checksum before extracting the core, info metadata and license. It also verifies that the extracted ELF is AArch64.

Run from the repository root:

```bash
bash scripts/fetch-melonds-ds-core.sh
```

The Android APK workflow runs the same script before Gradle packaging. The shared library is placed in `app/src/main/jniLibs/arm64-v8a/`; generated binary artifacts are not committed to Git. The app bundles a GPLv3+ license and corresponding-source notice under `app/src/main/assets/licenses/`.

## Implemented Android frontend work

The JNI frontend and Kotlin runtime now provide:

1. Dynamic loading of the Libretro core and ROM.
2. Video callback capture with conversion from 0RGB1555, XRGB8888 and RGB565 to ARGB_8888.
3. A reusable JNI video transfer array to reduce per-frame Java allocation.
4. Standard player-one Libretro joypad input IDs.
5. DS stylus coordinates mapped to the signed Libretro pointer range through `RETRO_DEVICE_POINTER`.
6. A bounded stereo PCM ring buffer and non-blocking Android `AudioTrack` output.
7. Kotlin runtime methods for reading frames, updating joypad/stylus input, and advancing frames.

Unit tests cover NDS extension routing (`.nds`, `.ds`, `.dsi`, `.ids`), video frame validation, stylus coordinate mapping and standard joypad IDs.

## Remaining work and limitations

This is not yet a verified playable NDS implementation.

1. Connect `LibretroRuntime` to ROM selection and the main Compose emulator screen.
2. Render the real core's dual-screen frame into the top and bottom screen surfaces and route virtual controller/stylus UI events to the runtime.
3. Add Libretro serialization/deserialization and local save-state persistence.
4. Handle pause/resume, lifecycle teardown, audio focus and repeated load/unload.
5. Run CI to verify the release download, checksum, archive contents, ELF dependencies, Kotlin compilation, native build and APK packaging.
6. Test on a physical ARM64 Android device with a legally obtained homebrew ROM; verify video, touch/controller input, audio and save/load round trips.

The upstream archive checksum is pinned and checked by the script, but this environment has not downloaded the archive or executed the Android/NDK build. No real NDS gameplay result is claimed.

## Branch dependency and safety

This branch is based on `feature/real-emulator-core-integration` to reuse its Libretro host foundation. The NDS PR targets that parent feature branch, not `main`. Review both branches together; do not merge either branch without the project owner's explicit approval.
