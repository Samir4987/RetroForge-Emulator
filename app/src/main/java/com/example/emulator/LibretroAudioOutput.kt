package com.example.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Streams bounded stereo PCM samples captured from the Libretro callbacks to Android.
 * The worker is separate from the frame loop so a blocking AudioTrack write cannot
 * stall core execution or Compose rendering.
 */
internal class LibretroAudioOutput(
    private val runtime: LibretroRuntime
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var track: AudioTrack? = null
    private var worker: Job? = null

    fun start(sampleRate: Int): Boolean {
        if (track != null || sampleRate !in 8_000..192_000) return false
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) return false

        var createdTrack: AudioTrack? = null
        return try {
            val builtTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(maxOf(minBufferSize, sampleRate / 5 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            createdTrack = builtTrack

            if (builtTrack.state != AudioTrack.STATE_INITIALIZED) {
                builtTrack.release()
                return false
            }

            val activeTrack = builtTrack
            track = activeTrack
            activeTrack.play()
            worker = scope.launch {
                val samples = ShortArray(4096)
                while (isActive) {
                    val count = runtime.readAudioSamples(samples)
                    if (count > 0) {
                        var offset = 0
                        while (offset < count && isActive) {
                            val written = activeTrack.write(
                                samples,
                                offset,
                                count - offset,
                                AudioTrack.WRITE_BLOCKING
                            )
                            if (written > 0) {
                                offset += written
                            } else {
                                delay(4)
                                break
                            }
                        }
                    } else {
                        delay(2)
                    }
                }
            }
            true
        } catch (_: Exception) {
            runCatching { createdTrack?.release() }
            track = null
            false
        }
    }

    fun close() {
        val activeTrack = track
        worker?.cancel()
        runCatching { activeTrack?.pause() }
        runCatching { activeTrack?.flush() }
        runCatching { activeTrack?.stop() }
        runBlocking { worker?.cancelAndJoin() }
        worker = null
        scope.cancel()
        runCatching { activeTrack?.release() }
        track = null
    }
}
