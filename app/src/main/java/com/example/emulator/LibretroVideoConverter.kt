package com.example.emulator

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteOrder

/**
 * Converts the Libretro frontend's packed frame into Android bitmaps.
 * Azahar presents its two 400x240 displays as one vertically stacked frame.
 */
object LibretroVideoConverter {
    fun splitDualScreenFrame(frame: LibretroVideoFrame): Pair<Bitmap, Bitmap> {
        require(frame.width > 0 && frame.height >= 2 && frame.height % 2 == 0) {
            "Dual-screen output must have a positive width and an even height."
        }
        val displayHeight = frame.height / 2
        return convertRegion(frame, 0, displayHeight) to
            convertRegion(frame, displayHeight, displayHeight)
    }

    private fun convertRegion(
        frame: LibretroVideoFrame,
        firstRow: Int,
        regionHeight: Int
    ): Bitmap {
        val bytesPerPixel = when (frame.pixelFormat) {
            0, 2 -> 2
            1 -> 4
            else -> throw IllegalArgumentException("Unsupported Libretro pixel format: ${frame.pixelFormat}")
        }
        require(frame.pitch.toLong() >= frame.width.toLong() * bytesPerPixel) {
            "Libretro frame pitch is smaller than its pixel row."
        }
        val requiredBytes = Math.multiplyExact(frame.pitch, frame.height)
        val source = frame.pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        require(source.limit() >= requiredBytes) { "Libretro frame buffer is truncated." }

        val pixels = IntArray(Math.multiplyExact(frame.width, regionHeight))
        for (y in 0 until regionHeight) {
            val rowOffset = Math.multiplyExact(firstRow + y, frame.pitch)
            for (x in 0 until frame.width) {
                val offset = rowOffset + x * bytesPerPixel
                pixels[y * frame.width + x] = when (frame.pixelFormat) {
                    // RETRO_PIXEL_FORMAT_0RGB1555: 0RRRRRGGGGGBBBBB.
                    0 -> {
                        val value = source.getShort(offset).toInt() and 0xFFFF
                        val red = (value ushr 10) and 0x1F
                        val green = (value ushr 5) and 0x1F
                        val blue = value and 0x1F
                        Color.rgb(expand5(red), expand5(green), expand5(blue))
                    }
                    // Little-endian XRGB8888 bytes are B, G, R, X.
                    1 -> {
                        val blue = source.get(offset).toInt() and 0xFF
                        val green = source.get(offset + 1).toInt() and 0xFF
                        val red = source.get(offset + 2).toInt() and 0xFF
                        Color.rgb(red, green, blue)
                    }
                    // RETRO_PIXEL_FORMAT_RGB565: RRRRRGGGGGGBBBBB.
                    else -> {
                        val value = source.getShort(offset).toInt() and 0xFFFF
                        val red = (value ushr 11) and 0x1F
                        val green = (value ushr 5) and 0x3F
                        val blue = value and 0x1F
                        Color.rgb(expand5(red), expand6(green), expand5(blue))
                    }
                }
            }
        }
        return Bitmap.createBitmap(frame.width, regionHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, frame.width, 0, 0, frame.width, regionHeight)
        }
    }

    private fun expand5(value: Int): Int = (value shl 3) or (value ushr 2)
    private fun expand6(value: Int): Int = (value shl 2) or (value ushr 4)
}
