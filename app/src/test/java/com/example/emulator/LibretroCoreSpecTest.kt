package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibretroCoreSpecTest {
    @Test
    fun n64CandidateUsesMupen64PlusNextAndRecognizesByteOrders() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.N64)
        assertNotNull(spec)
        assertEquals("mupen64plus_next_libretro_android.so", spec?.libraryFileName)
        assertEquals(
            setOf("n64", "z64", "v64"),
            spec?.extensions
        )
    }

    @Test
    fun gbaCandidateUsesMgbALibretroLibrary() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.GBA)
        assertNotNull(spec)
        assertEquals("mgba_libretro_android.so", spec?.libraryFileName)
        assertEquals(setOf("gba"), spec?.extensions)
    }
}
