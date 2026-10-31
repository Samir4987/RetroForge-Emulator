package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LibretroCoreSpecTest {
    @Test
    fun gbCandidateUsesMgbaAndOnlyGbExtension() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.GB)
        assertNotNull(spec)
        assertEquals("mgba_libretro_android.so", spec?.libraryFileName)
        assertEquals("https://github.com/libretro/mgba", spec?.upstreamUrl)
        assertEquals(setOf("gb"), spec?.extensions)
    }

    @Test
    fun gbcCandidateUsesMgbaAndOnlyGbcExtension() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.GBC)
        assertNotNull(spec)
        assertEquals("skyemu_libretro_android.so", spec?.libraryFileName)
        assertEquals("https://github.com/libretro/SkyEmu", spec?.upstreamUrl)
        assertEquals(setOf("gbc"), spec?.extensions)
    }

    @Test
    fun extensionLookupRecognizesGbAndGbcIndependently() {
        assertEquals(RetroSystem.GB, LibretroCoreSpec.forExtension(".GB")?.system)
        assertEquals(RetroSystem.GBC, LibretroCoreSpec.forExtension("GBC")?.system)
        assertNull(LibretroCoreSpec.forExtension("gbx"))
    }

    @Test
    fun gbaCandidateUsesMgbALibretroLibrary() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.GBA)
        assertNotNull(spec)
        assertEquals("mgba_libretro_android.so", spec?.libraryFileName)
        assertEquals(setOf("gba"), spec?.extensions)
    }
}
