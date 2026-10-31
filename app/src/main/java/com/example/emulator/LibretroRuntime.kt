package com.example.emulator

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.nio.ByteBuffer

/**
 * Android runtime boundary for a Libretro core. A loaded ROM is not considered playable
 * until frames are received and the frontend has been exercised on a real device.
 */
class LibretroRuntime(
    private val frontendLibrary: File,
    private val coreSpec: LibretroCoreSpec,
    private val systemDirectory: File = File(frontendLibrary.parentFile, "system"),
    private val saveDirectory: File = File(frontendLibrary.parentFile, "save"),
    private val assetsDirectory: File = File(frontendLibrary.parentFile, "assets")
) : LibretroCallbacks {
    enum class State { UNAVAILABLE, READY, GAME_LOADED, CLOSED }

    private var native: LibretroNative? = null
    private var audioTrack: AudioTrack? = null
    private val coreFile: File
        get() = File(frontendLibrary.parentFile, coreSpec.libraryFileName)

    @Volatile var state: State = State.UNAVAILABLE
        private set

    var screenBitmap: Bitmap? = null
        private set
    var bottomScreenBitmap: Bitmap? = null
        private set
    var onFrame: ((Bitmap, Bitmap?) -> Unit)? = null

    val isAvailable: Boolean
        get() = state == State.READY || state == State.GAME_LOADED

    init {
        if (frontendLibrary.isFile && coreFile.isFile) state = State.READY
    }

    @Synchronized fun loadRom(rom: File): Result<Unit> {
        if (state == State.CLOSED) return Result.failure(IllegalStateException("Libretro runtime is closed."))
        if (!isAvailable) {
            return Result.failure(IllegalStateException(
                "Libretro frontend/core is not installed for ${coreSpec.system}. Expected ${coreFile.absolutePath}."
            ))
        }
        if (!rom.isFile || rom.length() == 0L) {
            return Result.failure(IllegalArgumentException("ROM file is missing or empty."))
        }
        if (rom.extension.lowercase() !in coreSpec.extensions) {
            return Result.failure(IllegalArgumentException("Unsupported ROM extension: .${rom.extension.lowercase()}"))
        }

        return runCatching {
            systemDirectory.mkdirs()
            saveDirectory.mkdirs()
            assetsDirectory.mkdirs()
            val bridge = native ?: LibretroNative(this).also { native = it }
            bridge.nativeSetDirectories(
                systemDirectory.absolutePath,
                saveDirectory.absolutePath,
                assetsDirectory.absolutePath
            )
            val error = bridge.nativeOpen(coreFile.absolutePath, rom.absolutePath)
            if (error != null) throw IllegalStateException(error)
            state = State.GAME_LOADED
            startAudio()
        }.onFailure {
            runCatching { native?.nativeClose() }
            native = null
            state = if (frontendLibrary.isFile && coreFile.isFile) State.READY else State.UNAVAILABLE
            stopAudio()
        }
    }

    /** Pushes current controls before advancing one emulated frame. */
    @Synchronized fun runFrame(input: EmulatorInputState): Result<Unit> = runCatching {
        check(state == State.GAME_LOADED) { "Load a ROM before running frames." }
        val bridge = checkNotNull(native)
        bridge.nativeSetInputState(toLibretroJoypadMask(input), input.touchX, input.touchY, input.isTouching)
        check(bridge.nativeRunFrame()) { "Native Libretro core is not running." }
    }

    @Synchronized fun saveState(): ByteArray? = runCatching { native?.nativeSerialize() }.getOrNull()

    @Synchronized fun loadState(stateBytes: ByteArray): Boolean =
        runCatching { native?.nativeUnserialize(stateBytes) == true }.getOrDefault(false)

    @Synchronized fun setPaused(paused: Boolean) {
        val track = audioTrack ?: return
        runCatching {
            if (paused && track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
            else if (!paused && track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        }
    }

    override fun onNativeVideoFrame(argbPixels: ByteArray, width: Int, height: Int) {
        if (width <= 0 || height <= 0 || argbPixels.size != width * height * 4) return
        runCatching {
            val buffer = ByteBuffer.wrap(argbPixels)
            if (coreSpec.system == RetroSystem.NDS && width == 256 && height >= 384 && height % 2 == 0) {
                val halfHeight = height / 2
                val top = screenBitmap?.takeIf { it.width == width && it.height == halfHeight }
                    ?: Bitmap.createBitmap(width, halfHeight, Bitmap.Config.ARGB_8888)
                val bottom = bottomScreenBitmap?.takeIf { it.width == width && it.height == halfHeight }
                    ?: Bitmap.createBitmap(width, halfHeight, Bitmap.Config.ARGB_8888)
                top.copyPixelsFromBuffer(buffer)
                bottom.copyPixelsFromBuffer(buffer)
                screenBitmap = top
                bottomScreenBitmap = bottom
            } else {
                val frame = screenBitmap?.takeIf { it.width == width && it.height == height }
                    ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                frame.copyPixelsFromBuffer(buffer)
                screenBitmap = frame
                bottomScreenBitmap = null
            }
        }.onFailure {
            return
        }
        onFrame?.invoke(screenBitmap ?: return, bottomScreenBitmap)
    }

    override fun onNativeAudioSamples(interleavedStereoPcm: ShortArray) {
        val track = audioTrack ?: return
        if (interleavedStereoPcm.isEmpty()) return

        // AudioTrack's non-blocking write may accept only part of a frame's PCM.
        // The previous implementation discarded the unwritten tail, causing
        // avoidable audio gaps. The emulator advances on a background dispatcher,
        // so use a blocking stream write to preserve sample order and continuity.
        var offset = 0
        while (offset < interleavedStereoPcm.size && audioTrack === track) {
            val written = track.write(
                interleavedStereoPcm,
                offset,
                interleavedStereoPcm.size - offset,
                AudioTrack.WRITE_BLOCKING
            )
            if (written <= 0) break
            offset += written
        }
    }

    @Synchronized fun close() {
        if (state == State.CLOSED) return
        runCatching { native?.nativeClose() }
        native = null
        stopAudio()
        screenBitmap = null
        bottomScreenBitmap = null
        state = State.CLOSED
    }

    private fun startAudio() {
        stopAudio()
        val minBuffer = AudioTrack.getMinBufferSize(
            32768,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(32768)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        track.play()
    }

    private fun stopAudio() {
        audioTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        audioTrack = null
    }

}

/** Converts the app's buttons to RETRO_DEVICE_ID_JOYPAD bit positions. */
internal fun toLibretroJoypadMask(input: EmulatorInputState): Int {
    var mask = 0
    fun set(id: Int, pressed: Boolean) { if (pressed) mask = mask or (1 shl id) }
    set(0, input.buttonB)
    set(1, input.buttonY)
    set(2, input.select)
    set(3, input.start)
    set(4, input.dpadUp)
    set(5, input.dpadDown)
    set(6, input.dpadLeft)
    set(7, input.dpadRight)
    set(8, input.buttonA)
    set(9, input.buttonX)
    set(10, input.triggerL)
    set(11, input.triggerR)
    return mask
}
