package com.example.emulator

import java.nio.ByteBuffer

/**
 * JNI facade for the Libretro frontend. Video is copied into a reusable direct
 * buffer; audio is drained in interleaved stereo PCM16 samples.
 */
internal class LibretroNative {
    external fun nativeOpen(corePath: String, romPath: String): String?
    external fun nativeRunFrame(): Boolean
    external fun nativeGetAvInfo(): DoubleArray
    external fun nativeGetVideoInfo(): IntArray
    external fun nativeCopyVideoFrame(destination: ByteBuffer): Int
    external fun nativeCopyAudioSamples(destination: ShortArray): Int
    external fun nativeSetJoypadButton(port: Int, button: Int, pressed: Boolean)
    external fun nativeSetAnalogAxis(port: Int, stick: Int, axis: Int, value: Int)
    external fun nativeSetPointer(x: Int, y: Int, pressed: Boolean)
    external fun nativeClose()

    companion object {
        init {
            System.loadLibrary("retroforge_libretro")
        }
    }
}
