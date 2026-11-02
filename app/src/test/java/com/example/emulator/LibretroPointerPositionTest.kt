package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibretroPointerPositionTest {
    @Test
    fun mapsNormalizedCornersToLibretroSignedRange() {
        assertEquals(LibretroPointerPosition(-32768, -32768), LibretroPointerPosition.fromNormalized(0f, 0f))
        assertEquals(LibretroPointerPosition(32767, 32767), LibretroPointerPosition.fromNormalized(1f, 1f))
        assertEquals(LibretroPointerPosition(0, 0), LibretroPointerPosition.fromNormalized(0.5f, 0.5f))
    }

    @Test
    fun clampsCoordinatesOutsideScreenBounds() {
        assertEquals(LibretroPointerPosition(-32768, 32767), LibretroPointerPosition.fromNormalized(-2f, 3f))
    }

    @Test
    fun rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            LibretroPointerPosition.fromNormalized(Float.NaN, 0.5f)
        }
    }

    @Test
    fun joypadIdsMatchLibretroStandard() {
        assertEquals(0, LibretroJoypadButton.B.id)
        assertEquals(2, LibretroJoypadButton.SELECT.id)
        assertEquals(3, LibretroJoypadButton.START.id)
        assertEquals(4, LibretroJoypadButton.UP.id)
        assertEquals(7, LibretroJoypadButton.RIGHT.id)
        assertEquals(8, LibretroJoypadButton.A.id)
        assertEquals(10, LibretroJoypadButton.L.id)
        assertEquals(11, LibretroJoypadButton.R.id)
    }
}
