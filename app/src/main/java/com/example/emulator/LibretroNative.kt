package com.example.emulator

/**
 * JNI facade for the Libretro API. Native video, stereo audio and joypad/stylus
 * callbacks are bridged to Kotlin. Real gameplay still requires a verified core
 * binary, screen presentation wiring and device testing.
 */
internal class LibretroNative {
    external fun nativeOpen(corePath: String, romPath: String): String?
    external fun nativeRunFrame(): Boolean
    /** Returns [width, height, ARGB pixels...] for the most recent frame, or null before the first frame. */
    external fun nativeGetVideoFrame(): IntArray?
    external fun nativeSetJoypadButton(buttonId: Int, pressed: Boolean)
    external fun nativeSetPointer(x: Int, y: Int, pressed: Boolean)
    external fun nativeGetAudioSampleRate(): Double
    external fun nativeReadAudioSamples(maxFrames: Int): ShortArray?
    external fun nativeClose()

    companion object {
        init {
            System.loadLibrary("retroforge_libretro")
        }
    }
}
