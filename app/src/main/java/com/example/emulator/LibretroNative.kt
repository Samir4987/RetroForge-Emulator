package com.example.emulator

/**
 * JNI facade for the Libretro API. The native frontend is only a loader/runtime
 * foundation: real playback still requires a compatible core shared library,
 * a working video/audio/input pipeline, and device testing.
 */
internal class LibretroNative {
    external fun nativeOpen(corePath: String, romPath: String): String?
    external fun nativeRunFrame(): Boolean
    /** Returns [width, height, ARGB pixels...] for the most recent frame, or null before the first frame. */
    external fun nativeGetVideoFrame(): IntArray?
    external fun nativeClose()

    companion object {
        init {
            System.loadLibrary("retroforge_libretro")
        }
    }
}
