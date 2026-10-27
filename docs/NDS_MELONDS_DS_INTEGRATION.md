# NDS melonDS DS candidate integration

## Scope

This change selects the upstream melonDS DS Libretro core as the Nintendo DS candidate in `LibretroCoreSpec` and adds unit tests for the catalog mapping.

- Upstream source: https://github.com/JesseTG/melonds-ds
- Libretro documentation: https://docs.libretro.com/library/melonds_ds/
- Upstream build guide: https://github.com/JesseTG/melonds-ds/blob/main/CONTRIBUTING.md
- Upstream license: GPLv3; review the exact pinned revision and all dependencies before redistribution.
- Upstream-supported Android architecture: arm64. Do not infer support for `armeabi-v7a` from the existing Android app's minSdk.

## Important limitations

This is a candidate-selection change, not a playable NDS implementation.

1. The repository does not yet pin an upstream melonDS DS source commit or build it as part of the Android build.
2. No verified melonDS DS shared library is bundled by this change.
3. The existing JNI frontend on the parent integration branch has placeholder video, audio, and input callbacks. It cannot yet deliver gameplay frames/audio/input to Compose.
4. The candidate filename `melondsds_libretro_android.so` must be confirmed against the exact upstream Android build output before packaging. If upstream produces a different name, update the spec and packaging together.
5. No build, unit-test, APK, or device gameplay result is claimed here.

## Next implementation tasks

1. Pin a specific melonDS DS upstream commit/release and record source and dependency licenses.
2. Build it for Android `arm64-v8a` with the upstream CMake instructions and Android toolchain flags.
3. Confirm the produced ELF architecture and required shared libraries using Android NDK tools.
4. Integrate the core artifact into the app's native packaging without committing an unverified or placeholder binary.
5. Implement bounded video frame delivery with validated pixel format, dimensions and pitch; audio buffering; joypad input; and touchscreen coordinates mapped to the bottom screen.
6. Add a legally obtained homebrew NDS smoke test and validate repeated load/unload and lifecycle cleanup.
7. Run unit tests and an Android APK build in CI, then test on a physical ARM64 Android device.

## Branch dependency

This branch is based on `feature/real-emulator-core-integration` to reuse its in-progress Libretro host foundation. Review the parent branch and this branch together. Neither branch should be merged without the project owner's explicit approval.
