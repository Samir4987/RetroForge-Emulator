# Real emulator core integration — status

## Scope and branch safety

This branch, `feature/real-emulator-core-integration`, is for the shared
Libretro frontend and broader emulator integration across supported systems.
GBA-specific frame/input work belongs on `feature/gba-core-integration`.
Do not merge either branch into `main` without explicit approval. The existing
demo/simulation path must remain available as a fallback.

## Current implementation

- `LibretroCoreSpec` describes candidate libraries for GB, GBC, GBA, NDS,
  and N64. Candidate metadata does not package a core binary.
- `LibretroNative` loads the JNI frontend and calls the standard Libretro
  lifecycle functions. The frontend shared library is built by CMake, but no
  actual emulator core is currently bundled by this repository.
- `LibretroRuntime` checks the frontend/core files, validates ROM presence and
  extension, and exposes ROM loading and frame stepping.
- A failed ROM replacement now resets Kotlin runtime state to `READY` (when
  the frontend and core files remain available) instead of leaving the runtime
  incorrectly marked as `GAME_LOADED`.
- Native video/audio/input callbacks and real screen/audio/controller
  integration remain incomplete on this broader branch.

## Required before calling this a real emulator

1. Choose maintained, compatible cores per system and record exact source
   versions, licenses, and Android build instructions.
2. Build and package core shared libraries for the app's supported Android ABIs.
3. Route video frames into the existing renderer with bounded buffers and
   pixel-format/pitch/dimension validation.
4. Implement stable audio output and controller input, including release and
   lifecycle behavior.
5. Validate repeated ROM loads, failure cleanup, and demo-mode fallback.
6. Run Android CI and smoke-test legal homebrew ROMs on a real device. Do not
   bundle commercial ROMs or proprietary BIOS files.

## Validation status

Android APK CI passed for commit `d61a0ab1fa538a06f71a55aa828d3b09751fef81`:
Gradle assembled the debug APK, the APK existence check passed, and the artifact
upload completed. This validates the broader branch's build, not that any real
emulator core is packaged or playable. No device-level real-core playback has
been verified.
