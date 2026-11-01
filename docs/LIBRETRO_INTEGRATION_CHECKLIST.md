# Libretro integration acceptance checklist

This checklist defines the evidence required before a real-core system can be marked supported. Source code, a successful native build, or an APK containing a core is not by itself proof of gameplay compatibility.

## Native frontend

- [ ] Load the native core library for the current Android ABI.
- [ ] Resolve and call the required Libretro API entry points.
- [ ] Register environment, video, audio, input poll, and input state callbacks.
- [ ] Read the core's AV information and update display geometry.
- [ ] Handle software video frames and pixel formats safely.
- [ ] Stream audio without blocking the UI thread or losing partial writes.
- [ ] Map virtual controls, physical gamepads, and touch input.
- [ ] Run frames on a dedicated worker and stop/join it on pause, exit, or ROM change.
- [ ] Handle invalid ROMs and core-load failures without crashing the library screen.
- [ ] Verify lifecycle cleanup and repeated open/close cycles on device.

## Content and persistence

- [ ] Load user-selected ROMs through Android's Storage Access Framework.
- [ ] Handle cores that require a full content path versus a memory buffer.
- [ ] Keep saves and save states in app-private storage.
- [ ] Verify save RAM persistence separately from save-state serialization.
- [ ] Verify save-state round trips and reject incompatible/corrupt states safely.
- [ ] Document optional firmware requirements without distributing proprietary firmware or keys.
- [ ] Preserve the current demo games as a separate demo mode.

## Per-system test matrix

For each system GB, GBC, GBA, NDS, N3DS, and N64:

- [ ] A valid user-provided ROM loads and produces frames.
- [ ] Invalid and unsupported files show a readable error.
- [ ] D-pad and action buttons reach the emulated game.
- [ ] Audio starts, pauses, and resumes correctly.
- [ ] Save RAM persists across restart.
- [ ] Save state and load state work where supported by the core.
- [ ] Back navigation releases native resources.
- [ ] Repeated open/close cycles do not leak native threads or memory.
- [ ] Test at least one representative title/homebrew on a physical Android device.
- [ ] Verify license notices and bundled native libraries for every packaged ABI.

## Nintendo 3DS-specific checks

- [x] Pin the Azahar Libretro core and add a separate ARM64 build workflow.
- [x] Add ELF64/AArch64, required-symbol, revision, and checksum verification.
- [x] Implement JNI transport for video, stereo audio, AV metadata, joypad, analog, and pointer input.
- [x] Add vertically stacked dual-display conversion and Compose screen wiring.
- [x] Wire AudioTrack, runtime pause/resume, ROM URI caching, and optional Libretro save-state APIs.
- [x] Add APK staging for the core and GPL/revision metadata.
- [ ] Build and inspect the latest 3DS-enabled APK.
- [ ] Boot lawful homebrew on a physical ARM64 Android device.
- [ ] Verify display orientation, touch coordinates, analog input, audio, and save-state behavior.
- [ ] Verify stability, performance, and required user-supplied system data.
- [ ] Complete GPL-3.0 distribution review.

## Current status

The N3DS app-side integration is implemented in the feature branch, but its final APK packaging, device gameplay, compatibility, and licensing acceptance gates remain open. Keep the system labelled **integration in progress** until those checks pass.
