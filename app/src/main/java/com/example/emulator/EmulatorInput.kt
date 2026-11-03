package com.example.emulator

data class EmulatorInputState(
    var dpadUp: Boolean = false,
    var dpadDown: Boolean = false,
    var dpadLeft: Boolean = false,
    var dpadRight: Boolean = false,
    var buttonA: Boolean = false,
    var buttonB: Boolean = false,
    var buttonX: Boolean = false,
    var buttonY: Boolean = false,
    var triggerL: Boolean = false,
    var triggerR: Boolean = false,
    var triggerZ: Boolean = false,
    // Nintendo 3DS New 3DS shoulder triggers and stick clicks.
    var triggerZL: Boolean = false,
    var triggerZR: Boolean = false,
    var buttonL3: Boolean = false,
    var buttonR3: Boolean = false,
    var start: Boolean = false,
    var select: Boolean = false,
    // N64 C-buttons
    var cUp: Boolean = false,
    var cDown: Boolean = false,
    var cLeft: Boolean = false,
    var cRight: Boolean = false,
    // Analog joystick (-1.0f to 1.0f)
    var stickX: Float = 0f,
    var stickY: Float = 0f,
    // New Nintendo 3DS C-stick (right analog stick).
    var stick2X: Float = 0f,
    var stick2Y: Float = 0f,
    // NDS Touch Screen
    var touchX: Float = 0f,
    var touchY: Float = 0f,
    var isTouching: Boolean = false
) {
    fun reset() {
        dpadUp = false
        dpadDown = false
        dpadLeft = false
        dpadRight = false
        buttonA = false
        buttonB = false
        buttonX = false
        buttonY = false
        triggerL = false
        triggerR = false
        triggerZ = false
        triggerZL = false
        triggerZR = false
        buttonL3 = false
        buttonR3 = false
        start = false
        select = false
        cUp = false
        cDown = false
        cLeft = false
        cRight = false
        stickX = 0f
        stickY = 0f
        stick2X = 0f
        stick2Y = 0f
        touchX = 0f
        touchY = 0f
        isTouching = false
    }
}
