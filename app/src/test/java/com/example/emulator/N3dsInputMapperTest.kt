package com.example.emulator

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class N3dsInputMapperTest {
    @Test
    fun mapsFaceButtonsDpadAndShouldersAndReleasesThem() {
        val input = EmulatorInputState()

        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_A, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_B, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_DPAD_UP, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_L1, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_R1, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_L2, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_R2, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_START, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_SELECT, true))

        assertTrue(input.buttonA)
        assertTrue(input.buttonB)
        assertTrue(input.dpadUp)
        assertTrue(input.triggerL)
        assertTrue(input.triggerR)
        assertTrue(input.triggerZL)
        assertTrue(input.triggerZR)
        assertTrue(input.start)
        assertTrue(input.select)

        N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_A, false)
        N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_L2, false)
        assertFalse(input.buttonA)
        assertFalse(input.triggerZL)
    }

    @Test
    fun mapsNewNintendo3dsStickClicks() {
        val input = EmulatorInputState()

        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_THUMBL, true))
        assertTrue(N3dsInputMapper.applyKeyEvent(input, KeyEvent.KEYCODE_BUTTON_THUMBR, true))

        assertTrue(input.buttonL3)
        assertTrue(input.buttonR3)
    }


    @Test
    fun hatReleaseDoesNotCancelHeldKeyDpadAndKeyReleaseDoesNotCancelHat() {
        val input = EmulatorInputState()

        assertTrue(N3dsInputMapper.updateKeyDpad(input, KeyEvent.KEYCODE_DPAD_UP, true))
        N3dsInputMapper.updateHatDpad(input, hasX = false, x = 0f, hasY = true, y = -1f)
        N3dsInputMapper.updateHatDpad(input, hasX = false, x = 0f, hasY = true, y = 0f)
        assertTrue("A neutral hat must not release a key still held", input.dpadUp)

        N3dsInputMapper.updateKeyDpad(input, KeyEvent.KEYCODE_DPAD_UP, false)
        assertFalse(input.dpadUp)

        N3dsInputMapper.updateHatDpad(input, hasX = true, x = 1f, hasY = false, y = 0f)
        N3dsInputMapper.updateKeyDpad(input, KeyEvent.KEYCODE_DPAD_RIGHT, true)
        N3dsInputMapper.updateHatDpad(input, hasX = true, x = 0f, hasY = false, y = 0f)
        assertTrue("Releasing the hat must preserve the key direction", input.dpadRight)

        input.reset()
        assertFalse(input.dpadRight)
        assertFalse(input.keyDpadRight)
        assertFalse(input.hatDpadRight)
    }

    @Test
    fun normalizesStickAxesWithDeadZoneAndClampsOutOfRangeValues() {
        assertEquals(0f, N3dsInputMapper.normalizeAxis(0.1f, 0.2f), 0.0001f)
        assertEquals(0.5f, N3dsInputMapper.normalizeAxis(0.6f, 0.2f), 0.0001f)
        assertEquals(-1f, N3dsInputMapper.normalizeAxis(-1.2f, 0.2f), 0.0001f)
        assertEquals(1f, N3dsInputMapper.normalizeAxis(1.2f, 0f), 0.0001f)
    }

    @Test
    fun ignoresNonGamepadKeys() {
        assertFalse(N3dsInputMapper.applyKeyEvent(EmulatorInputState(), KeyEvent.KEYCODE_BACK, true))
    }
}
