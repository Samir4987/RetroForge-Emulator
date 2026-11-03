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
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> updateKeyDpad(input, keyCode, pressed)
            KeyEvent.KEYCODE_BUTTON_A -> { input.buttonA = pressed; true }
            KeyEvent.KEYCODE_BUTTON_B -> { input.buttonB = pressed; true }
            KeyEvent.KEYCODE_BUTTON_X -> { input.buttonX = pressed; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { input.buttonY = pressed; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { input.triggerL = pressed; true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { input.triggerR = pressed; true }
            KeyEvent.KEYCODE_BUTTON_L2 -> updateKeyTrigger(input, left = true, pressed = pressed)
            KeyEvent.KEYCODE_BUTTON_R2 -> updateKeyTrigger(input, left = false, pressed = pressed)
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
        // Android gamepads commonly expose the right stick as Z/RZ; some
        // joystick devices instead report RX/RY. Support both conventions.
        val rightStickXAxis = if (hasAxis(event, MotionEvent.AXIS_Z)) {
            MotionEvent.AXIS_Z
        } else {
            MotionEvent.AXIS_RX
        }
        val rightStickYAxis = if (hasAxis(event, MotionEvent.AXIS_RZ)) {
            MotionEvent.AXIS_RZ
        } else {
            MotionEvent.AXIS_RY
        }
        input.stick2X = normalizeAxis(event, rightStickXAxis)
        input.stick2Y = normalizeAxis(event, rightStickYAxis)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        updateHatDpad(
            input,
            hasX = hasAxis(event, MotionEvent.AXIS_HAT_X),
            x = hatX,
            hasY = hasAxis(event, MotionEvent.AXIS_HAT_Y),
            y = hatY
        )

        val leftTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rightTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        if (hasAxis(event, MotionEvent.AXIS_LTRIGGER) || hasAxis(event, MotionEvent.AXIS_BRAKE)) {
            updateAxisTrigger(input, left = true, pressed = leftTrigger > TRIGGER_THRESHOLD)
        }
        if (hasAxis(event, MotionEvent.AXIS_RTRIGGER) || hasAxis(event, MotionEvent.AXIS_GAS)) {
            updateAxisTrigger(input, left = false, pressed = rightTrigger > TRIGGER_THRESHOLD)
        }
        return true
    }

    /** Updates the key-driven D-pad source without disturbing joystick-hat directions. */
    internal fun updateKeyDpad(input: EmulatorInputState, keyCode: Int, pressed: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> input.keyDpadUp = pressed
            KeyEvent.KEYCODE_DPAD_DOWN -> input.keyDpadDown = pressed
            KeyEvent.KEYCODE_DPAD_LEFT -> input.keyDpadLeft = pressed
            KeyEvent.KEYCODE_DPAD_RIGHT -> input.keyDpadRight = pressed
            else -> return false
        }
        mergeDpadSources(input)
        return true
    }

    /** Updates only axes actually exposed by the device; key D-pad state is preserved. */
    internal fun updateHatDpad(
        input: EmulatorInputState,
        hasX: Boolean,
        x: Float,
        hasY: Boolean,
        y: Float
    ) {
        if (hasX) {
            input.hatDpadLeft = x < -0.5f
            input.hatDpadRight = x > 0.5f
        }
        if (hasY) {
            input.hatDpadUp = y < -0.5f
            input.hatDpadDown = y > 0.5f
        }
        mergeDpadSources(input)
    }

    /** Keeps digital shoulder buttons and analog trigger axes from cancelling each other. */
    internal fun updateKeyTrigger(input: EmulatorInputState, left: Boolean, pressed: Boolean): Boolean {
        if (left) input.keyTriggerZL = pressed else input.keyTriggerZR = pressed
        mergeTriggerSources(input)
        return true
    }

    internal fun updateAxisTrigger(input: EmulatorInputState, left: Boolean, pressed: Boolean) {
        if (left) input.axisTriggerZL = pressed else input.axisTriggerZR = pressed
        mergeTriggerSources(input)
    }

    private fun mergeTriggerSources(input: EmulatorInputState) {
        input.triggerZL = input.keyTriggerZL || input.axisTriggerZL
        input.triggerZR = input.keyTriggerZR || input.axisTriggerZR
    }

    private fun mergeDpadSources(input: EmulatorInputState) {
        input.dpadUp = input.keyDpadUp || input.hatDpadUp
        input.dpadDown = input.keyDpadDown || input.hatDpadDown
        input.dpadLeft = input.keyDpadLeft || input.hatDpadLeft
        input.dpadRight = input.keyDpadRight || input.hatDpadRight
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
