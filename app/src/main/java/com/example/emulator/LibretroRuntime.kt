package com.example.emulator

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

    private var native: LibretroNative? = null
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
            val error = bridge.nativeOpen(coreFile.absolutePath, rom.absolutePath)
            if (error != null) {
                bridge.nativeClose()
                throw IllegalStateException(error)
            }
            state = State.GAME_LOADED
        }
    }

    /** Advances one core frame. Rendering/audio/input callbacks are not yet implemented. */
    fun runFrame(): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before running frames." }
        check(native?.nativeRunFrame() == true) { "Native Libretro core is not running." }
    }

    fun close() {
        native?.nativeClose()
        native = null
        state = State.CLOSED
    }
}
