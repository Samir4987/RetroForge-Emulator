package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameStepAccumulatorTest {
    @Test
    fun oneTimesAlwaysRunsOneFramePerTick() {
        val accumulator = FrameStepAccumulator()
        repeat(8) { assertEquals(1, accumulator.nextSteps(1f)) }
    }

    @Test
    fun fractionalSpeedIsDistributedWithoutLongTermDrift() {
        val accumulator = FrameStepAccumulator()
        val steps = List(6) { accumulator.nextSteps(1.5f) }
        assertEquals(listOf(1, 2, 1, 2, 1, 2), steps)
        assertEquals(9, steps.sum())
    }

    @Test
    fun capsSpeedAndTreatsNonFiniteValuesAsNormalSpeed() {
        val accumulator = FrameStepAccumulator()
        assertEquals(1000, accumulator.nextSteps(5000f))
        assertEquals(1, accumulator.nextSteps(Float.NaN))
        assertEquals(1, accumulator.nextSteps(Float.POSITIVE_INFINITY))
    }

    @Test
    fun resetClearsFractionalRemainder() {
        val accumulator = FrameStepAccumulator()
        assertEquals(1, accumulator.nextSteps(1.5f))
        accumulator.reset()
        assertEquals(1, accumulator.nextSteps(1.5f))
    }
}
