package com.example.emulator

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sign

/**
 * Maps Android gamepad events to the Nintendo 3DS control surface.
 *
 * Face buttons use Android's conventional A/B/X/Y names. The left stick is
 * the Circle Pad; the right stick is the New 3DS C-stick. L2/R2 map to ZL/ZR.
 */
object N3dsInputMapper {
    fun applyKeyEvent(input: EmulatorInputState, keyCode: Int, pressed: Boolean): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { input.dpadUp = pressed; true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { input.dpadDown = pressed; true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { input.dpadLeft = pressed; true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { input.dpadRight = pressed; true }
            KeyEvent.KEYCODE_BUTTON_A -> { input.buttonA = pressed; true }
            KeyEvent.KEYCODE_BUTTON_B -> { input.buttonB = pressed; true }
            KeyEvent.KEYCODE_BUTTON_X -> { input.buttonX = pressed; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { input.buttonY = pressed; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { input.triggerL = pressed; true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { input.triggerR = pressed; true }
            KeyEvent.KEYCODE_BUTTON_L2 -> { input.triggerZL = pressed; true }
            KeyEvent.KEYCODE_BUTTON_R2 -> { input.triggerZR = pressed; true }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { input.buttonL3 = pressed; true }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { input.buttonR3 = pressed; true }
            KeyEvent.KEYCODE_BUTTON_START -> { input.start = pressed; true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { input.select = pressed; true }
            else -> false
        }

    /** Applies a joystick motion event, including Circle Pad, C-stick, hat and triggers. */
    fun applyMotionEvent(input: EmulatorInputState, event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE ||
            (event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
        ) return false

        input.stickX = normalizeAxis(event, MotionEvent.AXIS_X)
        input.stickY = normalizeAxis(event, MotionEvent.AXIS_Y)
        input.stick2X = normalizeAxis(event, MotionEvent.AXIS_RX)
        input.stick2Y = normalizeAxis(event, MotionEvent.AXIS_RY)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hasAxis(event, MotionEvent.AXIS_HAT_X)) {
            input.dpadLeft = hatX < -0.5f
            input.dpadRight = hatX > 0.5f
        }
        if (hasAxis(event, MotionEvent.AXIS_HAT_Y)) {
            input.dpadUp = hatY < -0.5f
            input.dpadDown = hatY > 0.5f
        }

        val leftTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rightTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        if (hasAxis(event, MotionEvent.AXIS_LTRIGGER) || hasAxis(event, MotionEvent.AXIS_BRAKE)) {
            input.triggerZL = leftTrigger > TRIGGER_THRESHOLD
        }
        if (hasAxis(event, MotionEvent.AXIS_RTRIGGER) || hasAxis(event, MotionEvent.AXIS_GAS)) {
            input.triggerZR = rightTrigger > TRIGGER_THRESHOLD
        }
        return true
    }

    /** Radial dead-zone remapping for a single normalized Android joystick axis. */
    fun normalizeAxis(value: Float, deadZone: Float): Float {
        val clamped = value.coerceIn(-1f, 1f)
        val threshold = deadZone.coerceIn(0f, 0.95f)
        val magnitude = abs(clamped)
        if (magnitude <= threshold) return 0f
        return sign(clamped) * ((magnitude - threshold) / (1f - threshold))
    }

    private fun normalizeAxis(event: MotionEvent, axis: Int): Float {
        val deadZone = event.device?.getMotionRange(axis, event.source)?.flat ?: DEFAULT_DEAD_ZONE
        return normalizeAxis(event.getAxisValue(axis), deadZone)
    }

    private fun hasAxis(event: MotionEvent, axis: Int): Boolean =
        event.device?.getMotionRange(axis, event.source) != null

    private const val DEFAULT_DEAD_ZONE = 0.15f
    private const val TRIGGER_THRESHOLD = 0.5f
}
