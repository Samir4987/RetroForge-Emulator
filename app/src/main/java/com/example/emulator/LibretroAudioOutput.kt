package com.example.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.Closeable

/**
 * Low-latency stereo PCM16 output for a running Libretro core.
 *
 * The caller drains samples from LibretroRuntime on its emulator worker and
 * passes them here. Writes are non-blocking so audio back-pressure never stalls
 * emulation or Compose rendering.
 */
class LibretroAudioOutput(sampleRate: Int) : Closeable {
    private val track: AudioTrack
    private var closed = false

    init {
        require(sampleRate in 8_000..192_000) { "Unsupported core audio sample rate: $sampleRate" }
        val minimumBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minimumBytes > 0) { "Android does not support the requested stereo PCM format." }
        val bufferBytes = (minimumBytes * 2).coerceAtLeast(8_192)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) {
            track.release()
            "Android AudioTrack failed to initialize."
        }
        track.play()
    }

    /** Samples must be interleaved L,R,L,R... PCM16. Returns samples accepted by AudioTrack. */
    @Synchronized
    fun write(samples: ShortArray): Int {
        if (closed || samples.isEmpty()) return 0
        return track.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
            .coerceAtLeast(0)
    }

    @Synchronized
    fun pause() {
        if (!closed && track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    @Synchronized
    fun resume() {
        if (!closed && track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { track.pause() }
        runCatching { track.flush() }
        track.release()
    }
}
