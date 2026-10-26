package com.example.emulator

import java.io.File

/**
 * Boundary between the Android UI and a real Libretro JNI frontend.
 *
 * This class deliberately fails closed: a system is NOT considered emulated
 * until the JNI frontend and a matching native core are both present.
 * It does not replace EmulatorCore's demo simulations.
 */
class LibretroRuntime(
    private val nativeLibrary: File,
    private val coreSpec: LibretroCoreSpec
) {
    enum class State { UNAVAILABLE, READY, GAME_LOADED, CLOSED }

    var state: State = State.UNAVAILABLE
        private set

    val isAvailable: Boolean
        get() = state == State.READY || state == State.GAME_LOADED

    init {
        // Native loading is deferred until both required binaries exist.
        if (nativeLibrary.isFile && coreSpec.libraryFileName.isNotBlank()) {
            state = State.READY
        }
    }

    /**
     * Returns false until the JNI frontend is built and its ABI-specific core
     * is installed. Never silently reports a demo simulation as real emulation.
     */
    fun loadRom(rom: File): Result<Unit> {
        if (!isAvailable) {
            return Result.failure(
                IllegalStateException("Libretro frontend/core is not installed for ${coreSpec.system}.")
            )
        }
        if (!rom.isFile || rom.length() == 0L) {
            return Result.failure(IllegalArgumentException("ROM file is missing or empty."))
        }
        val extension = rom.extension.lowercase()
        if (extension !in coreSpec.extensions) {
            return Result.failure(IllegalArgumentException("Unsupported ROM extension: .$extension"))
        }

        // This layer must not claim success before JNI is connected.
        return Result.failure(
            UnsupportedOperationException("JNI Libretro bridge is not connected yet.")
        )
    }

    fun close() {
        state = State.CLOSED
    }
}
