# NDS melonDS DS integration

## Pinned upstream core

- Upstream source: https://github.com/JesseTG/melonds-ds
- Release: `v1.4.0`, source revision `f394adb` (2026-09-27).
- Android Release archive: https://github.com/JesseTG/melonds-ds/releases/download/v1.4.0/melondsds_libretro-android-Release.zip
- Published archive SHA-256: `555568396dd5cf230030e4dd0ff0b0c6ca7118231fe562bc50680fefa7ad8594`.
- Expected library: `melondsds_libretro_android.so`.
- Android ABI: `arm64-v8a`.
- License: GPLv3+.

The upstream Android build targets Android API 24 / arm64-v8a and disables the OpenGL renderer. The compute-shader renderer is not supported on Android. The fetch script verifies the published ZIP checksum, extracts the core, info metadata and license, then checks that the ELF machine is AArch64. The APK workflow runs this script before Gradle packaging. Generated native binaries are not committed to Git.

## Implemented Android frontend

- Dynamic loading of the Libretro frontend and melonDS DS core.
- App-private Libretro system and battery-save directories are supplied through `RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY` and `RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY`.
- Video callback conversion for 0RGB1555, XRGB8888 and RGB565 to ARGB_8888.
- Reusable JNI frame transfer storage and bounded frame memory.
- Stacked (256x384) and side-by-side (512x192) frame splitting into separate 256x192 top and bottom screens.
- Main Compose emulator screen runs the native NDS core off the UI thread and copies its two screens into reused Android bitmaps.
- Existing virtual controller buttons are mapped to standard Libretro joypad IDs; bottom-screen tap/drag coordinates are sent as `RETRO_DEVICE_POINTER`.
- Stereo PCM is buffered in a bounded native ring and drained to non-blocking Android `AudioTrack`.
- Native `retro_serialize_size`, `retro_serialize` and `retro_unserialize` calls are exposed through Kotlin.
- Save slots 0–4 use atomic app-private files with a 256 MiB safety limit. NDS save states are not uploaded to Firestore.
- Runtime native calls are serialized so frame execution cannot overlap save/load or teardown.
- The NDS library recognizes `.nds`, `.ds`, `.dsi` and `.ids` file extensions.

## System files and DSi mode

The core reads system files from the app-private directory `files/retroforge/system`; battery saves are written under `files/retroforge/saves`. The melonDS DS documentation says standard DS BIOS/firmware files are optional for its built-in BIOS mode. DSi mode requires DSi BIOS, firmware and NAND files. A user-facing SAF import flow and DSi core-option configuration are not implemented yet, so DSi mode is not claimed as supported. Only use system files dumped from hardware you own.

## Remaining verification

This is still not a verified playable NDS release.

1. Obtain a successful CI run and inspect the actual release archive, ELF dependencies, Kotlin compilation, native build and APK packaging.
2. Verify that the upstream core emits the expected stacked or side-by-side dimensions on Android; confirm screen ordering and rotation.
3. Test homebrew boot, touch/controller input, audio, battery saves, save-state round trips, pause/resume and repeated load/unload on a physical ARM64 device.
4. Add a user-facing SAF firmware import flow and core-option configuration if DSi mode is required.

The release checksum is pinned in the fetch script, but this environment has not downloaded the archive or executed the Android/NDK build. No real NDS gameplay result is claimed.

## Branch dependency and safety

This branch is based on `feature/real-emulator-core-integration`. The NDS PR targets that feature branch, not `main`. Nothing is merged; do not merge either branch without the project owner's explicit approval.
