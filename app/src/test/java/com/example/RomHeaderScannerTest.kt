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
        val titleBytes = "CRYSTAL CAVERN\u0000\u0000".toByteArray(StandardCharsets.US_ASCII)
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
    @Test
    fun parseN3dsCciHeader_detectsNcsdContainer() {
        val bytes = ByteArray(1024)
        val signature = "NCSD".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(signature, 0, bytes, 0x100, signature.size)

        val result = RomHeaderScanner.parseHeader(bytes, "cci", "Pokemon_X.3ds")

        assertEquals(RetroSystem.N3DS, result.system)
        assertEquals("Pokemon X", result.title)
        assertEquals("3DS-NCSD", result.gameCode)
        assertEquals("SIGNATURE-OK", result.headerChecksum)
    }

    @Test
    fun parseN3dsCxiHeader_detectsNcchContainer() {
        val bytes = ByteArray(1024)
        val signature = "NCCH".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(signature, 0, bytes, 0x100, signature.size)

        val result = RomHeaderScanner.parseHeader(bytes, "cxi", "Homebrew_App.cxi")

        assertEquals(RetroSystem.N3DS, result.system)
        assertEquals("Homebrew App", result.title)
        assertEquals("3DS-NCCH", result.gameCode)
        assertEquals("SIGNATURE-OK", result.headerChecksum)
    }

    @Test
    fun parseN3ds3dsxHeaderRecognizesHomebrewExecutable() {
        val bytes = ByteArray(1024)
        System.arraycopy("3DSX".toByteArray(StandardCharsets.US_ASCII), 0, bytes, 0, 4)

        val result = RomHeaderScanner.parseHeader(bytes, "3dsx", "Homebrew.3dsx")

        assertEquals(RetroSystem.N3DS, result.system)
        assertEquals("3DS-3DSX", result.gameCode)
        assertEquals("SIGNATURE-OK", result.headerChecksum)
    }

    @Test
    fun parseN3dsElfHeaderRecognizesExecutable() {
        val bytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(1020)

        val result = RomHeaderScanner.parseHeader(bytes, "elf", "Homebrew.elf")

        assertEquals(RetroSystem.N3DS, result.system)
        assertEquals("3DS-ELF", result.gameCode)
        assertEquals("SIGNATURE-OK", result.headerChecksum)
    }

    @Test
    fun allDocumentedAzaharExtensionsResolveToNintendo3ds() {
        val extensions = listOf("3ds", "3dsx", "z3dsx", "elf", "axf", "cci", "zcci", "cxi", "zcxi", "app")
        extensions.forEach { extension ->
            assertEquals(extension, RetroSystem.N3DS, RetroSystem.fromExtension(extension))
        }
    }

}
