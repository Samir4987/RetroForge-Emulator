package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.RetroSystem

@Composable
fun RetroScreenView(
    system: RetroSystem,
    screenBitmap: Bitmap,
    bottomScreenBitmap: Bitmap? = null,
    isRewinding: Boolean = false,
    fastForwardMultiplier: Float = 1.0f,
    isPaused: Boolean = false,
    enableScanlines: Boolean = true,
    frameVersion: Long = 0L,
    onTouchInput: ((x: Float, y: Float, isTouching: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("retro_screen_container")
            .clip(RoundedCornerShape(12.dp)),
        color = Color(0xFF0F111A),
        shadowElevation = 8.dp
    ) {
        if (system.isDualScreen && bottomScreenBitmap != null) {
            // NDS Dual Screen Layout (Top display + Bottom touch display)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(system.defaultAspect)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    ScreenCanvas(
                        bitmap = screenBitmap,
                        enableScanlines = enableScanlines,
                        frameVersion = frameVersion
                    )
                    ScreenHudOverlay(
                        isRewinding = isRewinding,
                        fastForwardMultiplier = fastForwardMultiplier,
                        isPaused = isPaused
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                // DS Hinge Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(4.dp)
                        .background(Color(0xFF2C2F3E), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Touch Screen. The 3DS bottom panel is 320x240; its top panel is 400x240.
                var touchPos by remember { mutableStateOf<Offset?>(null) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(if (system == RetroSystem.N3DS) 4f / 3f else system.defaultAspect)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                    onTouchInput?.invoke(nx, ny, true)
                                    tryAwaitRelease()
                                    onTouchInput?.invoke(nx, ny, false)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                    onTouchInput?.invoke(nx, ny, true)
                                },
                                onDragEnd = {
                                    onTouchInput?.invoke(0f, 0f, false)
                                },
                                onDragCancel = {
                                    onTouchInput?.invoke(0f, 0f, false)
                                },
                                onDrag = { change, _ ->
                                    val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                    onTouchInput?.invoke(nx, ny, true)
                                }
                            )
                        }
                ) {
                    ScreenCanvas(
                        bitmap = bottomScreenBitmap,
                        enableScanlines = enableScanlines,
                        frameVersion = frameVersion
                    )
                    Text(
                        text = "TOUCH SCREEN",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    )
                }
            }
        } else {
            // Single Screen Display (GB, GBC, GBA, N64)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .aspectRatio(system.defaultAspect)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                ScreenCanvas(
                    bitmap = screenBitmap,
                    enableScanlines = enableScanlines,
                    frameVersion = frameVersion
                )
                ScreenHudOverlay(
                    isRewinding = isRewinding,
                    fastForwardMultiplier = fastForwardMultiplier,
                    isPaused = isPaused
                )
            }
        }
    }
}

@Composable
private fun ScreenCanvas(
    bitmap: Bitmap,
    enableScanlines: Boolean,
    frameVersion: Long
) {
    val imageBitmap = remember(bitmap, frameVersion) { bitmap.asImageBitmap() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (enableScanlines) {
                    drawCrtScanlines(size)
                }
            }
    ) {
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}

private fun DrawScope.drawCrtScanlines(size: Size) {
    val lineSpacing = 4f
    var y = 0f
    val scanlineColor = Color.Black.copy(alpha = 0.16f)
    while (y < size.height) {
        drawLine(
            color = scanlineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5f
        )
        y += lineSpacing
    }
}

@Composable
private fun ScreenHudOverlay(
    isRewinding: Boolean,
    fastForwardMultiplier: Float,
    isPaused: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isRewinding) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = Color(0xCCFF0055),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "◀◀ REWINDING",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else if (fastForwardMultiplier > 1.0f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = Color(0xCC00C853),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "▶▶ FAST ${fastForwardMultiplier}x",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (isPaused) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xD010121E),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "❚❚ PAUSED",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
