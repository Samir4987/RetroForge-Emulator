package com.example.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AudioSynthesizer {
    private val sampleRate = 22050
    private var isEnabled = true
    private var volume = 0.8f
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    fun playBeep(frequencyHz: Float, durationMs: Int, waveType: WaveType = WaveType.SQUARE) {
        if (!isEnabled || volume <= 0f) return
        scope.launch {
            try {
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
                val buffer = ShortArray(numSamples)
                val amplitude = (Short.MAX_VALUE * 0.4f * volume).toInt()

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val sample = when (waveType) {
                        WaveType.SQUARE -> {
                            val cycle = (time * frequencyHz) % 1.0
                            if (cycle < 0.5) amplitude else -amplitude
                        }
                        WaveType.SINE -> {
                            (sin(2.0 * PI * frequencyHz * time) * amplitude).toInt()
                        }
                        WaveType.NOISE -> {
                            Random.nextInt(-amplitude, amplitude)
                        }
                    }
                    buffer[i] = sample.toShort()
                }

                writeAndPlay(buffer)
            } catch (_: Exception) {
                // Audio device fallback
            }
        }
    }

    fun playJump() {
        if (!isEnabled || volume <= 0f) return
        scope.launch {
            try {
                val durationMs = 120
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val amplitude = (Short.MAX_VALUE * 0.4f * volume).toInt()

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val freq = 220f + progress * 440f // Frequency sweep up
                    val time = i.toDouble() / sampleRate
                    val cycle = (time * freq) % 1.0
                    buffer[i] = (if (cycle < 0.5) amplitude else -amplitude).toShort()
                }
                writeAndPlay(buffer)
            } catch (_: Exception) {}
        }
    }

    fun playLaser() {
        if (!isEnabled || volume <= 0f) return
        scope.launch {
            try {
                val durationMs = 100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val amplitude = (Short.MAX_VALUE * 0.45f * volume).toInt()

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val freq = 900f - progress * 700f // Frequency sweep down
                    val time = i.toDouble() / sampleRate
                    val cycle = (time * freq) % 1.0
                    buffer[i] = (if (cycle < 0.5) amplitude else -amplitude).toShort()
                }
                writeAndPlay(buffer)
            } catch (_: Exception) {}
        }
    }

    fun playCoin() {
        if (!isEnabled || volume <= 0f) return
        scope.launch {
            try {
                val numSamples = (sampleRate * 0.15).toInt()
                val buffer = ShortArray(numSamples)
                val amplitude = (Short.MAX_VALUE * 0.4f * volume).toInt()

                for (i in 0 until numSamples) {
                    val freq = if (i < numSamples / 2) 987.77f else 1318.51f // B5 -> E6 chime
                    val time = i.toDouble() / sampleRate
                    val cycle = (time * freq) % 1.0
                    buffer[i] = (if (cycle < 0.5) amplitude else -amplitude).toShort()
                }
                writeAndPlay(buffer)
            } catch (_: Exception) {}
        }
    }

    fun playHit() {
        if (!isEnabled || volume <= 0f) return
        scope.launch {
            try {
                val numSamples = (sampleRate * 0.08).toInt()
                val buffer = ShortArray(numSamples)
                val amplitude = (Short.MAX_VALUE * 0.5f * volume).toInt()
                for (i in 0 until numSamples) {
                    buffer[i] = Random.nextInt(-amplitude, amplitude).toShort()
                }
                writeAndPlay(buffer)
            } catch (_: Exception) {}
        }
    }

    private fun writeAndPlay(buffer: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
    }

    enum class WaveType {
        SQUARE, SINE, NOISE
    }
}
