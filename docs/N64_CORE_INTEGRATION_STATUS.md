# Nintendo 64 integration status

## Implemented in this branch

-The application recognizes `.n64`, `.z64`, and `.v64` ROM extensions and selects the Mupen64Plus-Next Libretro core candidate.
- The JNI frontend forwards the standard Libretro joypad and analog interfaces. RetroForge's left stick maps to the N64 analog stick; the four C-buttons map to the right analog axes.
- The JNI frontend accepts Libretro GLES2 and GLES3 hardware-rendering requests, creates an EGL pbuffer context, supplies GL procedure lookup and framebuffer callbacks, and attempts RGBA frame readback into the Android bitmap pipeline.
- CI compiles the JNI frontend for all configured Android ABIs and runs the Android unit tests.

## Not yet complete / not yet verified

- The Mupen64Plus-Next shared library is **not built or packaged**. The current workflow's native-core build step is for mGBA (a shared dependency from the GBA branch), not N64.
- The EGL implementation is an initial off-screen integration. It has not been validated against GLideN64 on a physical Android device. In particular, pbuffer/default-framebuffer assumptions, GLES3 config selection, GL state/readback behavior, and context lifecycle need runtime validation.
- No N64 ROM has been verified to boot or play in RetroForge. Audio, save data, sustained frame pacing, controller mapping, and clean shutdown remain unverified for N64.

## Core provenance and build target

- Upstream: https://github.com/libretro/mupen64plus-libretro-nx
- Upstream revision currently inspected: `6752836de8b224febfd5708444755b77712ac939` (upstream master, 2026-09-11).
- Upstream CI defines Android GLES2 and GLES3 targets for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. Its GLES3 target sets `GLES3=1` and uses the core name `mupen64plus_next_gles3`.
- Upstream core is GPL-3.0; packaging must retain its license and source/provenance notices.

## Acceptance checklist

- [x] N64 extensions resolve to the Mupen64Plus-Next candidate.
- [x] Analog axes and C-button mapping are implemented and unit-tested.
- [x] JNI frontend has an initial GLES2/GLES3 EGL hardware-rendering path.
- [ ] Pin the exact upstream source revision in the repository build.
- [ ] Build and package the N64 core for Android ABIs (at minimum `arm64-v8a`; add others when validated).
- [ ] Confirm the core's requested hardware context matches the EGL context actually provided.
- [ ] Boot a legally obtained N64 ROM on an Android device and verify video, audio, input, save data, frame pacing, and shutdown.
- [ ] Keep N64 marked experimental until runtime acceptance checks pass.

A successful frontend compile or APK build is not evidence that N64 gameplay works.
