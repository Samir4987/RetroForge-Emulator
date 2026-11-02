package com.example.emulator

import java.io.File

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
            try {
                val error = bridge.nativeOpen(coreFile.absolutePath, rom.absolutePath)
                if (error != null) throw IllegalStateException(error)
                state = State.GAME_LOADED
            } catch (failure: Throwable) {
                // nativeOpen closes any previously loaded core before attempting a new load.
                // Keep Kotlin state in sync if the replacement ROM fails to load.
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

    /** Advances one core frame and captures video through the registered Libretro callback. */
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
