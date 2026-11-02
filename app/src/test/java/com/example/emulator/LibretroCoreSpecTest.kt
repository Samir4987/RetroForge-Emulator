package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroCoreSpecTest {
    @Test
    fun ndsUsesMelonDsDsCandidateRatherThanSkyEmu() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.NDS)

        assertNotNull(spec)
        assertEquals("melondsds_libretro_android.so", spec!!.libraryFileName)
        assertEquals("https://github.com/JesseTG/melonds-ds", spec.upstreamUrl)
        assertEquals(setOf("nds", "ds", "dsi", "ids"), spec.extensions)
    }

    @Test
    fun ndsExtensionsResolveCaseInsensitively() {
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension(".NDS")?.system)
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension("ds")?.system)
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension(".DS")?.system)
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension("dsi")?.system)
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension("IDS")?.system)
        assertEquals(RetroSystem.NDS, RetroSystem.fromExtension("dsi"))
        assertEquals(RetroSystem.NDS, RetroSystem.fromExtension("ids"))
    }

    @Test
    fun ndsAndN64RemainSeparateCoreCandidates() {
        val nds = LibretroCoreSpec.forSystem(RetroSystem.NDS)
        val n64 = LibretroCoreSpec.forSystem(RetroSystem.N64)

        assertNotNull(nds)
        assertNotNull(n64)
        assertTrue(nds!!.upstreamUrl.contains("melonds-ds"))
        assertTrue(n64!!.upstreamUrl.contains("mupen64plus"))
        assertTrue(nds.libraryFileName != n64.libraryFileName)
    }
}
