package com.example.emulator

import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibretroVideoConverterTest {
    @Test
    fun splitsStackedXrgb8888FrameIntoTopAndBottomDisplays() {
        val bytes = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN)
        // Top row: red, green. Bottom row: blue, white. XRGB8888 is B,G,R,X.
        bytes.put(byteArrayOf(
            0, 0, -1, 0, 0, -1, 0, 0,
            -1, 0, 0, 0, -1, -1, -1, 0
        ))
        bytes.position(0)
        val frame = LibretroVideoFrame(2, 2, 8, 1, bytes)

        val (top, bottom) = LibretroVideoConverter.splitDualScreenFrame(frame)

        assertEquals(2, top.width)
        assertEquals(1, top.height)
        assertEquals(Color.RED, top.getPixel(0, 0))
        assertEquals(Color.GREEN, top.getPixel(1, 0))
        assertEquals(Color.BLUE, bottom.getPixel(0, 0))
        assertEquals(Color.WHITE, bottom.getPixel(1, 0))
    }

    @Test
    fun convertsRgb565Pixels() {
        val bytes = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0xF800.toShort())
            .putShort(0x07E0.toShort())
            .putShort(0xF800.toShort())
            .putShort(0x07E0.toShort())
        bytes.position(0)
        val frame = LibretroVideoFrame(2, 2, 4, 2, bytes)

        val (top, bottom) = LibretroVideoConverter.splitDualScreenFrame(frame)

        assertEquals(Color.RED, top.getPixel(0, 0))
        assertEquals(Color.GREEN, top.getPixel(1, 0))
        assertEquals(Color.RED, bottom.getPixel(0, 0))
        assertEquals(Color.GREEN, bottom.getPixel(1, 0))
    }
    @Test
    fun converts0Rgb1555Pixels() {
        val bytes = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x7C00.toShort())
            .putShort(0x03E0.toShort())
            .putShort(0x001F.toShort())
            .putShort(0x7FFF.toShort())
        bytes.position(0)
        val frame = LibretroVideoFrame(2, 2, 4, 0, bytes)

        val (top, bottom) = LibretroVideoConverter.splitDualScreenFrame(frame)

        assertEquals(Color.RED, top.getPixel(0, 0))
        assertEquals(Color.GREEN, top.getPixel(1, 0))
        assertEquals(Color.BLUE, bottom.getPixel(0, 0))
        assertEquals(Color.WHITE, bottom.getPixel(1, 0))
    }

}
