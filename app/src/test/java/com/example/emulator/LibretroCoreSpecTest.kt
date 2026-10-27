package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibretroCoreSpecTest {
    @Test
    fun gbaCandidateUsesMgbALibretroLibrary() {
        val spec = LibretroCoreSpec.forSystem(RetroSystem.GBA)
        assertNotNull(spec)
        assertEquals("mgba_libretro_android.so", spec?.libraryFileName)
        assertEquals(setOf("gba"), spec?.extensions)
    }
}
