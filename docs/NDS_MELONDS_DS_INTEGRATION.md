# NDS melonDS DS candidate integration

## Scope

This change selects the upstream melonDS DS Libretro core as the Nintendo DS candidate in `LibretroCoreSpec` and adds unit tests for the catalog mapping.

- Upstream source: https://github.com/JesseTG/melonds-ds
- Libretro documentation: https://docs.libretro.com/library/melonds_ds/
- Upstream build guide: https://github.com/JesseTG/melonds-ds/blob/main/CONTRIBUTING.md
- Candidate upstream release: **v1.4.0**, released 2026-09-27; release page identifies source revision `f394adb`.
- Android Release artifact: [melondsds_libretro-android-Release.zip](https://github.com/JesseTG/melonds-ds/releases)
- Published SHA-256 for that ZIP: `555568396dd5cf230030e4dd0ff0b0c6ca7118231fe562bc50680fefa7ad8594`.
- Expected Android shared-library filename: `melondsds_libretro_android.so`. Upstream's changelog documents this name as the Android artifact naming convention.
- Upstream license: GPLv3; review the exact pinned revision and all dependencies before redistribution.
- Upstream-supported Android architecture: arm64. Do not infer support for `armeabi-v7a` from the existing Android app's minSdk.

The ZIP checksum is published upstream metadata; this project has **not** downloaded or verified the archive, inspected its contents, or packaged the shared library. The short source revision above is not a substitute for recording a full immutable source commit in the build configuration.

## Important limitations

This is a candidate-selection change, not a playable NDS implementation.

1. The repository does not yet pin an upstream melonDS DS source commit or build it as part of the Android build.
2. No verified melonDS DS shared library is bundled by this change.
3. The existing JNI frontend on the parent integration branch has placeholder video, audio, and input callbacks. It cannot yet deliver gameplay frames/audio/input to Compose.
4. The upstream release archive and its exact contents still need to be verified and pinned before any packaging change.
5. No build, unit-test, APK, or device gameplay result is claimed here.

## Next implementation tasks

1. Pin a full immutable melonDS DS upstream source commit/release and record source and dependency licenses.
2. Download the published Android Release ZIP, verify its SHA-256, inspect the archive contents, and confirm the contained shared library's ABI and filename.
3. Build it from source for Android `arm64-v8a` using the upstream CMake instructions and Android toolchain flags, or document a reproducible, license-compliant artifact acquisition path.
4. Confirm the produced ELF architecture and required shared libraries using Android NDK tools.
5. Integrate the core artifact into the app's native packaging without committing an unverified or placeholder binary.
6. Implement bounded video frame delivery with validated pixel format, dimensions and pitch; audio buffering; joypad input; and touchscreen coordinates mapped to the bottom screen.
7. Add a legally obtained homebrew NDS smoke test and validate repeated load/unload and lifecycle cleanup.
8. Run unit tests and an Android APK build in CI, then test on a physical ARM64 Android device.

## Branch dependency

This branch is based on `feature/real-emulator-core-integration` to reuse its in-progress Libretro host foundation. Review the parent branch and this branch together. Neither branch should be merged without the project owner's explicit approval.
