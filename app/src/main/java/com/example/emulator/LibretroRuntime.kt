package com.example.emulator

import java.io.File
import kotlin.math.roundToInt

/** Standard RETRO_DEVICE_ID_JOYPAD values used by Libretro cores. */
enum class LibretroJoypadButton(val id: Int) {
    B(0), Y(1), SELECT(2), START(3),
    UP(4), DOWN(5), LEFT(6), RIGHT(7),
    A(8), X(9), L(10), R(11),
    L2(12), R2(13), L3(14), R3(15)
}

/** Maps normalized Android touch coordinates to Libretro's signed pointer coordinate range. */
data class LibretroPointerPosition(val x: Int, val y: Int) {
    companion object {
        fun fromNormalized(x: Float, y: Float): LibretroPointerPosition {
            require(x.isFinite() && y.isFinite()) { "Pointer coordinates must be finite." }
            fun convert(value: Float): Int =
                ((value.coerceIn(0f, 1f) * 65535f) - 32768f)
                    .roundToInt()
                    .coerceIn(-32768, 32767)
            return LibretroPointerPosition(convert(x), convert(y))
        }
    }
}

/** A copied, tightly packed ARGB_8888 frame emitted by the native Libretro video callback. */
data class LibretroVideoFrame(
    val width: Int,
    val height: Int,
    val argbPixels: IntArray
) {
    init {
        require(width > 0 && height > 0) { "Video frame dimensions must be positive." }
        require(width.toLong() * height.toLong() == argbPixels.size.toLong()) {
            "Video frame pixel count does not match its dimensions."
        }
    }
}

/**
 * Boundary between the Android UI and the Libretro JNI frontend.
 *
 * A successful ROM load means the native core accepted the ROM. Video frames,
 * stereo PCM and controller/stylus input are exposed through this runtime;
 * save-state support and presentation in the main emulator screen remain separate work.
 */
class LibretroRuntime(
    private val frontendLibrary: File,
    private val coreSpec: LibretroCoreSpec
) {
    enum class State { UNAVAILABLE, READY, GAME_LOADED, CLOSED }

    private var native: LibretroNative? = null
    private var audioOutput: LibretroAudioOutput? = null
    private val coreFile: File
        get() = File(frontendLibrary.parentFile, coreSpec.libraryFileName)

    var state: State = State.UNAVAILABLE
        private set

    val isAvailable: Boolean
        get() = state == State.READY || state == State.GAME_LOADED

    init {
        if (frontendLibrary.isFile && coreFile.isFile) {
            state = State.READY
        }
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
            audioOutput?.close()
            audioOutput = null
            try {
                val error = bridge.nativeOpen(coreFile.absolutePath, rom.absolutePath)
                if (error != null) throw IllegalStateException(error)
                state = State.GAME_LOADED
                val sampleRate = bridge.nativeGetAudioSampleRate().toInt().coerceIn(8_000, 192_000)
                audioOutput = runCatching { LibretroAudioOutput(sampleRate) }.getOrNull()
            } catch (failure: Throwable) {
                // nativeOpen closes any previously loaded core before attempting a new load.
                // Keep Kotlin state in sync if the replacement ROM fails to load.
                audioOutput?.close()
                audioOutput = null
                runCatching { bridge.nativeClose() }
                native = null
                state = if (frontendLibrary.isFile && coreFile.isFile) {
                    State.READY
                } else {
                    State.UNAVAILABLE
                }
                throw failure
            }
        }
    }

    /**
     * Returns the latest frame captured by the native video callback.
     * Call after [runFrame]; null means the core has not emitted a frame yet.
     */
    fun readVideoFrame(): Result<LibretroVideoFrame?> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before reading video frames." }
        val packed = native?.nativeGetVideoFrame() ?: return@runCatching null
        require(packed.size >= 2) { "Native video frame header is incomplete." }
        val width = packed[0]
        val height = packed[1]
        require(width in 1..4096 && height in 1..4096) {
            "Native video frame dimensions are invalid."
        }
        val expectedPixels = width.toLong() * height.toLong()
        require(expectedPixels <= 16L * 1024L * 1024L && expectedPixels == packed.size - 2L) {
            "Native video frame pixel data is incomplete or too large."
        }
        LibretroVideoFrame(width, height, packed.copyOfRange(2, packed.size))
    }

    fun setJoypadButton(button: LibretroJoypadButton, pressed: Boolean): Result<Unit> =
        setJoypadButton(button.id, pressed)

    /** Sets one standard Libretro joypad button (IDs 0 through 15) for player one. */
    fun setJoypadButton(buttonId: Int, pressed: Boolean): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        require(buttonId in 0..15) { "Unsupported Libretro joypad button ID: $buttonId" }
        checkNotNull(native).nativeSetJoypadButton(buttonId, pressed)
    }

    /** Sends the Nintendo DS stylus position and pressed state through RETRO_DEVICE_POINTER. */
    fun setStylusPosition(x: Float, y: Float, pressed: Boolean): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        val pointer = LibretroPointerPosition.fromNormalized(x, y)
        checkNotNull(native).nativeSetPointer(pointer.x, pointer.y, pressed)
    }

    /** Advances one core frame and captures video through the registered Libretro callback. */
    fun runFrame(): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before running frames." }
        val bridge = checkNotNull(native)
        check(bridge.nativeRunFrame()) { "Native Libretro core is not running." }
        bridge.nativeReadAudioSamples(4_096)?.let { audioOutput?.write(it) }
    }

    fun close() {
        audioOutput?.close()
        audioOutput = null
        native?.nativeClose()
        native = null
        state = State.CLOSED
    }
}
