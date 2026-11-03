package com.example.emulator

/**
 * Converts externally advanced emulator frames into whole play-time seconds
 * without assuming every console runs at exactly 60 Hz.
 */
class ExternalFrameTimeAccumulator {
    private var fractionalSeconds = 0.0

    @Synchronized
    fun advance(framesAdvanced: Int, framesPerSecond: Double): Long {
        require(framesAdvanced >= 0) { "Frame count cannot be negative." }
        if (framesAdvanced == 0) return 0L

        val safeFramesPerSecond = framesPerSecond
            .takeIf { it.isFinite() && it in MIN_FPS..MAX_FPS }
            ?: DEFAULT_FPS
        fractionalSeconds += framesAdvanced / safeFramesPerSecond

        val wholeSeconds = fractionalSeconds.toLong()
        fractionalSeconds -= wholeSeconds
        return wholeSeconds
    }

    @Synchronized
    fun reset() {
        fractionalSeconds = 0.0
    }

    private companion object {
        const val DEFAULT_FPS = 60.0
        const val MIN_FPS = 1.0
        const val MAX_FPS = 1000.0
    }
}
