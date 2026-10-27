package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class LibretroInputMappingTest {
    @Test
    fun emptyInputProducesNoPressedButtons() {
        assertEquals(0, EmulatorInputState().toLibretroJoypadMask())
    }

    @Test
    fun mapsStandardGbaJoypadIds() {
        val input = EmulatorInputState(
            dpadUp = true,
            dpadRight = true,
            buttonA = true,
            buttonB = true,
            triggerL = true,
            triggerR = true,
            start = true,
            select = true
        )

        val expected = (1 shl 0) or // B
            (1 shl 2) or // Select
            (1 shl 3) or // Start
            (1 shl 4) or // Up
            (1 shl 7) or // Right
            (1 shl 8) or // A
            (1 shl 10) or // L
            (1 shl 11) // R

        assertEquals(expected, input.toLibretroJoypadMask())
    }

    @Test
    fun releasedButtonsAreNotKeptInTheMask() {
        val input = EmulatorInputState(buttonA = true, dpadLeft = true)
        assertEquals((1 shl 8) or (1 shl 6), input.toLibretroJoypadMask())

        input.buttonA = false
        input.dpadLeft = false
        assertEquals(0, input.toLibretroJoypadMask())
    }
}
