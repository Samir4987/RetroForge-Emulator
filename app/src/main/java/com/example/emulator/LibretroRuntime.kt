package com.example.emulator

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

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
    private val audioQueue = ArrayBlockingQueue<ShortArray>(16)
    @Volatile private var audioPaused = false
    @Volatile private var audioWorker: Thread? = null
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

    /** Native core timing in frames per second, used to pace emulation accurately. */
    val frameRate: Double
        get() = native?.nativeGetFrameRate()?.takeIf { it.isFinite() && it in 1.0..240.0 }
            ?: DEFAULT_FRAME_RATE

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
            startAudio(bridge.nativeGetSampleRate())
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
        audioPaused = paused
        if (paused) audioQueue.clear()
        val track = audioTrack ?: return
        runCatching {
            if (paused && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
            } else if (!paused && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
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

    /**
     * Native callbacks must never block the emulation thread on speaker playback.
     * A bounded queue drops the oldest audio chunk during fast-forward rather than
     * slowing the core or allowing unbounded latency to accumulate.
     */
    override fun onNativeAudioSamples(interleavedStereoPcm: ShortArray) {
        if (interleavedStereoPcm.isEmpty() || audioTrack == null || audioPaused) return
        if (!audioQueue.offer(interleavedStereoPcm)) {
            audioQueue.poll()
            audioQueue.offer(interleavedStereoPcm)
        }
    }

    private fun pumpAudio(track: AudioTrack) {
        var currentChunk: ShortArray? = null
        var offset = 0
        while (!Thread.currentThread().isInterrupted && audioTrack === track) {
            if (audioPaused || track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            if (currentChunk == null) {
                currentChunk = try {
                    audioQueue.poll(100, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                }
                offset = 0
                if (currentChunk == null) continue
            }

            val chunk = currentChunk ?: continue
            val written = runCatching {
                track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            }.getOrDefault(-1)
            if (written > 0) {
                offset += written
                if (offset >= chunk.size) currentChunk = null
            } else if (written < 0) {
                currentChunk = null
                offset = 0
            } else {
                try {
                    Thread.sleep(2)
                } catch (_: InterruptedException) {
                    break
                }
            }
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

    private fun startAudio(reportedSampleRate: Double) {
        stopAudio()
        // Libretro timing is authoritative. Keep a defensive fallback for malformed cores.
        val sampleRate = reportedSampleRate.takeIf { it.isFinite() && it in 8000.0..192000.0 }
            ?.toInt() ?: DEFAULT_SAMPLE_RATE
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).let { if (it > 0) it else 4096 }.coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) { "Android could not initialize NDS audio output." }
        audioQueue.clear()
        audioPaused = false
        audioTrack = track
        track.play()
        audioWorker = Thread({ pumpAudio(track) }, "RetroForge-Audio").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopAudio() {
        audioPaused = true
        audioQueue.clear()
        audioWorker?.let { worker ->
            worker.interrupt()
            runCatching { worker.join(250) }
        }
        audioWorker = null
        audioTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        audioTrack = null
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 32768
        const val DEFAULT_FRAME_RATE = 60.0
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
