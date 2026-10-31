# Nintendo 64 integration status

## Current state

- The application already identifies `.n64`, `.z64`, and `.v64` files as Nintendo 64 content.
- `LibretroCoreSpec` already selects the Mupen64Plus-Next Libretro core candidate.
- This branch adds native Libretro analog-axis forwarding for the N64 left stick and maps the four C-buttons to the right analog stick.
- Unit tests cover the N64 core selection and analog mapping.

## Important implementation blocker

The current JNI frontend only accepts Libretro software pixel formats. Mupen64Plus-Next uses GLideN64 by default and requests a Libretro hardware-rendering context. The current environment callback rejects that request, and the frontend has no EGL/GLES context lifecycle or hardware-frame readback yet.

Therefore, adding the Mupen64Plus-Next shared library alone would not make N64 gameplay work. The next implementation phase must add an Android EGL/GLES context (including context reset/destroy callbacks and framebuffer readback) or a verified software-rendering path supported by the core.

## Build and packaging plan

1. Pin an upstream Mupen64Plus-Next revision and preserve its GPL-3.0 license notice.
2. Build Android GLES3 and/or GLES2 variants with the Android NDK for supported ABIs.
3. Package the core under the filename in `LibretroCoreSpec`.
4. Extend the frontend to satisfy the core's hardware-rendering contract.
5. Add CI checks for ABI packaging and native symbol loading.
6. Validate a legally obtained N64 ROM through load, video, audio, analog input, save data, and clean shutdown before calling N64 playable.

## Validation status

- [x] N64 ROM extensions are recognized by the existing core catalog.
- [x] Native analog axes are forwarded by the JNI bridge.
- [x] Unit tests cover N64 core selection and analog mapping.
- [ ] Mupen64Plus-Next Android core is built and packaged.
- [ ] EGL/GLES hardware rendering is implemented.
- [ ] N64 ROM boot and gameplay are verified.

A passing Android APK build is not proof of N64 gameplay. Keep the system marked as experimental until the final runtime checks pass.
