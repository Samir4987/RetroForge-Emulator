package com.example.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Non-blocking stereo PCM output for frames drained from the native Libretro audio queue.
 * Audio is best-effort: device audio initialization must never prevent a ROM from loading.
 */
internal class LibretroAudioOutput(sampleRateHz: Int) : AutoCloseable {
    private val track: AudioTrack? = runCatching {
        val rate = sampleRateHz.coerceIn(8_000, 192_000)
        val minimumBufferBytes = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minimumBufferBytes > 0) { "Android audio device rejected the requested PCM format." }

        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimumBufferBytes, rate * 4 / 5))
            .build()
            .also { audioTrack ->
                check(audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                    "Android AudioTrack failed to initialize."
                }
                audioTrack.play()
            }
    }.getOrNull()

    fun pause() {
        track?.let { audioTrack ->
            runCatching {
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.pause()
                    audioTrack.flush()
                }
            }
        }
    }

    fun resume() {
        track?.let { audioTrack ->
            runCatching {
                if (audioTrack.state == AudioTrack.STATE_INITIALIZED &&
                    audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING
                ) {
                    audioTrack.play()
                }
            }
        }
    }

    fun write(samples: ShortArray, sampleCount: Int = samples.size) {
        val audioTrack = track ?: return
        if (sampleCount <= 0 || sampleCount > samples.size ||
            audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING
        ) return
        runCatching {
            audioTrack.write(samples, 0, sampleCount, AudioTrack.WRITE_NON_BLOCKING)
        }
    }

    override fun close() {
        track?.let { audioTrack ->
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.release() }
        }
    }
}
