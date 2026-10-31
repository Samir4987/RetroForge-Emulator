package com.example.data.local

import com.example.emulator.RetroSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RomHeaderScannerTest {
    @Test
    fun parsesUppercaseAndDottedGbExtension() {
        val header = gameBoyHeader(cgbFlag = 0x00)

        val result = RomHeaderScanner.parseHeader(header, ".GB", "Demo.gb")

        assertEquals(RetroSystem.GB, result.system)
        assertEquals("DMG-00", result.gameCode)
    }

    @Test
    fun parsesUppercaseGbcExtension() {
        val header = gameBoyHeader(cgbFlag = 0x00)

        val result = RomHeaderScanner.parseHeader(header, "GBC", "Demo.gbc")

        assertEquals(RetroSystem.GBC, result.system)
        assertEquals("CGB-00", result.gameCode)
    }

    @Test
    fun detectsColorHardwareFromGbHeaderFlag() {
        val header = gameBoyHeader(cgbFlag = 0x80)

        val result = RomHeaderScanner.parseHeader(header, "gb", "ColorGame.gb")

        assertEquals(RetroSystem.GBC, result.system)
    }

    @Test
    fun detectsColorOnlyHeaderFlag() {
        val header = gameBoyHeader(cgbFlag = 0xC0)

        val result = RomHeaderScanner.parseHeader(header, "gb", "ColorOnlyGame.gb")

        assertEquals(RetroSystem.GBC, result.system)
    }

    @Test
    fun shortGbcHeaderUsesNormalizedExtensionFallback() {
        val result = RomHeaderScanner.parseHeader(ByteArray(0x80), ".GBC", "Short.gbc")

        assertEquals(RetroSystem.GBC, result.system)
    }

    @Test
    fun readsHeaderUntilEofWhenStreamReturnsSmallChunks() {
        val bytes = ByteArray(0x180) { it.toByte() }
        val chunkedStream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return super.read(buffer, offset, minOf(length, 7))
            }
        }

        val (header, bytesRead) = RomHeaderScanner.readHeaderBytes(chunkedStream)

        assertEquals(bytes.size, bytesRead)
        assertEquals(bytes.toList(), header.take(bytes.size))
    }

    private fun gameBoyHeader(cgbFlag: Int): ByteArray =
        ByteArray(0x150).apply {
            this[0x0134] = 'T'.code.toByte()
            this[0x0135] = 'E'.code.toByte()
            this[0x0136] = 'S'.code.toByte()
            this[0x0137] = 'T'.code.toByte()
            this[0x0143] = cgbFlag.toByte()
        }
}
