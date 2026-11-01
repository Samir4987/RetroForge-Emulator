# Nintendo 3DS core integration decision

## Decision

Use **Azahar's official Libretro core target** as the Nintendo 3DS candidate for RetroForge, rather than trying to load Azahar's standalone Android app as if it were a shared library.

Upstream evidence inspected on 2026-10-31:

- Repository: https://github.com/azahar-emu/azahar
- Pinned research revision: `01623d2fb707317410225c0eced82e3e0c84edc1`
- The upstream tree contains `src/citra_libretro/CMakeLists.txt`, which builds a shared target named `azahar_libretro` (output name `azahar_libretro`, no platform prefix).
- That target implements the Libretro API and links Azahar's emulator, video, audio, and input components.
- The upstream `.ci/libretro-ci.yml` includes an Android ARM64-v8a build job, sets Android NDK version `29.0.14206865`, and names the artifact `azahar_libretro.so`.
- Upstream's Android app is a separate frontend under `src/android`; it is not itself the core library to pass to RetroForge's `dlopen` bridge.

## Why this is the selected route

Azahar is based on the Citra lineage and has a first-party Libretro implementation. This gives RetroForge a documented Libretro ABI boundary, matching the existing native loader more closely than embedding Azahar's full Android application and its UI. The Android ARM64 CI job is evidence that upstream builds this core for Android, but does not prove that RetroForge's own build or runtime is already compatible.

## Current RetroForge integration status

The native frontend has moved beyond the original ROM-loader scaffold:

- It captures Libretro CPU video frames and supports Azahar's GLES3 hardware-frame callback by reading the EGL pbuffer into XRGB8888.
- It exposes frame dimensions, pitch, pixel format, and the core's negotiated AV geometry/timing to Kotlin.
- It buffers interleaved stereo PCM16 audio for the Android layer to drain.
- It accepts Libretro joypad buttons, both analog sticks, and pointer coordinates/press state.
- The Kotlin runtime exposes those video, audio, AV-info, and input operations.

Important remaining work:

- The GLES3/EGL pbuffer path is a first CPU-readback frontend, not a hardware-accelerated Android display surface. Its device compatibility and performance are unverified.
- Android AudioTrack playback is not connected yet; the runtime currently only drains PCM samples.
- The Compose UI does not yet split Azahar's default combined 400×480 frame into the top and bottom displays or forward on-screen controls/touch into the runtime.
- Save serialization, persistent game-save directories, user-provided system-data configuration, and lifecycle recovery still need frontend work.
- No successful APK packaging or physical-device gameplay test has been verified for this callback implementation.

The Android Azahar build is configured to disable Vulkan and use GLES so the frontend can negotiate the implemented GLES3 path. A successful core build still does not prove gameplay. Keep Nintendo 3DS labelled **integration in progress**, and preserve demo mode for other systems and when the 3DS core is unavailable.

## Reproducible build requirements to implement

Use the pinned upstream revision above and recursively fetch its submodules. Follow upstream's Android Libretro CI configuration rather than guessing flags:

- Android ABI: initially `arm64-v8a` only, matching the upstream Android Libretro job.
- Android NDK: `29.0.14206865`, matching the upstream CI definition.
- CMake: upstream top-level project requires CMake `3.25` or newer; RetroForge's current app CMake version is `3.22.1`, so the 3DS core build must use a separate newer CMake installation without silently changing the app's CMake setting.
- Configure with `-DENABLE_LIBRETRO=ON -DENABLE_TESTS=OFF`, as in upstream's Libretro CI template.
- Expected shared library: `azahar_libretro.so` (confirm actual output path and packaging in the first CI build before wiring the runtime spec).
- Preserve upstream source revision, submodule revisions, license notices, and corresponding source when distributing a binary.

Do not add the Azahar build to the main Android build until the standalone native build has passed. Build time and memory use may be substantial.

## Licensing and user data

Azahar's repository declares GPL-3.0. Before distributing an APK containing its core, review GPL-3.0 obligations for the combined distribution, provide required notices and corresponding source, and have the project's distribution approach reviewed appropriately. Do not imply that an APK containing the core is proprietary or license-free.

Do not bundle commercial game images, copyrighted console firmware, or keys. Any optional system data must be obtained and supplied lawfully by the user; document requirements without distributing those files.

## Implemented in RetroForge\n\n- Added `RetroSystem.N3DS` metadata with dual-screen and analog-stick flags. The top-screen 400×240 dimensions are metadata only; the bottom-screen layout still needs frontend work.\n- Registered the pinned Azahar Libretro candidate as `azahar_libretro.so` for `.3ds`, `.cci`, and `.cxi` files. This is a candidate registration, not proof the binary is packaged or playable.\n- Added unit tests for system lookup, extension lookup, and dual-screen/input metadata.\n- Added `scripts/build-azahar-android.sh` and a separate, manually dispatchable / branch-scoped GitHub Actions workflow. The build script pins source and recursively updates submodules, then checks required Libretro exports before uploading the library with license and revision metadata.\n\n## APK packaging workflow

The core is intentionally not committed to Git. The successful Azahar build run (`41200970352`) uploaded the `azahar-libretro-android-arm64` artifact, including the native library and license/revision metadata. To package it:

1. Open the **Package Nintendo 3DS core into Android APK** workflow on `feature/n3ds-android-integration`.
2. Run it manually and provide the successful Azahar build workflow run ID in `azahar_run_id`.
3. The workflow downloads the artifact, checks the pinned revision and license files, runs `:app:stageAzaharCore :app:assembleDebug`, verifies that `lib/arm64-v8a/azahar_libretro.so` is inside the APK, and uploads `RetroForge-Android-APK-with-Azahar-3DS`.

For local packaging, extract `azahar_libretro.so` to `.build/azahar-artifacts/arm64-v8a/azahar_libretro.so` and keep `AZAHAR-LICENSE.txt` plus `AZAHAR-REVISION.txt` beside it, then run:

```bash
./gradlew :app:stageAzaharCore :app:assembleDebug
```

The staging task copies the library into a generated `jniLibs/arm64-v8a` source directory. A normal APK build does not require the large Azahar binary; without explicitly staging it, the app remains in demo/fallback mode for 3DS.

## Acceptance gates

- [x] Build the pinned Azahar Libretro core for Android ARM64-v8a in CI (run `41200970352`, successful).
- [x] Verify the produced shared object exports the required Libretro entry points in the build script.
- [x] Add a dedicated workflow to download the verified core artifact, stage it, assemble the APK, and verify the ARM64 library entry (workflow added; packaging run still pending).
- [x] Add a dedicated Nintendo 3DS system/core spec and unit tests without mapping unknown systems to 3DS.
- [x] Add native video/audio/input callback transport, AV metadata, and an initial EGL/GLES3 hardware-frame readback path.
- [ ] Connect PCM output to Android AudioTrack and render/split Azahar's combined dual-screen frame in Compose.
- [ ] Forward virtual/physical controls and touchscreen input from Android UI to the native runtime.
- [ ] Test legal homebrew content on a physical Android device.
- [ ] Verify load failure, unload, pause/resume, repeated launch/close, and demo fallback.
- [ ] Complete GPL-3.0 notices and source-distribution review.

Until these gates pass, describe Nintendo 3DS as **planned/integration in progress**, not playable or fully supported.
