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

## Current RetroForge blockers

The existing RetroForge JNI bridge is only a lifecycle/ROM-loading scaffold:

- Its video callback discards every frame.
- Its audio callbacks discard samples.
- Its input callbacks always return neutral input.
- It does not expose the core's AV geometry or audio rate to the Android renderer/audio pipeline.
- The current generic runtime only reports that a ROM was accepted; it does not establish playable video, audio, touch, or controller input.

Therefore, even after packaging `azahar_libretro.so`, 3DS gameplay must **not** be marked supported until the callback pipeline and dual-screen/touch presentation are implemented and device-tested. Keep demo mode available when the native core or required runtime path is unavailable.

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

## Acceptance gates

- [ ] Build the pinned Azahar Libretro core for Android ARM64-v8a in CI.
- [ ] Verify the produced shared object exports the required Libretro entry points.
- [ ] Package it under the app's native-library directory and confirm ABI loading.
- [ ] Add a dedicated Nintendo 3DS system/core spec and tests without mapping unknown systems to 3DS.
- [ ] Implement real video, audio, input, dual-screen layout, and touch callbacks.
- [ ] Test legal homebrew content on a physical Android device.
- [ ] Verify load failure, unload, pause/resume, repeated launch/close, and demo fallback.
- [ ] Complete GPL-3.0 notices and source-distribution review.

Until these gates pass, describe Nintendo 3DS as **planned/integration in progress**, not playable or fully supported.
