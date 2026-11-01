package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroNdsCoreSpecTest {
    @Test
    fun ndsUsesMelonDsDsCoreAndAllSupportedExtensions() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.NDS)

        assertNotNull(spec)
        assertEquals("melondsds_libretro_android.so", spec!!.libraryFileName)
        assertEquals("https://git.libretro.com/libretro/melonds-ds", spec.upstreamUrl)
        assertEquals(setOf("nds", "ds", "dsi", "srl"), spec.extensions)

        spec.extensions.forEach { extension ->
            assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension(extension)?.system)
            assertEquals(RetroSystem.NDS, RetroSystem.fromExtension(extension))
            assertEquals(RetroSystem.NDS, LibretroCoreSpec.forExtension(".$extension".uppercase())?.system)
        }
    }

    @Test
    fun ndsUsesDualScreenResolutionAndTouchDisplay() {
        val system = RetroSystem.NDS
        assertEquals(256, system.screenWidth)
        assertEquals(192, system.screenHeight)
        assertEquals(256, system.bottomScreenWidth)
        assertEquals(192, system.bottomScreenHeight)
        assertTrue(system.isDualScreen)
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
    @Test
    fun allNdsJoypadButtonsMapToLibretroIdsAndIgnoreNonDsControls() {
        val allDsButtons = EmulatorInputState(
            dpadUp = true,
            dpadDown = true,
            dpadLeft = true,
            dpadRight = true,
            buttonA = true,
            buttonB = true,
            buttonX = true,
            buttonY = true,
            triggerL = true,
            triggerR = true,
            start = true,
            select = true
        )
        assertEquals(0xFFF, toLibretroJoypadMask(allDsButtons))

        val nonDsControlsOnly = EmulatorInputState(
            triggerZ = true,
            stickX = 1f,
            stickY = -1f
        )
        assertEquals(0, toLibretroJoypadMask(nonDsControlsOnly))
    }

}
