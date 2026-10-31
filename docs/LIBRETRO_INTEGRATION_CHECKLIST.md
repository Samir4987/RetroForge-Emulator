# Libretro integration acceptance checklist

This checklist is the definition of done for real-core support. Do not mark a system as supported until its items pass on a physical Android device or a suitable emulator.

## Native frontend
- [ ] Load the native core library for the current Android ABI.
- [ ] Resolve and call the required Libretro API entry points.
- [ ] Register environment, video, audio, input poll, and input state callbacks.
- [ ] Read the core's system AV information and update display geometry.
- [ ] Handle software video frames and pixel formats safely.
- [ ] Stream audio samples without blocking the UI thread.
- [ ] Map virtual controller input, physical gamepad input, and NDS touch input.
- [ ] Run frames on a dedicated worker and stop/join it on pause, exit, or ROM change.
- [ ] Handle core crashes and invalid ROMs without crashing the library screen.

## Content and persistence
- [ ] Load user-selected ROMs through Android's Storage Access Framework.
- [ ] Handle cores that require a full content path versus a memory buffer.
- [ ] Keep saves and save states in app-private storage.
- [ ] Implement save RAM serialization and state serialization separately.
- [ ] Document optional firmware requirements without distributing proprietary firmware.
- [ ] Preserve the current demo games as a separate demo mode.

## Per-system test matrix
For each system GB, GBC, GBA, NDS, and N64:
- [ ] Valid user-provided ROM loads and produces frames.
- [ ] Invalid and unsupported files show a readable error.
- [ ] D-pad and action buttons reach the emulated game.
- [ ] Audio starts, pauses, and resumes correctly.
- [ ] Save RAM persists across restart.
- [ ] Save state and load state work.
- [ ] Back navigation releases native resources.
- [ ] Repeated open/close cycles do not leak native threads or memory.
- [ ] Test at least one representative title on a physical Android device.
- [ ] Verify licensing notices and bundled native libraries for every ABI.

## Current status

The catalog and documentation have been added, but the native frontend, native core binaries, and runtime tests are still pending. This checklist intentionally remains unchecked until verified.
