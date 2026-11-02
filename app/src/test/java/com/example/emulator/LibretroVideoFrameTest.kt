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
    @Test
    fun splitsVerticallyStackedNintendoDsScreens() {
        val pixels = IntArray(256 * 384) { it }
        val screens = LibretroVideoFrame(256, 384, pixels).toNintendoDsScreens()

        assertEquals(256, screens.top.width)
        assertEquals(192, screens.top.height)
        assertEquals(0, screens.top.argbPixels.first())
        assertEquals(256 * 192 - 1, screens.top.argbPixels.last())
        assertEquals(256 * 192, screens.bottom.argbPixels.first())
        assertEquals(256 * 384 - 1, screens.bottom.argbPixels.last())
    }

    @Test
    fun splitsSideBySideNintendoDsScreens() {
        val pixels = IntArray(512 * 192) { index ->
            val y = index / 512
            val x = index % 512
            y * 1000 + x
        }
        val screens = LibretroVideoFrame(512, 192, pixels).toNintendoDsScreens()

        assertEquals(192 * 1000 + 255, screens.top.argbPixels.last())
        assertEquals(192 * 1000 + 511, screens.bottom.argbPixels.last())
    }

    @Test
    fun rejectsUnsupportedNintendoDsFrameLayout() {
        assertThrows(IllegalArgumentException::class.java) {
            LibretroVideoFrame(256, 192, IntArray(256 * 192)).toNintendoDsScreens()
        }
    }

}
