package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroCoreSpecTest {
    @Test
    fun azaharIsRegisteredAsNintendo3dsCandidate() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.N3DS)

        assertNotNull(spec)
        assertEquals("azahar_libretro.so", spec!!.libraryFileName)
        assertEquals("https://github.com/azahar-emu/azahar", spec.upstreamUrl)
        assertEquals(setOf("3ds", "cci", "cxi"), spec.extensions)
    }

    @Test
    fun nintendo3dsExtensionsResolveToAzahar() {
        listOf("3ds", ".3DS", "cci", "cxi").forEach { extension ->
            assertEquals(RetroSystem.N3DS, LibretroCoreSpec.forExtension(extension)?.system)
        }
    }

    @Test
    fun nintendo3dsIsMarkedAsDualScreenAndAnalogInputSystem() {
        assertTrue(RetroSystem.N3DS.isDualScreen)
        assertTrue(RetroSystem.N3DS.hasAnalogStick)
    }
}
