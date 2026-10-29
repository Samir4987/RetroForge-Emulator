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
    @Test
    fun ndsUsesDualScreenResolutionAndTouchDisplay() {
        val system = RetroSystem.NDS
        assertEquals(256, system.screenWidth)
        assertEquals(192, system.screenHeight)
        assertEquals(256, system.bottomScreenWidth)
        assertEquals(192, system.bottomScreenHeight)
        assertTrue(system.isDualScreen)
        assertEquals(RetroSystem.NDS, RetroSystem.fromExtension("dsi"))
    }

    @Test
    fun controlsMapToLibretroJoypadIds() {
        val input = EmulatorInputState(
            dpadUp = true,
            dpadRight = true,
            buttonA = true,
            buttonB = true,
            triggerL = true,
            start = true
        )
        val expected = (1 shl 0) or (1 shl 3) or (1 shl 4) or (1 shl 7) or (1 shl 8) or (1 shl 10)
        assertEquals(expected, toLibretroJoypadMask(input))
    }

}
