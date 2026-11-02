package com.example.emulator

/**
 * Converts a requested speed multiplier into whole emulated frames per display tick.
 * Fractional values such as 1.5x are distributed across ticks instead of truncated.
 */
internal class FrameStepAccumulator {
    private var remainder = 0.0

    fun nextSteps(multiplier: Float): Int {
        val speed = if (multiplier.isFinite()) {
            multiplier.toDouble().coerceIn(1.0, 1000.0)
        } else {
            1.0
        }
        remainder += speed
        val steps = remainder.toInt().coerceAtLeast(1)
        remainder -= steps
        return steps
    }

    fun reset() {
        remainder = 0.0
    }
}
