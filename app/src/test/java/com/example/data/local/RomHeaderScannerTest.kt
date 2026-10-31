package com.example.data.local

import com.example.emulator.RetroSystem
import org.junit.Assert.assertEquals
import org.junit.Test

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

    private fun gameBoyHeader(cgbFlag: Int): ByteArray =
        ByteArray(0x150).apply {
            this[0x0134] = 'T'.code.toByte()
            this[0x0135] = 'E'.code.toByte()
            this[0x0136] = 'S'.code.toByte()
            this[0x0137] = 'T'.code.toByte()
            this[0x0143] = cgbFlag.toByte()
        }
}
