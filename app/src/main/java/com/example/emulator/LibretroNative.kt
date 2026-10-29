package com.example.emulator

/** Receives frames and interleaved signed 16-bit stereo PCM from the native frontend. */
internal interface LibretroCallbacks {
    fun onNativeVideoFrame(argbPixels: ByteArray, width: Int, height: Int)
    fun onNativeAudioSamples(interleavedStereoPcm: ShortArray)
}

/**
 * JNI facade for the Libretro API. The callback payload uses native-endian ARGB_8888
 * pixels and interleaved signed 16-bit stereo PCM.
 */
internal class LibretroNative(
    private val callbacks: LibretroCallbacks
) {
    external fun nativeSetDirectories(systemDirectory: String, saveDirectory: String, assetsDirectory: String)
    external fun nativeOpen(corePath: String, romPath: String): String?
    external fun nativeRunFrame(): Boolean
    external fun nativeSetInputState(
        buttonMask: Int,
        touchX: Float,
        touchY: Float,
        touching: Boolean
    )
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(state: ByteArray): Boolean
    external fun nativeClose()

    @Suppress("unused") // Invoked from libretro_jni.cpp.
    private fun onNativeVideoFrame(argbPixels: ByteArray, width: Int, height: Int) {
        callbacks.onNativeVideoFrame(argbPixels, width, height)
    }

    @Suppress("unused") // Invoked from libretro_jni.cpp.
    private fun onNativeAudioSamples(interleavedStereoPcm: ShortArray) {
        callbacks.onNativeAudioSamples(interleavedStereoPcm)
    }

    companion object {
        init {
            System.loadLibrary("retroforge_libretro")
        }
    }
}
