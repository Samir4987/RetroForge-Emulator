package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RetroGame
import com.example.data.model.SaveStateDto
import com.example.data.repository.SaveStateRepository
import com.example.emulator.ControllerSkin
import com.example.emulator.EmulatorCore
import com.example.emulator.LibretroCoreSpec
import com.example.emulator.LibretroRuntime
import com.example.emulator.RetroSystem
import com.example.ui.components.RetroScreenView
import com.example.ui.components.SaveSlotDialog
import com.example.ui.components.VirtualController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.data.local.RomLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun EmulatorScreen(
    game: RetroGame,
    emulatorCore: EmulatorCore,
    saveStateRepository: SaveStateRepository?,
    romLibraryRepository: RomLibraryRepository? = null,
    initialSkin: ControllerSkin = ControllerSkin.CLASSIC_DMG,
    onBackToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeSkin by remember { mutableStateOf(initialSkin) }
    var enableScanlines by remember { mutableStateOf(true) }
    var showSlotDialog by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSkinMenu by remember { mutableStateOf(false) }

    val saveStates = remember { mutableStateListOf<SaveStateDto>() }
    var libretroBitmap by remember(game.id) { mutableStateOf<Bitmap?>(null) }
    var isUsingLibretro by remember(game.id) { mutableStateOf(false) }

    // Intercept back button to return safely to Library
    BackHandler {
        emulatorCore.isPaused = true
        coroutineScope.launch {
            romLibraryRepository?.recordPlayStats(game.id, emulatorCore.playTimeSeconds)
        }
        onBackToLibrary()
    }

    // Load game into core on launch
    LaunchedEffect(game) {
        emulatorCore.loadGame(game)
        // Load cloud saves if logged in
        if (Firebase.auth.currentUser != null && saveStateRepository != null) {
            try {
                val existing = saveStateRepository.observeSaveStates(game.id).firstOrNull()
                if (existing != null) {
                    saveStates.clear()
                    saveStates.addAll(existing)
                }
            } catch (_: Exception) {}
        }
    }

    // Prefer the real GBA core only when both a ROM and a packaged core are available.
    // Otherwise the existing demo/simulation remains the fallback.
    var frameTicker by remember { mutableStateOf(0L) }
    LaunchedEffect(game) {
        var runtime: LibretroRuntime? = null
        var temporaryRom: File? = null
        try {
            val romBytes = game.romBytes
            val coreSpec = LibretroCoreSpec.forSystem(RetroSystem.GBA)
            if (game.system.equals("GBA", ignoreCase = true) &&
                romBytes != null && romBytes.isNotEmpty() && coreSpec != null
            ) {
                val frontend = File(context.applicationInfo.nativeLibraryDir, "libretroforge_libretro.so")
                val candidate = LibretroRuntime(frontend, coreSpec)
                if (candidate.isAvailable) {
                    val romFile = File.createTempFile("retroforge-gba-", ".gba", context.cacheDir)
                    temporaryRom = romFile
                    romFile.writeBytes(romBytes)
                    if (candidate.loadRom(romFile).isSuccess) {
                        runtime = candidate
                        isUsingLibretro = true
                    } else {
                        candidate.close()
                        romFile.delete()
                        temporaryRom = null
                    }
                }
            }

            while (isActive) {
                val activeRuntime = runtime
                if (!emulatorCore.isPaused) {
                    if (activeRuntime != null) {
                        // A core frame can do substantial native work. Keep it off the
                        // Compose/UI thread, then publish the resulting Bitmap on main.
                        val inputSnapshot = emulatorCore.inputState.copy()
                        val frame = withContext(Dispatchers.Default) {
                            activeRuntime.updateInput(inputSnapshot)
                            val result = activeRuntime.runFrame()
                            result to if (result.isSuccess) activeRuntime.readVideoFrame() else null
                        }
                        if (frame.first.isSuccess) {
                            frame.second?.let { libretroBitmap = it }
                        } else {
                            activeRuntime.close()
                            runtime = null
                            isUsingLibretro = false
                            libretroBitmap = null
                            emulatorCore.tick()
                        }
                    } else {
                        emulatorCore.tick()
                    }
                }
                frameTicker = emulatorCore.frameCounter
                // 60 FPS standard timing adjusted for fast forward.
                val frameDelay = (16L / emulatorCore.fastForwardMultiplier.toLong().coerceAtLeast(1L)).coerceAtLeast(4L)
                delay(frameDelay)
            }
        } finally {
            runtime?.close()
            temporaryRom?.delete()
            isUsingLibretro = false
            libretroBitmap = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C14))
            .testTag("emulator_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emulator_hud_bar"),
                color = Color(0xFF141724),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button & Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                emulatorCore.isPaused = true
                                onBackToLibrary()
                            },
                            modifier = Modifier.testTag("button_back_to_library")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Library",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = game.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${game.system} • ${if (isUsingLibretro) "REAL CORE" else "${emulatorCore.playTimeSeconds}s"}",
                                fontSize = 10.sp,
                                color = Color(0xFF00E5FF),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Action Controls: Speed, Pause, Save Slots, Skin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Fast Forward Speed Selector
                        Box {
                            IconButton(
                                onClick = { showSpeedMenu = true },
                                modifier = Modifier.testTag("button_speed_menu")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (speedMultiplier > 1f) Color(0xFF00C853) else Color(0xFF25293C)
                                ) {
                                    Text(
                                        text = "${speedMultiplier}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(1.0f, 1.5f, 2.0f, 4.0f, 8.0f).forEach { spd ->
                                    DropdownMenuItem(
                                        text = { Text("${spd}x Speed") },
                                        onClick = {
                                            speedMultiplier = spd
                                            emulatorCore.fastForwardMultiplier = spd
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Pause Toggle
                        IconButton(
                            onClick = { emulatorCore.isPaused = !emulatorCore.isPaused },
                            modifier = Modifier.testTag("button_pause_toggle")
                        ) {
                            Icon(
                                imageVector = if (emulatorCore.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause / Resume",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Demo save states currently serialize the simulation engine only.
                        if (!isUsingLibretro) {
                            // Quick Save Slot 0 Button
                            IconButton(
                                onClick = {
                                    saveStateToSlot(
                                        slot = 0,
                                        game = game,
                                        emulatorCore = emulatorCore,
                                        saveStateRepository = saveStateRepository,
                                        saveStates = saveStates,
                                        onSaved = {
                                            Toast.makeText(context, "Quick Saved to Slot 1", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("button_quick_save")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Quick Save",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Save Slots Modal Button
                            IconButton(
                                onClick = { showSlotDialog = true },
                                modifier = Modifier.testTag("button_open_slots")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewList,
                                    contentDescription = "Save Slots",
                                    tint = Color(0xFF29B6F6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // CRT Filter Toggle
                        IconButton(
                            onClick = { enableScanlines = !enableScanlines },
                            modifier = Modifier.testTag("button_toggle_crt")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Toggle CRT Scanlines",
                                tint = if (enableScanlines) Color(0xFF00E5FF) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Controller Skin Selector
                        Box {
                            IconButton(
                                onClick = { showSkinMenu = true },
                                modifier = Modifier.testTag("button_skin_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Switch Skin",
                                    tint = activeSkin.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSkinMenu,
                                onDismissRequest = { showSkinMenu = false }
                            ) {
                                ControllerSkin.entries.forEach { skin ->
                                    DropdownMenuItem(
                                        text = { Text(skin.displayName) },
                                        onClick = {
                                            activeSkin = skin
                                            showSkinMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Game Display View
            RetroScreenView(
                system = emulatorCore.currentSystem,
                screenBitmap = libretroBitmap ?: emulatorCore.screenBitmap,
                bottomScreenBitmap = emulatorCore.bottomScreenBitmap,
                isRewinding = emulatorCore.isRewinding,
                fastForwardMultiplier = emulatorCore.fastForwardMultiplier,
                isPaused = emulatorCore.isPaused,
                enableScanlines = enableScanlines,
                onTouchInput = { nx, ny, touching ->
                    emulatorCore.inputState.touchX = nx
                    emulatorCore.inputState.touchY = ny
                    emulatorCore.inputState.isTouching = touching
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // On-Screen Virtual Controller
            VirtualController(
                skin = activeSkin,
                system = emulatorCore.currentSystem,
                inputState = emulatorCore.inputState,
                onRewindStateChange = { isRewind ->
                    emulatorCore.isRewinding = isRewind
                },
                onFastForwardToggle = {
                    if (emulatorCore.fastForwardMultiplier > 1f) {
                        emulatorCore.fastForwardMultiplier = 1f
                        speedMultiplier = 1f
                    } else {
                        emulatorCore.fastForwardMultiplier = 2f
                        speedMultiplier = 2f
                    }
                },
                isFastForwardActive = emulatorCore.fastForwardMultiplier > 1f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Save State Slots Dialog
        if (showSlotDialog && !isUsingLibretro) {
            SaveSlotDialog(
                gameTitle = game.title,
                systemName = game.system,
                existingSaves = saveStates,
                onSaveToSlot = { slot ->
                    saveStateToSlot(
                        slot = slot,
                        game = game,
                        emulatorCore = emulatorCore,
                        saveStateRepository = saveStateRepository,
                        saveStates = saveStates,
                        onSaved = {
                            Toast.makeText(context, "State saved to Slot ${slot + 1}", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onLoadFromSlot = { dto ->
                    val success = emulatorCore.loadSaveStateBase64(dto.stateData)
                    if (success) {
                        Toast.makeText(context, "Loaded Slot ${dto.slot + 1}", Toast.LENGTH_SHORT).show()
                        showSlotDialog = false
                    } else {
                        Toast.makeText(context, "Failed to load state", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteSlot = { saveId ->
                    coroutineScope.launch {
                        saveStateRepository?.deleteSaveState(saveId)
                        saveStates.removeAll { it.id == saveId }
                    }
                },
                onDismiss = { showSlotDialog = false }
            )
        }
    }
}

private fun saveStateToSlot(
    slot: Long,
    game: RetroGame,
    emulatorCore: EmulatorCore,
    saveStateRepository: SaveStateRepository?,
    saveStates: MutableList<SaveStateDto>,
    onSaved: () -> Unit
) {
    val stateBase64 = emulatorCore.exportSaveStateBase64()
    val thumbnail = emulatorCore.generateThumbnailBase64()
    val uid = Firebase.auth.currentUser?.uid ?: "local_user"
    val docId = "${game.id}_slot_$slot"

    val newSave = SaveStateDto(
        id = docId,
        userId = uid,
        gameId = game.id,
        gameTitle = game.title,
        system = game.system,
        slot = slot,
        stateData = stateBase64,
        thumbnailBase64 = thumbnail,
        playTimeSeconds = emulatorCore.playTimeSeconds
    )

    // Update in-memory state
    val existingIndex = saveStates.indexOfFirst { it.slot == slot }
    if (existingIndex >= 0) {
        saveStates[existingIndex] = newSave
    } else {
        saveStates.add(newSave)
    }

    // Persist to Cloud Firestore if signed in
    if (Firebase.auth.currentUser != null && saveStateRepository != null) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            saveStateRepository.saveState(newSave)
        }
    }
    onSaved()
}
