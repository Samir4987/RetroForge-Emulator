package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroNdsCoreSpecTest {
    @Test
    fun ndsUsesMelonDsDsCoreAndSupportedExtensions() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.NDS)

        assertNotNull(spec)
        assertEquals("melondsds_libretro_android.so", spec!!.libraryFileName)
        assertEquals("https://git.libretro.com/libretro/melonds-ds", spec.upstreamUrl)
        assertTrue("nds" in spec.extensions)
        assertTrue("dsi" in spec.extensions)
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension(".nds")?.system)
        assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension("DSI")?.system)
    }
}
