package com.example

import com.example.data.local.RomHeaderScanner
import com.example.emulator.RetroSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class RomHeaderScannerTest {

    @Test
    fun parseGbHeader_extractsTitleAndSystem() {
        val bytes = ByteArray(512)
        // Set Nintendo logo byte to indicate GB
        bytes[0x0104] = 0xCE.toByte()

        // Write title "POKEMON RED" at 0x0134
        val titleBytes = "POKEMON RED\u0000\u0000\u0000\u0000\u0000".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, 0x0134, 16)

        // Set cart type to MBC3+RTC (0x10)
        bytes[0x0147] = 0x10.toByte()
        // Set checksum to 0x4A
        bytes[0x014D] = 0x4A.toByte()

        val result = RomHeaderScanner.parseHeader(bytes, "gb", "Pokemon Red.gb")

        assertEquals("Pokemon Red", result.title)
        assertEquals(RetroSystem.GB, result.system)
        assertEquals("MBC3+RTC", result.cartridgeMapper)
        assertEquals("0x4A", result.headerChecksum)
        assertEquals("Role-Playing (RPG)", result.genre)
        assertNotNull(result.boxArtCoverKey)
    }

    @Test
    fun parseGbcHeader_detectsColorFlag() {
        val bytes = ByteArray(512)
        bytes[0x0104] = 0xCE.toByte()
        val titleBytes = "MARIO LAND\u0000\u0000".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, 0x0134, 16)

        // Set CGB flag 0x80 (GBC compatible)
        bytes[0x0143] = 0x80.toByte()
        bytes[0x0147] = 0x19.toByte() // MBC5

        val result = RomHeaderScanner.parseHeader(bytes, "gbc", "Crystal.gbc")

        assertEquals(RetroSystem.GBC, result.system)
        assertEquals("MBC5", result.cartridgeMapper)
        assertEquals("Platformer", result.genre)
    }

    @Test
    fun parseGbaHeader_extractsGameCodeAndPublisher() {
        val bytes = ByteArray(512)
        // Fixed 0x96 byte at 0x00B2
        bytes[0x00B2] = 0x96.toByte()

        val titleBytes = "POKEMON EMER".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, 0x00A0, 12)

        val codeBytes = "BPEE".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(codeBytes, 0, bytes, 0x00AC, 4)

        val makerBytes = "01".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(makerBytes, 0, bytes, 0x00B0, 2)

        val result = RomHeaderScanner.parseHeader(bytes, "gba", "Pokemon_Emerald.gba")

        assertEquals("Pokemon Emer", result.title)
        assertEquals(RetroSystem.GBA, result.system)
        assertEquals("BPEE", result.gameCode)
        assertEquals("Nintendo", result.publisher)
        assertEquals("ARM7TDMI 32-Bit", result.cartridgeMapper)
    }

    @Test
    fun parseN64Header_bigEndianParsing() {
        val bytes = ByteArray(512)
        // Big-endian magic 0x80371240
        bytes[0] = 0x80.toByte()
        bytes[1] = 0x37.toByte()
        bytes[2] = 0x12.toByte()
        bytes[3] = 0x40.toByte()

        // Title at 0x0020
        val titleBytes = "SUPER MARIO 64      ".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, 0x0020, 20)

        // Cart ID 'SM' at 0x003C
        bytes[0x003C] = 'S'.code.toByte()
        bytes[0x003D] = 'M'.code.toByte()
        bytes[0x003E] = 'E'.code.toByte() // USA

        val result = RomHeaderScanner.parseHeader(bytes, "z64", "SuperMario64.z64")

        assertEquals("Super Mario 64", result.title)
        assertEquals(RetroSystem.N64, result.system)
        assertEquals("NUS-SME", result.gameCode)
        assertEquals("Nintendo 64", result.publisher)
        assertEquals("Platformer", result.genre)
    }

    @Test
    fun parseNdsHeader_extractsNtrCode() {
        val bytes = ByteArray(512)
        val titleBytes = "MARIO KART  ".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, 0x0000, 12)

        val codeBytes = "AMCE".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(codeBytes, 0, bytes, 0x000C, 4)

        val result = RomHeaderScanner.parseHeader(bytes, "nds", "MarioKartDS.nds")

        assertEquals("Mario Kart", result.title)
        assertEquals(RetroSystem.NDS, result.system)
        assertEquals("NTR-AMCE", result.gameCode)
        assertEquals("Racing", result.genre)
        assertTrue(result.cartridgeMapper.contains("Touch"))
    }
}
