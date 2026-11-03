# Nintendo 3DS integration: Azahar Libretro

## Core decision

RetroForge uses the official Azahar Libretro target, not the standalone Azahar Android application.

- Upstream: https://github.com/azahar-emu/azahar
- Pinned revision: `01623d2fb707317410225c0eced82e3e0c84edc1`
- Core output: `azahar_libretro.so`
- Supported packaged ABI: `arm64-v8a`
- Android NDK: `29.0.14206865`
- Azahar CMake requirement: 3.25 or newer; the core build remains separate from the app's CMake configuration.

The pin and build approach follow the upstream Libretro Android CI configuration. The upstream Android app is not loaded as a Libretro shared object.

## Implemented in RetroForge

### Build and binary validation

- `scripts/build-azahar-android.sh` fetches the pinned revision and recursively initializes submodules.
- The Azahar core is built separately with Libretro enabled, Vulkan disabled, and GLES enabled.
- The build verifies ELF64 and AArch64 headers and checks required Libretro dynamic exports.
- The build writes a SHA-256 manifest, pinned source revision, and upstream GPL license into the CI artifact.
- The Android app and Azahar core use the pinned NDK version and the shared C++ runtime.
- The APK staging task verifies the unstripped core artifact hash and source revision, packages the core for ARM64, and includes the GPL license, revision, and source-artifact SHA-256 metadata under `assets/licenses/azahar/`. Android Gradle strips non-runtime ELF symbols during packaging, so the final APK's `.so` hash differs from the verified source-artifact hash; required Libretro dynamic exports remain present.

### Android runtime

- The Kotlin runtime loads `azahar_libretro.so` through the JNI Libretro frontend.
- JNI supplies system, save, and content directories to the core and reads ROMs from a private cached file.
- The emulator screen provides a Storage Access Framework folder import for user-owned Azahar data. It validates the selected `Azahar/` tree and copies `sysdata/`, `nand/`, and `sdmc/` into the core's app-private save directory.
- The frontend negotiates GLES3 and captures the hardware frame through an EGL pbuffer readback path.
- Video metadata, pitch, pixel format, AV timing, and stereo PCM16 audio are transported to Kotlin.
- The Compose emulator screen now starts the Azahar runtime for N3DS ROMs rather than using the demo renderer.
- A vertically stacked dual-display frame is converted into separate top and bottom Android bitmaps, with stacked, side-by-side, top-only, and touch-screen-only layout choices.
- Virtual D-pad and Circle Pad, A/B/X/Y, Start/Select, L/R, ZL/ZR, and bottom-screen pointer input are forwarded to Libretro. Physical Android gamepads map D-pad, face buttons, shoulders, triggers, stick clicks, Circle Pad, and the New 3DS C-stick; analog axes use a dead zone.
- PCM samples are sent to Android AudioTrack. Partial writes are retained in a bounded pending buffer.
- App lifecycle pause/resume pauses native frame execution and audio output.
- Save-state serialization and restore are exposed when the core exports all three Libretro serialization functions. Large 3DS snapshots are stored atomically in app-private files, not Firestore documents (which have a small per-document size limit); slot metadata is rebuilt from local files on launch.
- The scanned ROM's persisted URI is carried into the game model so the selected ROM can be copied into app-private cache before native loading. Staging is streamed and capped at 4 GiB; the JNI fallback that must load ROM bytes in memory is capped at 512 MiB and catches allocation failure.
- Native frame counts and play-time accounting use the core-reported refresh rate rather than assuming 60 Hz.

## Known limitations and validation still required

Implementation does not mean the system is fully supported yet.

1. **Physical-device gameplay is not verified.** Install the 3DS-enabled APK and test lawful homebrew on a real ARM64 Android device.
2. **The GLES3/EGL path uses CPU readback.** It is a compatibility-oriented first frontend, not a zero-copy Android display surface. Measure frame pacing, heat, and memory use on devices.
3. **System data and firmware are not bundled.** Azahar requires decrypted ROMs; some games also need Mii data. The in-app folder importer accepts user-owned Azahar data (including `sysdata/`, `nand/`, and `sdmc/`) and places it under `files/retroforge/3ds/saves/Azahar/`. Do not distribute proprietary firmware, keys, NAND dumps, or game content.
4. **Save-state support is conditional and local-only.** It is exposed only when the core provides `retro_serialize_size`, `retro_serialize`, and `retro_unserialize`. Large snapshots are stored under app-private files and are not cloud-synced; round-trip testing against the pinned core is pending.
5. **Physical gamepad mapping is implemented but not device-verified.** Android gamepad keys map D-pad, A/B/X/Y, Start/Select, L/R, ZL/ZR, and stick clicks; joystick axes map the Circle Pad and New 3DS C-stick with a dead zone. Hardware-specific axis ranges and controller layouts still need testing on real devices.
6. **The 3DS-enabled APK packaging gate has passed in CI.** Run [#337](https://github.com/Samir4987/RetroForge-Emulator/actions/runs/41643904622) verified the pinned source-core hash and revision, ran the unit tests, built the APK, and confirmed `azahar_libretro.so`, `libc++_shared.so`, and the GPL license/revision assets are packaged. Android's packaging step strips non-runtime ELF symbols, reducing the core from roughly 369 MiB to 37 MiB while retaining its Libretro exports.
7. **Licensing review remains open.** Azahar declares GPL-3.0. Before distributing the APK, review combined-work obligations and provide required corresponding source and notices. Do not describe the resulting distribution as proprietary or license-free.

Nintendo 3DS must remain labelled **integration in progress** until the packaging, device, compatibility, lifecycle, and legal gates below pass. Other systems retain their existing demo behavior and are not replaced by the 3DS runtime.

## Reproducible build

Build the core separately:

```bash
bash scripts/build-azahar-android.sh
```

The expected artifact directory is:

```text
.build/azahar-artifacts/arm64-v8a/
  azahar_libretro.so
  AZAHAR-LICENSE.txt
  AZAHAR-REVISION.txt
  AZAHAR-SHA256.txt
```

Package the core into the Android debug APK:

```bash
./gradlew :app:stageAzaharCore :app:assembleDebug
```

The `feature/n3ds-android-integration` branch's `build.yml` downloads the verified ARM64 core artifact and runs `:app:stageAzaharCore`, tests, and `assembleDebug`. If the artifact is unavailable, it rebuilds the pinned core from source. A generic APK without the staged Azahar binary reports that the N3DS core is unavailable; it must not silently fall back to a fake 3DS game.

The separate GitHub Actions workflow **Package Nintendo 3DS core into Android APK** remains available for manual packaging from a successful Azahar build run ID.

## Acceptance gates

- [x] Select and pin the official Azahar Libretro core.
- [x] Add a separate reproducible Android ARM64 core build.
- [x] Add ELF architecture, required-export, source-revision, and SHA-256 checks to the core build.
- [x] Add the N3DS system/core registration and extension tests.
- [x] Implement JNI video, audio, input, AV-info, system/save/content directory, and optional save-state interfaces.
- [x] Add Kotlin runtime, AudioTrack output, dual-display conversion, touch and virtual-controller forwarding.
- [x] Map Android physical gamepad keys, Circle Pad/C-stick axes, hat D-pad, and ZL/ZR trigger axes; add mapper unit tests.
- [x] Add on-screen ZL/ZR controls, refresh-rate-aware native frame accounting, bounded ROM staging, and safe user-data/save-state replacement.
- [x] Connect N3DS ROM selection, persistent Storage Access Framework permissions, and runtime lifecycle to the emulator screen.
- [x] Add an app-private importer for user-provided Azahar system/Mii data.
- [x] Add APK staging for the core, matching C++ runtime, license/revision metadata, and third-party license notices.
- [x] Produce a successful 3DS-enabled APK and verify the Azahar core, shared C++ runtime, and license/revision assets in CI run #338.
- [ ] Install on a physical ARM64 Android device and boot lawful homebrew.
- [ ] Verify both displays, touch, Circle Pad, buttons, audio, pause/resume, and repeated open/close.
- [ ] Verify save-state save/restore round trips with the pinned Azahar core.
- [ ] Verify performance, memory use, and compatibility across target devices.
- [ ] Complete upstream and third-party license review and corresponding-source distribution review.

Do not mark Nintendo 3DS playable or fully supported until the unchecked acceptance gates have passed.
