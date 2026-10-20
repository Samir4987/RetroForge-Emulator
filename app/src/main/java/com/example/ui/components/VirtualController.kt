package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.ControllerSkin
import com.example.emulator.EmulatorInputState
import com.example.emulator.RetroSystem
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun VirtualController(
    skin: ControllerSkin,
    system: RetroSystem,
    inputState: EmulatorInputState,
    onRewindStateChange: (Boolean) -> Unit,
    onFastForwardToggle: () -> Unit,
    isFastForwardActive: Boolean,
    hapticsEnabled: Boolean = true,
    opacity: Float = 0.95f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun triggerHaptic() {
        if (hapticsEnabled && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(18)
                }
            } catch (_: Exception) {}
        }
    }

    Surface(
        modifier = modifier
            .testTag("virtual_controller_surface")
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = skin.bodyColor.copy(alpha = opacity)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row: Shoulder Bumpers (L / R) & Turbo / Rewind Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trigger L
                BumperButton(
                    label = if (system == RetroSystem.N64) "Z / L" else "L",
                    color = skin.bezelColor,
                    textColor = skin.textColor,
                    testTag = "button_trigger_l",
                    onPressChange = { isPressed ->
                        inputState.triggerL = isPressed
                        if (system == RetroSystem.N64) inputState.triggerZ = isPressed
                        if (isPressed) triggerHaptic()
                    }
                )

                // Center Utility Bar: Fast Forward and Rewind Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind Hold Button
                    var isRewindHeld by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .testTag("button_rewind")
                            .height(34.dp)
                            .shadow(2.dp, RoundedCornerShape(17.dp))
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (isRewindHeld) Color(0xFFFF0055) else Color(0xFF2A2B38))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isRewindHeld = true
                                        onRewindStateChange(true)
                                        triggerHaptic()
                                        tryAwaitRelease()
                                        isRewindHeld = false
                                        onRewindStateChange(false)
                                    }
                                )
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Hold Rewind",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REWIND",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Fast Forward Turbo Toggle Button
                    Surface(
                        modifier = Modifier
                            .testTag("button_fast_forward")
                            .height(34.dp)
                            .shadow(2.dp, RoundedCornerShape(17.dp))
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (isFastForwardActive) Color(0xFF00C853) else Color(0xFF2A2B38))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        onFastForwardToggle()
                                        triggerHaptic()
                                    }
                                )
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Fast Forward",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TURBO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Trigger R
                BumperButton(
                    label = "R",
                    color = skin.bezelColor,
                    textColor = skin.textColor,
                    testTag = "button_trigger_r",
                    onPressChange = { isPressed ->
                        inputState.triggerR = isPressed
                        if (isPressed) triggerHaptic()
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Controls Layout: Left (D-pad / Joystick) vs Right (Action Buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: D-Pad or Analog Joystick for N64
                if (system == RetroSystem.N64) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VirtualAnalogStick(
                            skin = skin,
                            onStickMove = { sx, sy ->
                                inputState.stickX = sx
                                inputState.stickY = sy
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "3D STICK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = skin.textColor.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    VirtualDPad(
                        skin = skin,
                        inputState = inputState,
                        onHaptic = { triggerHaptic() }
                    )
                }

                // Center Lower: Start and Select Buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PillButton(
                            label = "SELECT",
                            skin = skin,
                            testTag = "button_select",
                            onPressChange = {
                                inputState.select = it
                                if (it) triggerHaptic()
                            }
                        )
                        PillButton(
                            label = "START",
                            skin = skin,
                            testTag = "button_start",
                            onPressChange = {
                                inputState.start = it
                                if (it) triggerHaptic()
                            }
                        )
                    }
                }

                // Right Side: Action Buttons according to system
                when (system) {
                    RetroSystem.GB, RetroSystem.GBC -> {
                        // Slanted A/B buttons for Game Boy
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .offset(y = 8.dp)
                        ) {
                            RoundActionButton(
                                label = "B",
                                color = skin.buttonBColor,
                                textColor = Color.White,
                                sizeDp = 52,
                                testTag = "button_b",
                                modifier = Modifier.offset(y = 16.dp),
                                onPressChange = {
                                    inputState.buttonB = it
                                    if (it) triggerHaptic()
                                }
                            )
                            RoundActionButton(
                                label = "A",
                                color = skin.buttonAColor,
                                textColor = Color.White,
                                sizeDp = 52,
                                testTag = "button_a",
                                onPressChange = {
                                    inputState.buttonA = it
                                    if (it) triggerHaptic()
                                }
                            )
                        }
                    }
                    RetroSystem.GBA, RetroSystem.NDS -> {
                        // Diamond ABXY layout
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // X (Top)
                            RoundActionButton(
                                label = "X",
                                color = skin.buttonXColor,
                                textColor = Color.White,
                                sizeDp = 44,
                                testTag = "button_x",
                                modifier = Modifier.align(Alignment.TopCenter),
                                onPressChange = {
                                    inputState.buttonX = it
                                    if (it) triggerHaptic()
                                }
                            )
                            // Y (Left)
                            RoundActionButton(
                                label = "Y",
                                color = skin.buttonYColor,
                                textColor = Color.White,
                                sizeDp = 44,
                                testTag = "button_y",
                                modifier = Modifier.align(Alignment.CenterStart),
                                onPressChange = {
                                    inputState.buttonY = it
                                    if (it) triggerHaptic()
                                }
                            )
                            // A (Right)
                            RoundActionButton(
                                label = "A",
                                color = skin.buttonAColor,
                                textColor = Color.White,
                                sizeDp = 44,
                                testTag = "button_a",
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onPressChange = {
                                    inputState.buttonA = it
                                    if (it) triggerHaptic()
                                }
                            )
                            // B (Bottom)
                            RoundActionButton(
                                label = "B",
                                color = skin.buttonBColor,
                                textColor = Color.White,
                                sizeDp = 44,
                                testTag = "button_b",
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onPressChange = {
                                    inputState.buttonB = it
                                    if (it) triggerHaptic()
                                }
                            )
                        }
                    }
                    RetroSystem.N64 -> {
                        // N64 Layout: Blue A, Red B, and Yellow C-cluster
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // C-Buttons Cluster (4 yellow buttons)
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .background(Color(0xFF282933), CircleShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                RoundActionButton(
                                    label = "▲",
                                    color = skin.accentColor,
                                    textColor = Color.Black,
                                    sizeDp = 26,
                                    testTag = "button_c_up",
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    onPressChange = {
                                        inputState.cUp = it
                                        if (it) triggerHaptic()
                                    }
                                )
                                RoundActionButton(
                                    label = "◀",
                                    color = skin.accentColor,
                                    textColor = Color.Black,
                                    sizeDp = 26,
                                    testTag = "button_c_left",
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    onPressChange = {
                                        inputState.cLeft = it
                                        if (it) triggerHaptic()
                                    }
                                )
                                RoundActionButton(
                                    label = "▶",
                                    color = skin.accentColor,
                                    textColor = Color.Black,
                                    sizeDp = 26,
                                    testTag = "button_c_right",
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    onPressChange = {
                                        inputState.cRight = it
                                        if (it) triggerHaptic()
                                    }
                                )
                                RoundActionButton(
                                    label = "▼",
                                    color = skin.accentColor,
                                    textColor = Color.Black,
                                    sizeDp = 26,
                                    testTag = "button_c_down",
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    onPressChange = {
                                        inputState.cDown = it
                                        if (it) triggerHaptic()
                                    }
                                )
                            }

                            // Blue A and Red B Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RoundActionButton(
                                    label = "B",
                                    color = skin.buttonBColor,
                                    textColor = Color.White,
                                    sizeDp = 44,
                                    testTag = "button_b",
                                    onPressChange = {
                                        inputState.buttonB = it
                                        if (it) triggerHaptic()
                                    }
                                )
                                RoundActionButton(
                                    label = "A",
                                    color = skin.buttonAColor,
                                    textColor = Color.White,
                                    sizeDp = 46,
                                    testTag = "button_a",
                                    onPressChange = {
                                        inputState.buttonA = it
                                        if (it) triggerHaptic()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualDPad(
    skin: ControllerSkin,
    inputState: EmulatorInputState,
    onHaptic: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(136.dp)
            .testTag("dpad_controller")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = hypot(dx, dy)
                        if (dist > 14f && dist < cx) {
                            val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                            // 8-way directional parsing
                            inputState.dpadRight = angle in 337.5..360.0 || angle in 0.0..22.5 || angle in 22.5..67.5 || angle in 292.5..337.5
                            inputState.dpadDown = angle in 22.5..157.5
                            inputState.dpadLeft = angle in 112.5..247.5
                            inputState.dpadUp = angle in 202.5..337.5
                            onHaptic()
                        }
                        tryAwaitRelease()
                        inputState.dpadUp = false
                        inputState.dpadDown = false
                        inputState.dpadLeft = false
                        inputState.dpadRight = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = hypot(dx, dy)
                        if (dist > 14f) {
                            val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                            inputState.dpadRight = angle in 337.5..360.0 || angle in 0.0..22.5 || angle in 22.5..67.5 || angle in 292.5..337.5
                            inputState.dpadDown = angle in 22.5..157.5
                            inputState.dpadLeft = angle in 112.5..247.5
                            inputState.dpadUp = angle in 202.5..337.5
                            onHaptic()
                        }
                    },
                    onDragEnd = {
                        inputState.dpadUp = false
                        inputState.dpadDown = false
                        inputState.dpadLeft = false
                        inputState.dpadRight = false
                    },
                    onDragCancel = {
                        inputState.dpadUp = false
                        inputState.dpadDown = false
                        inputState.dpadLeft = false
                        inputState.dpadRight = false
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val dist = hypot(dx, dy)
                        if (dist > 14f) {
                            val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                            inputState.dpadRight = angle in 337.5..360.0 || angle in 0.0..22.5 || angle in 22.5..67.5 || angle in 292.5..337.5
                            inputState.dpadDown = angle in 22.5..157.5
                            inputState.dpadLeft = angle in 112.5..247.5
                            inputState.dpadUp = angle in 202.5..337.5
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Cross background
        Canvas(modifier = Modifier.size(136.dp)) {
            val armWidth = 44.dp.toPx()
            val totalSize = 136.dp.toPx()
            val inset = (totalSize - armWidth) / 2f

            // Horizontal arm
            drawRoundRect(
                color = skin.dpadColor,
                topLeft = Offset(0f, inset),
                size = androidx.compose.ui.geometry.Size(totalSize, armWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            // Vertical arm
            drawRoundRect(
                color = skin.dpadColor,
                topLeft = Offset(inset, 0f),
                size = androidx.compose.ui.geometry.Size(armWidth, totalSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )

            // Center pivot concave indent
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = 16.dp.toPx(),
                center = center
            )
        }

        // Direction Arrows
        Text("▲", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        Text("▼", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        Text("◀", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        Text("▶", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
    }
}

@Composable
private fun VirtualAnalogStick(
    skin: ControllerSkin,
    onStickMove: (Float, Float) -> Unit
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadius = 40f

    Box(
        modifier = Modifier
            .size(120.dp)
            .testTag("analog_joystick")
            .background(Color(0xFF22232B), CircleShape)
            .border(2.dp, skin.bezelColor, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var dx = offset.x - cx
                        var dy = offset.y - cy
                        val dist = hypot(dx, dy)
                        if (dist > maxRadius) {
                            dx = (dx / dist) * maxRadius
                            dy = (dy / dist) * maxRadius
                        }
                        thumbOffset = Offset(dx, dy)
                        onStickMove(dx / maxRadius, dy / maxRadius)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onStickMove(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onStickMove(0f, 0f)
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var dx = change.position.x - cx
                        var dy = change.position.y - cy
                        val dist = hypot(dx, dy)
                        if (dist > maxRadius) {
                            dx = (dx / dist) * maxRadius
                            dy = (dy / dist) * maxRadius
                        }
                        thumbOffset = Offset(dx, dy)
                        onStickMove(dx / maxRadius, dy / maxRadius)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Analog Base Well
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFF16171D), CircleShape)
        )

        // Floating Thumbstick cap
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(54.dp)
                .shadow(6.dp, CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF888B9E), Color(0xFF4A4C59))
                    ),
                    CircleShape
                )
                .border(2.dp, Color(0xFF6F7285), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Textured concentric rings on thumbstick
            Canvas(modifier = Modifier.size(34.dp)) {
                drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = size.minDimension / 2f)
                drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = size.minDimension / 3.5f)
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    label: String,
    color: Color,
    textColor: Color,
    sizeDp: Int,
    testTag: String,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .testTag(testTag)
            .size(sizeDp.dp)
            .shadow(if (isPressed) 1.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (isPressed) color.copy(alpha = 0.85f) else color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressChange(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChange(false)
                    }
                )
            },
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = textColor,
                fontSize = (sizeDp * 0.36f).sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun BumperButton(
    label: String,
    color: Color,
    textColor: Color,
    testTag: String,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .testTag(testTag)
            .size(width = 72.dp, height = 32.dp)
            .shadow(if (isPressed) 1.dp else 3.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) color.copy(alpha = 0.8f) else color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressChange(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChange(false)
                    }
                )
            },
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    skin: ControllerSkin,
    testTag: String,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(2.dp)
    ) {
        Surface(
            modifier = Modifier
                .testTag(testTag)
                .size(width = 44.dp, height = 14.dp)
                .rotate(if (skin == ControllerSkin.CLASSIC_DMG) -25f else 0f)
                .shadow(if (isPressed) 1.dp else 2.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(if (isPressed) Color(0xFF1E202B) else Color(0xFF424557))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onPressChange(true)
                            tryAwaitRelease()
                            isPressed = false
                            onPressChange(false)
                        }
                    )
                },
            color = Color.Transparent
        ) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = skin.textColor.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace
        )
    }
}
