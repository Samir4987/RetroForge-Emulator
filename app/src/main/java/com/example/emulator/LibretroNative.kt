package com.example.emulator

/**
 * JNI facade for the Libretro API. Native video, stereo audio and joypad/stylus
 * callbacks are bridged to Kotlin. Real gameplay still requires a verified core
 * binary, screen presentation wiring and device testing.
 */
internal class LibretroNative {
    external fun nativeOpen(corePath: String, romPath: String): String?
    external fun nativeRunFrame(): Boolean
    /** Copies [width, height, ARGB pixels...] into a reusable array; returns the required array size. */
    external fun nativeCopyVideoFrame(destination: IntArray): Int
    external fun nativeSetJoypadButton(buttonId: Int, pressed: Boolean)
    external fun nativeSetPointer(x: Int, y: Int, pressed: Boolean)
    external fun nativeGetAudioSampleRate(): Double
    external fun nativeReadAudioSamples(maxFrames: Int): ShortArray?
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(state: ByteArray): Boolean
    external fun nativeClose()

    companion object {
        init {
            System.loadLibrary("retroforge_libretro")
        }
    }
}
