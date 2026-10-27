package com.example.emulator

/**
 * JNI facade for the Libretro API. The native frontend is only a loader/runtime
 * foundation: real playback still requires a compatible core shared library,
 * a working video/audio/input pipeline, and device testing.
 */
internal class LibretroNative {
    external fun nativeOpen(corePath: String, romPath: String): String?
    external fun nativeRunFrame(): Boolean
    external fun nativeVideoFrameInfo(): IntArray?
    external fun nativeCopyVideoFrame(destination: IntArray): Boolean
    external fun nativeSetJoypadState(mask: Int)
    external fun nativeClose()

    companion object {
        init {
            System.loadLibrary("retroforge_libretro")
        }
    }
}
