package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroSystemN3dsTest {
    @Test
    fun n3dsUsesIndependentTopAndBottomDisplayDimensions() {
        val system = RetroSystem.N3DS

        assertEquals(400, system.screenWidth)
        assertEquals(240, system.screenHeight)
        assertEquals(320, system.bottomScreenWidth)
        assertEquals(240, system.bottomScreenHeight)
        assertTrue(system.isDualScreen)
        assertTrue(system.hasAnalogStick)
    }

    @Test
    fun n3dsRomExtensionsResolveToN3ds() {
        assertEquals(RetroSystem.N3DS, RetroSystem.fromExtension("3ds"))
        assertEquals(RetroSystem.N3DS, RetroSystem.fromExtension(".cci"))
        assertEquals(RetroSystem.N3DS, RetroSystem.fromExtension("CXI"))
        assertEquals(RetroSystem.N3DS, RetroSystem.fromCode("n3ds"))
    }
}
