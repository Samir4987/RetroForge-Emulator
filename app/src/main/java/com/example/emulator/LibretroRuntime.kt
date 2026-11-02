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

/** A Nintendo DS frame pair split from a stacked or side-by-side Libretro frame. */
data class LibretroDualScreenFrame(
    val top: LibretroVideoFrame,
    val bottom: LibretroVideoFrame
)

fun LibretroVideoFrame.toNintendoDsScreens(): LibretroDualScreenFrame {
    return when {
        width == 256 && height == 384 -> {
            val screenPixels = 256 * 192
            LibretroDualScreenFrame(
                top = LibretroVideoFrame(256, 192, argbPixels.copyOfRange(0, screenPixels)),
                bottom = LibretroVideoFrame(256, 192, argbPixels.copyOfRange(screenPixels, screenPixels * 2))
            )
        }
        width == 512 && height == 192 -> {
            val screenPixels = 256 * 192
            val top = IntArray(screenPixels)
            val bottom = IntArray(screenPixels)
            for (y in 0 until 192) {
                argbPixels.copyInto(top, y * 256, y * 512, y * 512 + 256)
                argbPixels.copyInto(bottom, y * 256, y * 512 + 256, y * 512 + 512)
            }
            LibretroDualScreenFrame(
                top = LibretroVideoFrame(256, 192, top),
                bottom = LibretroVideoFrame(256, 192, bottom)
            )
        }
        else -> throw IllegalArgumentException(
            "Expected a 256x384 stacked or 512x192 side-by-side Nintendo DS frame, got ${width}x${height}."
        )
    }
}

/**
 * Boundary between the Android UI and the Libretro JNI frontend.
 *
 * A successful ROM load means the native core accepted the ROM. Video frames,
 * stereo PCM, controller/stylus input and serialized save states are exposed
 * through this runtime. The Android screen owns frame presentation and lifecycle.
 */
class LibretroRuntime(
    private val frontendLibrary: File,
    private val coreSpec: LibretroCoreSpec,
    private val systemDirectory: File,
    private val saveDirectory: File
) {
    enum class State { UNAVAILABLE, READY, GAME_LOADED, CLOSED }

    private val nativeLock = Any()
    private var native: LibretroNative? = null
    private var audioOutput: LibretroAudioOutput? = null
    private var videoTransferBuffer = IntArray(2)
    private val audioTransferBuffer = ShortArray(8_192)
    private val ndsTopPixels = IntArray(256 * 192)
    private val ndsBottomPixels = IntArray(256 * 192)
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
        if (rom.length() > 512L * 1024L * 1024L) {
            return Result.failure(IllegalArgumentException("ROM exceeds the 512 MiB safety limit."))
        }
        val extension = rom.extension.lowercase()
        if (extension !in coreSpec.extensions) {
            return Result.failure(IllegalArgumentException("Unsupported ROM extension: .$extension"))
        }

        return runCatching {
            synchronized(nativeLock) {
            val bridge = native ?: LibretroNative().also { native = it }
            audioOutput?.close()
            audioOutput = null
            try {
                check(systemDirectory.exists() || systemDirectory.mkdirs()) {
                    "Could not create Libretro system directory: ${systemDirectory.absolutePath}"
                }
                check(saveDirectory.exists() || saveDirectory.mkdirs()) {
                    "Could not create Libretro save directory: ${saveDirectory.absolutePath}"
                }
                val error = bridge.nativeOpen(
                    coreFile.absolutePath,
                    rom.absolutePath,
                    systemDirectory.absolutePath,
                    saveDirectory.absolutePath
                )
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
    }

    /**
     * Returns the latest frame captured by the native video callback.
     * Call after [runFrame]; null means the core has not emitted a frame yet.
     */
    fun readVideoFrame(): Result<LibretroVideoFrame?> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before reading video frames." }
        val bridge = checkNotNull(native)
        var requiredSize = bridge.nativeCopyVideoFrame(videoTransferBuffer)
        if (requiredSize == 0) return@runCatching null
        require(requiredSize in 3..(4 * 1024 * 1024 + 2)) {
            "Native video frame size is invalid or too large."
        }
        if (videoTransferBuffer.size < requiredSize) {
            videoTransferBuffer = IntArray(requiredSize)
            requiredSize = bridge.nativeCopyVideoFrame(videoTransferBuffer)
        }
        require(requiredSize >= 3 && requiredSize <= videoTransferBuffer.size) {
            "Native video frame transfer was incomplete."
        }
        val width = videoTransferBuffer[0]
        val height = videoTransferBuffer[1]
        require(width in 1..4096 && height in 1..4096) {
            "Native video frame dimensions are invalid."
        }
        val expectedPixels = width.toLong() * height.toLong()
        require(expectedPixels <= 4L * 1024L * 1024L && expectedPixels == requiredSize - 2L) {
            "Native video frame pixel data is incomplete or too large."
        }
        LibretroVideoFrame(width, height, videoTransferBuffer.copyOfRange(2, requiredSize))
    } }

    /**
     * Copies the most recent Nintendo DS frame into reusable top/bottom pixel arrays.
     * Returned arrays are reused on the next call; consume them before advancing another frame.
     */
    fun readNintendoDsScreens(): Result<LibretroDualScreenFrame?> = runCatching {
        synchronized(nativeLock) {
            check(state == State.GAME_LOADED) { "Load a ROM before reading video frames." }
            val bridge = checkNotNull(native)
            var requiredSize = bridge.nativeCopyVideoFrame(videoTransferBuffer)
            if (requiredSize == 0) return@runCatching null
            require(requiredSize in 3..(4 * 1024 * 1024 + 2)) {
                "Native video frame size is invalid or too large."
            }
            if (videoTransferBuffer.size < requiredSize) {
                videoTransferBuffer = IntArray(requiredSize)
                requiredSize = bridge.nativeCopyVideoFrame(videoTransferBuffer)
            }
            require(requiredSize >= 3 && requiredSize <= videoTransferBuffer.size) {
                "Native video frame transfer was incomplete."
            }
            val width = videoTransferBuffer[0]
            val height = videoTransferBuffer[1]
            when {
                width == 256 && height == 384 && requiredSize == 256 * 384 + 2 -> {
                    System.arraycopy(videoTransferBuffer, 2, ndsTopPixels, 0, ndsTopPixels.size)
                    System.arraycopy(
                        videoTransferBuffer, 2 + ndsTopPixels.size,
                        ndsBottomPixels, 0, ndsBottomPixels.size
                    )
                }
                width == 512 && height == 192 && requiredSize == 512 * 192 + 2 -> {
                    for (y in 0 until 192) {
                        val sourceOffset = 2 + y * 512
                        val targetOffset = y * 256
                        System.arraycopy(videoTransferBuffer, sourceOffset, ndsTopPixels, targetOffset, 256)
                        System.arraycopy(videoTransferBuffer, sourceOffset + 256, ndsBottomPixels, targetOffset, 256)
                    }
                }
                else -> throw IllegalArgumentException(
                    "Expected a 256x384 stacked or 512x192 side-by-side Nintendo DS frame, got ${width}x${height}."
                )
            }
            LibretroDualScreenFrame(
                top = LibretroVideoFrame(256, 192, ndsTopPixels),
                bottom = LibretroVideoFrame(256, 192, ndsBottomPixels)
            )
        }
    }

    fun setJoypadButton(button: LibretroJoypadButton, pressed: Boolean): Result<Unit> =
        setJoypadButton(button.id, pressed)

    /** Sets one standard Libretro joypad button (IDs 0 through 15) for player one. */
    fun setJoypadButton(buttonId: Int, pressed: Boolean): Result<Unit> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        require(buttonId in 0..15) { "Unsupported Libretro joypad button ID: $buttonId" }
        checkNotNull(native).nativeSetJoypadButton(buttonId, pressed)
    } }

    /** Sends the Nintendo DS stylus position and pressed state through RETRO_DEVICE_POINTER. */
    fun setStylusPosition(x: Float, y: Float, pressed: Boolean): Result<Unit> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        val pointer = LibretroPointerPosition.fromNormalized(x, y)
        checkNotNull(native).nativeSetPointer(pointer.x, pointer.y, pressed)
    } }

    /** Applies the Android virtual controller and bottom-screen stylus state to the core. */
    fun updateInput(input: EmulatorInputState): Result<Unit> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before sending input." }
        val bridge = checkNotNull(native)
        val buttons = arrayOf(
            LibretroJoypadButton.B to input.buttonB,
            LibretroJoypadButton.Y to input.buttonY,
            LibretroJoypadButton.SELECT to input.select,
            LibretroJoypadButton.START to input.start,
            LibretroJoypadButton.UP to input.dpadUp,
            LibretroJoypadButton.DOWN to input.dpadDown,
            LibretroJoypadButton.LEFT to input.dpadLeft,
            LibretroJoypadButton.RIGHT to input.dpadRight,
            LibretroJoypadButton.A to input.buttonA,
            LibretroJoypadButton.X to input.buttonX,
            LibretroJoypadButton.L to input.triggerL,
            LibretroJoypadButton.R to input.triggerR
        )
        buttons.forEach { (button, pressed) ->
            bridge.nativeSetJoypadButton(button.id, pressed)
        }
        val pointer = LibretroPointerPosition.fromNormalized(input.touchX, input.touchY)
        bridge.nativeSetPointer(pointer.x, pointer.y, input.isTouching)
    } }

    /** Serializes the running core; null is reported as an unsupported-core failure. */
    fun saveState(): Result<ByteArray> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before saving state." }
        checkNotNull(native).nativeSerialize()
            ?: throw UnsupportedOperationException("The loaded Libretro core does not support save states.")
    } }

    /** Restores a previously serialized core state. */
    fun loadState(stateBytes: ByteArray): Result<Unit> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before restoring state." }
        require(stateBytes.isNotEmpty()) { "Save-state data is empty." }
        require(stateBytes.size <= 256 * 1024 * 1024) { "Save-state data exceeds the 256 MiB limit." }
        check(checkNotNull(native).nativeUnserialize(stateBytes)) {
            "The Libretro core rejected this save state."
        }
    } }

    fun pauseAudio() = synchronized(nativeLock) {
        audioOutput?.pause()
    }

    fun resumeAudio() = synchronized(nativeLock) {
        if (state == State.GAME_LOADED) audioOutput?.resume()
    }

    /** Advances one core frame and captures video through the registered Libretro callback. */
    fun runFrame(): Result<Unit> = runCatching { synchronized(nativeLock) {
        check(state == State.GAME_LOADED) { "Load a ROM before running frames." }
        val bridge = checkNotNull(native)
        check(bridge.nativeRunFrame()) { "Native Libretro core is not running." }
        val sampleCount = bridge.nativeCopyAudioSamples(audioTransferBuffer)
        if (sampleCount > 0) audioOutput?.write(audioTransferBuffer, sampleCount)
    } }

    fun close() = synchronized(nativeLock) {
        audioOutput?.close()
        audioOutput = null
        native?.nativeClose()
        native = null
        videoTransferBuffer = IntArray(2)
        state = State.CLOSED
    }
}
