# Real emulator core integration

This branch is the integration work area for replacing the demo simulation with real Libretro cores while preserving the demo mini-games.

## Core plan

| RetroForge system | Intended core | ROM extensions | Status |
|---|---|---|---|
| GB | SkyEmu Libretro core | .gb | Not bundled / not runtime-tested |
| GBC | SkyEmu Libretro core | .gbc | Not bundled / not runtime-tested |
| GBA | SkyEmu Libretro core | .gba | Not bundled / not runtime-tested |
| NDS | SkyEmu Libretro core | .nds, .ds | Not bundled / not runtime-tested; upstream DS support is beta |
| N64 | Mupen64Plus-Next Libretro core | .n64, .z64, .v64 | Not bundled / not runtime-tested |

## Why this is more than a dependency

Libretro cores are native shared libraries and do not run merely by adding a Maven dependency. RetroForge needs a frontend that loads the core through its `retro_*` C API, passes a ROM buffer or content path, routes controller/touch input, receives video frames and audio samples, and connects save/load state. Android ABI builds and each core's license must be handled explicitly.

## Guardrails

- Never package commercial game ROMs.
- Do not bundle proprietary firmware/BIOS files; document any optional firmware requirements and let users provide their own legally obtained files.
- Keep the current demo mini-games available as demos rather than presenting them as ROM emulation.
- Do not mark a system as supported until a real ROM has been tested on-device for load, input, video, audio, save/load, and clean exit.
- Pin upstream core source revisions and preserve their license notices before producing distributable builds.

## Upstream references

- SkyEmu core documentation: https://docs.libretro.com/library/skyemu/
- SkyEmu upstream: https://github.com/libretro/SkyEmu
- Mupen64Plus-Next documentation: https://docs.libretro.com/library/mupen64plus_next/
- Libretro Android build guidance: https://docs.libretro.com/development/retroarch/compilation/android/

This file documents the intended integration and current limitations; it does not claim that native cores are already installed.
