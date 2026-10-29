# Nintendo 3DS Android integration status

## Branch and scope

Work is isolated to `feature/real-emulator-core-integration`. Nothing has
been merged to `main`.

## App-side changes in this pass

- Added `RetroSystem.N3DS` metadata with a 400x240 top screen, 320x240
  bottom touch screen, analog-stick capability, and `.3ds`, `.cci`, and
  `.cxi` extension recognition.
- Updated the existing dual-screen demo path to allocate the bottom bitmap at
  the system-specific dimensions and scale its reference drawing space for
  NDS/N3DS.
- Updated the dual-screen Compose view to use a 4:3 bottom-screen aspect ratio
  for N3DS instead of reusing the 5:3 top-screen ratio.
- Added unit tests for N3DS display metadata and extension resolution.

## Not yet complete / do not claim playable

- The real Libretro core catalog does not yet define an N3DS core. No N3DS
  native core library is packaged by this change, and a system enum entry does
  not make commercial 3DS ROMs playable.
- The current integration-branch JNI facade still only loads a Libretro core
  and advances frames. It does not yet expose video frames, audio output,
  controller state, or pointer/touch input to Kotlin.
- Save-state support must be negotiated against the selected core; it cannot
  be assumed from the generic loader.
- Android CI and real-device gameplay/performance have not been verified by
  this change. Do not report real-device testing as complete until a compatible
  core and test device are available.

## Next steps

1. Confirm the chosen 3DS core and its Android ABI/library naming and license.
2. Complete the JNI video/audio/joypad/pointer bridge against that core's
   supported Libretro interface (or implement a separate adapter if it is not
   Libretro-compatible).
3. Add a configurable top/bottom frame composition strategy matching the
   chosen core's actual video output format.
4. Add lifecycle, pause/resume, repeated-load, invalid-ROM, and input-release
   tests; then run Android CI and test on physical Android devices using
   legally distributable homebrew content.
