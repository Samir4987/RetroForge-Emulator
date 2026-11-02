package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibretroVideoFrameTest {
    @Test
    fun acceptsTightlyPackedArgbPixels() {
        val frame = LibretroVideoFrame(
            width = 2,
            height = 2,
            argbPixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        )

        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertEquals(4, frame.argbPixels.size)
        assertEquals(0xFFFF0000.toInt(), frame.argbPixels[0])
    }

    @Test
    fun rejectsPixelCountThatDoesNotMatchDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            LibretroVideoFrame(width = 2, height = 2, argbPixels = intArrayOf(0, 1, 2))
        }
    }

    @Test
    fun rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            LibretroVideoFrame(width = 0, height = 1, argbPixels = intArrayOf())
        }
    }
}
