# Nintendo 64 integration status

## Implemented in this branch

- The app recognizes `.n64`, `.z64`, and `.v64` ROM extensions and selects the Mupen64Plus-Next Libretro core.
- The JNI frontend forwards standard Libretro joypad and analog input. RetroForge's left stick maps to the N64 analog stick; the four C-buttons map to the right analog axes.
- The JNI frontend accepts Libretro GLES2 and GLES3 hardware-rendering requests, creates an EGL pbuffer context, supplies GL procedure lookup and framebuffer callbacks, and attempts RGBA frame readback into the Android bitmap pipeline.
- The build pins upstream `libretro/mupen64plus-libretro-nx` at `6752836de8b224febfd5708444755b77712ac939`, builds its Android GLES3 target, and packages the resulting library as `mupen64plus_next_libretro_android.so`.
- The pinned upstream GPL-3.0 license is copied into the app's license assets.
- CI successfully built the Mupen64Plus-Next GLES3 core for `arm64-v8a`, compiled the Android app, and uploaded a debug APK. The workflow now builds the core separately for all four configured ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`.

## Not yet complete / not yet verified

- The EGL implementation is an initial off-screen integration. It has not been validated against GLideN64 on a physical Android device. In particular, pbuffer/default-framebuffer assumptions, GLES3 config selection, GL state/readback behavior, and context lifecycle need runtime validation.
- No N64 ROM has been verified to boot or play in RetroForge. Audio, save data, sustained frame pacing, controller mapping, and clean shutdown remain unverified for N64.
- The first successful core build was for `arm64-v8a`. The expanded four-ABI build matrix must pass before claiming N64 core coverage across all configured Android ABIs.

## Core provenance and build target

- Upstream: https://github.com/libretro/mupen64plus-libretro-nx
- Pinned upstream revision: `6752836de8b224febfd5708444755b77712ac939`.
- Upstream Android build target: GLES3 (`GLES3=1`), producing `libretro.so`, which the packaging script renames to the filename consumed by `LibretroCoreSpec`.
- Upstream core is GPL-3.0; the upstream license is packaged under `app/src/main/assets/licenses/`. The source URL and exact revision are recorded in the build script.

## Acceptance checklist

- [x] N64 extensions resolve to Mupen64Plus-Next.
- [x] Analog axes and C-button mapping are implemented and unit-tested.
- [x] JNI frontend has an initial GLES2/GLES3 EGL hardware-rendering path.
- [x] Pin the exact upstream source revision in the repository build.
- [x] Build and package the pinned N64 GLES3 core for `arm64-v8a`.
- [x] Compile the Android app and produce a CI APK containing the arm64 N64 core.
- [ ] Pass the expanded N64 core build for all four configured Android ABIs.
- [ ] Confirm the core's requested hardware context matches the EGL context actually provided on Android.
- [ ] Boot a legally obtained N64 ROM on an Android device and verify video, audio, input, save data, frame pacing, and shutdown.
- [ ] Keep N64 marked experimental until runtime acceptance checks pass.

A successful frontend compile, core compile, or APK build is not evidence that N64 gameplay works.
