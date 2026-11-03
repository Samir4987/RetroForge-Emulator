package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalFrameTimeAccumulatorTest {
    @Test
    fun accumulatesThirtyFramesAtSixtyHertzIntoHalfSeconds() {
        val clock = ExternalFrameTimeAccumulator()

        assertEquals(0L, clock.advance(30, 60.0))
        assertEquals(1L, clock.advance(30, 60.0))
        assertEquals(1L, clock.advance(60, 60.0))
    }

    @Test
    fun usesActualThirtyHertzCoreRate() {
        val clock = ExternalFrameTimeAccumulator()

        assertEquals(1L, clock.advance(30, 30.0))
        assertEquals(2L, clock.advance(60, 30.0))
    }

    @Test
    fun preservesFractionalSecondsAtNtscRefreshRate() {
        val clock = ExternalFrameTimeAccumulator()
        var wholeSeconds = 0L

        repeat(60) {
            wholeSeconds += clock.advance(60, 59.94)
        }

        assertEquals(60L, wholeSeconds)
    }

    @Test
    fun fallsBackForInvalidRefreshRatesAndCanReset() {
        val clock = ExternalFrameTimeAccumulator()

        assertEquals(1L, clock.advance(60, Double.NaN))
        clock.reset()
        assertEquals(0L, clock.advance(30, 60.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeFrameCounts() {
        ExternalFrameTimeAccumulator().advance(-1, 60.0)
    }
}
