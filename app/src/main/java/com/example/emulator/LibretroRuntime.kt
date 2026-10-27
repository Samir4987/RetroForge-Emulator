package com.example.emulator

import android.graphics.Bitmap
import java.io.File

/**
 * Boundary between the Android UI and the Libretro JNI frontend.
 *
 * A successful ROM load means the native core accepted the ROM; it does not
 * by itself mean video, audio, input, saves, or UI presentation are complete.
 */
class LibretroRuntime(
    private val frontendLibrary: File,
    private val coreSpec: LibretroCoreSpec
) {
    enum class State { UNAVAILABLE, READY, GAME_LOADED, CLOSED }

    @Volatile private var native: LibretroNative? = null
    private var audioOutput: LibretroAudioOutput? = null
    private val coreFile: File
        get() = File(frontendLibrary.parentFile, coreSpec.libraryFileName)

    @Volatile var state: State = State.UNAVAILABLE
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
            audioOutput?.close()
            audioOutput = null
            val bridge = native ?: LibretroNative().also { native = it }
            try {
                val error = bridge.nativeOpen(coreFile.absolutePath, rom.absolutePath)
                if (error != null) throw IllegalStateException(error)
                state = State.GAME_LOADED
                val output = LibretroAudioOutput(this)
                if (output.start(bridge.nativeGetAudioSampleRate().toInt())) {
                    audioOutput = output
                } else {
                    output.close()
                }
            } catch (failure: Throwable) {
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

    /** Advances one core frame. The core may emit a new video frame during this call. */
    fun runFrame(): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before running frames." }
        check(native?.nativeRunFrame() == true) { "Native Libretro core is not running." }
    }

    /** Updates standard Libretro joypad buttons for player one. */
    fun updateInput(input: EmulatorInputState) {
        if (state != State.GAME_LOADED) return
        native?.nativeSetJoypadState(input.toLibretroJoypadMask())
    }

    /** Copies queued stereo PCM samples into the supplied buffer for the audio output worker. */
    internal fun readAudioSamples(destination: ShortArray): Int {
        if (state != State.GAME_LOADED) return 0
        return native?.nativeReadAudio(destination)?.coerceIn(0, destination.size - (destination.size % 2)) ?: 0
    }

    /**
     * Copies the most recent core frame into an Android ARGB_8888 bitmap.
     * Returns null until the core has submitted a valid frame.
     */
    fun readVideoFrame(): Bitmap? {
        if (state != State.GAME_LOADED) return null
        val bridge = native ?: return null
        val dimensions = bridge.nativeVideoFrameInfo() ?: return null
        if (dimensions.size < 2) return null
        val width = dimensions[0]
        val height = dimensions[1]
        if (width <= 0 || height <= 0 || width > 1024 || height > 1024) return null
        val count = width.toLong() * height.toLong()
        if (count > Int.MAX_VALUE) return null

        val pixels = IntArray(count.toInt())
        if (!bridge.nativeCopyVideoFrame(pixels)) return null
        return runCatching {
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    fun close() {
        audioOutput?.close()
        audioOutput = null
        native?.nativeClose()
        native = null
        state = State.CLOSED
    }
}


/** Maps RetroForge's touch/controller state to standard Libretro joypad IDs. */
internal fun EmulatorInputState.toLibretroJoypadMask(): Int {
    var mask = 0
    fun set(id: Int, pressed: Boolean) {
        if (pressed) mask = mask or (1 shl id)
    }

    set(0, buttonB)
    set(1, buttonY)
    set(2, select)
    set(3, start)
    set(4, dpadUp)
    set(5, dpadDown)
    set(6, dpadLeft)
    set(7, dpadRight)
    set(8, buttonA)
    set(9, buttonX)
    set(10, triggerL)
    set(11, triggerR)
    return mask
}
