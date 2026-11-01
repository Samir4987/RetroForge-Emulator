package com.example.emulator

import java.io.File
import java.nio.ByteBuffer

data class LibretroVideoFrame(
    val width: Int,
    val height: Int,
    val pitch: Int,
    /** Libretro pixel format: 0 = 0RGB1555, 1 = XRGB8888, 2 = RGB565. */
    val pixelFormat: Int,
    /** Direct buffer containing pitch * height bytes in the core's native pixel format. */
    val pixels: ByteBuffer
)

/**
 * Boundary between the Android UI and the Libretro JNI frontend.
 *
 * This runtime transports actual core video/audio/input data. The caller still
 * owns pixel conversion/rendering and feeding drained PCM into Android AudioTrack.
 */
class LibretroRuntime(
    private val frontendLibrary: File,
    private val coreSpec: LibretroCoreSpec
) {
    enum class State { UNAVAILABLE, READY, GAME_LOADED, CLOSED }

    private var native: LibretroNative? = null
    private val coreFile: File
        get() = File(frontendLibrary.parentFile, coreSpec.libraryFileName)

    var state: State = State.UNAVAILABLE
        private set

    val isAvailable: Boolean
        get() = state == State.READY || state == State.GAME_LOADED

    init {
        if (frontendLibrary.isFile && coreFile.isFile) state = State.READY
    }

    fun loadRom(rom: File): Result<Unit> {
        if (state == State.CLOSED) {
            return Result.failure(IllegalStateException("Libretro runtime is closed."))
        }
        if (!isAvailable) {
            return Result.failure(
                IllegalStateException(
                    "Libretro frontend/core is not installed for ${coreSpec.system}. " +
                        "Expected core at ${coreFile.absolutePath}."
                )
            )
        }
        if (!rom.isFile || rom.length() == 0L) {
            return Result.failure(IllegalArgumentException("ROM file is missing or empty."))
        }
        val extension = rom.extension.lowercase()
        if (extension !in coreSpec.extensions) {
            return Result.failure(IllegalArgumentException("Unsupported ROM extension: .$extension"))
        }

        return runCatching {
            val bridge = native ?: LibretroNative().also { native = it }
            try {
                val error = bridge.nativeOpen(coreFile.absolutePath, rom.absolutePath)
                if (error != null) throw IllegalStateException(error)
                state = State.GAME_LOADED
            } catch (failure: Throwable) {
                runCatching { bridge.nativeClose() }
                native = null
                state = if (frontendLibrary.isFile && coreFile.isFile) State.READY else State.UNAVAILABLE
                throw failure
            }
        }
    }

    /** Advances one emulated frame. Call from the emulator worker, not the UI thread. */
    fun runFrame(): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before running frames." }
        check(native?.nativeRunFrame() == true) { "Native Libretro core is not running." }
    }

    /**
     * Returns the latest core frame, or null before the core has produced one.
     * The buffer is a snapshot and remains valid after the next emulated frame.
     */
    fun copyVideoFrame(): Result<LibretroVideoFrame?> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before reading video." }
        val bridge = checkNotNull(native)
        val info = bridge.nativeGetVideoInfo()
        require(info.size >= 4) { "Native frontend returned invalid video metadata." }
        val width = info[0]
        val height = info[1]
        val pitch = info[2]
        val pixelFormat = info[3]
        if (width <= 0 || height <= 0 || pitch <= 0) return@runCatching null
        val byteCount = Math.multiplyExact(pitch, height)
        val pixels = ByteBuffer.allocateDirect(byteCount)
        val copied = bridge.nativeCopyVideoFrame(pixels)
        if (copied <= 0) return@runCatching null
        require(copied <= byteCount) { "Native frontend copied more video data than the buffer can hold." }
        pixels.position(0)
        pixels.limit(copied)
        LibretroVideoFrame(width, height, pitch, pixelFormat, pixels)
    }

    /** Drains up to maxSamples interleaved stereo PCM16 samples from the core. */
    fun drainAudioSamples(maxSamples: Int = 4096): Result<ShortArray> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before reading audio." }
        require(maxSamples > 0) { "maxSamples must be positive." }
        val destination = ShortArray(maxSamples)
        val count = checkNotNull(native).nativeCopyAudioSamples(destination)
        destination.copyOf(count.coerceIn(0, destination.size))
    }

    /** Libretro joypad button IDs follow RETRO_DEVICE_ID_JOYPAD. */
    fun setJoypadButton(port: Int = 0, button: Int, pressed: Boolean) {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        require(port in 0..1 && button in 0..15) { "Invalid Libretro joypad port or button." }
        checkNotNull(native).nativeSetJoypadButton(port, button, pressed)
    }

    /** Analog axis values are signed 16-bit values; stick 0 is left, 1 is right. */
    fun setAnalogAxis(port: Int = 0, stick: Int, axis: Int, value: Int) {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        require(port in 0..1 && stick in 0..1 && axis in 0..1) { "Invalid Libretro analog axis." }
        checkNotNull(native).nativeSetAnalogAxis(port, stick, axis, value.coerceIn(-32768, 32767))
    }

    /** Pointer coordinates use Libretro's signed -32768..32767 coordinate range. */
    fun setPointer(x: Int, y: Int, pressed: Boolean) {
        check(state == State.GAME_LOADED) { "Load a ROM before sending touch input." }
        checkNotNull(native).nativeSetPointer(x.coerceIn(-32768, 32767), y.coerceIn(-32768, 32767), pressed)
    }

    fun close() {
        native?.nativeClose()
        native = null
        state = State.CLOSED
    }
}
