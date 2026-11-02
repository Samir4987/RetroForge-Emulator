package com.example.emulator

/**
 * JNI facade for the Libretro API. Native video frames are captured and exposed
 * to Kotlin; audio and input callbacks remain placeholders until their Android
 * pipelines are connected and verified on a device.
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
