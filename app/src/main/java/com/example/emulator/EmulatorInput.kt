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
        start = false
        select = false
        cUp = false
        cDown = false
        cLeft = false
        cRight = false
        stickX = 0f
        stickY = 0f
        isTouching = false
    }
}
