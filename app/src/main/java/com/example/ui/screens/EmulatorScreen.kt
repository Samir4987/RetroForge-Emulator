package com.example.ui.screens

import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RetroGame
import com.example.data.model.SaveStateDto
import com.example.data.repository.SaveStateRepository
import com.example.emulator.ControllerSkin
import com.example.emulator.EmulatorCore
import com.example.emulator.FrameStepAccumulator
import com.example.emulator.LibretroCoreSpec
import com.example.emulator.LibretroRuntime
import com.example.emulator.NdsSaveStateStore
import com.example.emulator.RetroSystem
import com.example.ui.components.RetroScreenView
import com.example.ui.components.SaveSlotDialog
import com.example.ui.components.VirtualController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.Timestamp
import java.util.Date
import com.example.data.local.RomLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val gamepadFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var activeSkin by remember { mutableStateOf(initialSkin) }
    var enableScanlines by remember { mutableStateOf(true) }
    var showSlotDialog by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSkinMenu by remember { mutableStateOf(false) }

    val saveStates = remember { mutableStateListOf<SaveStateDto>() }
    val isNdsGame = RetroSystem.fromCode(game.system) == RetroSystem.NDS
    val ndsSaveStore = remember(context) { NdsSaveStateStore(context.filesDir) }
    var ndsRuntime by remember { mutableStateOf<LibretroRuntime?>(null) }
    var ndsStatus by remember { mutableStateOf<String?>(null) }
    var wasRunningBeforeBackground by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, ndsRuntime) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasRunningBeforeBackground = !emulatorCore.isPaused
                    if (wasRunningBeforeBackground) emulatorCore.isPaused = true
                    ndsRuntime?.setPaused(true)
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasRunningBeforeBackground) emulatorCore.isPaused = false
                    wasRunningBeforeBackground = false
                    ndsRuntime?.setPaused(emulatorCore.isPaused)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(ndsRuntime) {
        onDispose {
            ndsRuntime?.close()
        }
    }

    // Intercept back button to return safely to Library
    BackHandler {
        emulatorCore.isPaused = true
        ndsRuntime?.close()
        ndsRuntime = null
        emulatorCore.stopExternalCore()
        coroutineScope.launch {
            romLibraryRepository?.recordPlayStats(game.id, emulatorCore.playTimeSeconds)
        }
        onBackToLibrary()
    }

    // Load game into core on launch
    LaunchedEffect(game) {
        ndsRuntime?.close()
        ndsRuntime = null
        ndsStatus = null
        emulatorCore.loadGame(game)

        if (emulatorCore.currentSystem == RetroSystem.NDS) {
            // Do not present the placeholder NDS demo as if it were real emulation.
            emulatorCore.externalCoreActive = true
            val romBytes = game.romBytes
            val spec = LibretroCoreSpec.forSystem(RetroSystem.NDS)
            if (romBytes == null || romBytes.isEmpty()) {
                ndsStatus = "No ROM data is available for real NDS emulation."
            } else if (spec == null) {
                ndsStatus = "No Nintendo DS core is configured."
            } else {
                val extension = game.romFileName
                    ?.substringAfterLast('.', "nds")
                    ?.lowercase()
                    ?.takeIf { it in spec.extensions } ?: "nds"
                val safeId = game.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val romFile = File(context.cacheDir, "retroforge-$safeId.$extension")
                val runtime = LibretroRuntime(
                    File(context.applicationInfo.nativeLibraryDir, "libretroforge_libretro.so"),
                    spec,
                    File(context.filesDir, "retroforge/system"),
                    File(context.filesDir, "retroforge/saves"),
                    File(context.filesDir, "retroforge/assets")
                )
                runtime.onFrame = { top, bottom -> emulatorCore.updateExternalFrame(top, bottom) }
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        if (!romFile.isFile || romFile.length() != romBytes.size.toLong()) {
                            val temporaryRom = File(context.cacheDir, "${romFile.name}.tmp")
                            temporaryRom.outputStream().use { output -> output.write(romBytes) }
                            if (!temporaryRom.renameTo(romFile)) {
                                temporaryRom.copyTo(romFile, overwrite = true)
                                temporaryRom.delete()
                            }
                        }
                        runtime.loadRom(romFile).getOrThrow()
                    }
                }
                if (result.isSuccess) {
                    ndsRuntime = runtime
                    emulatorCore.externalCoreActive = true
                } else {
                    runtime.close()
                    ndsStatus = result.exceptionOrNull()?.message ?: "Could not start the Nintendo DS core."
                }
            }
        }
        if (isNdsGame) {
            val entries = withContext(Dispatchers.IO) { ndsSaveStore.list(game.id) }
            saveStates.clear()
            entries.forEach { entry ->
                val timestamp = Timestamp(Date(entry.updatedAtMillis))
                saveStates.add(
                    SaveStateDto(
                        id = "nds-slot-${entry.slot}",
                        userId = "local_user",
                        gameId = game.id,
                        gameTitle = game.title,
                        system = game.system,
                        slot = entry.slot.toLong(),
                        stateData = "",
                        playTimeSeconds = emulatorCore.playTimeSeconds,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )
                )
            }
        }

        // NDS save-state payloads are local-only because Firestore documents are size-limited.
        if (!isNdsGame && Firebase.auth.currentUser != null && saveStateRepository != null) {
            try {
                val existing = saveStateRepository.observeSaveStates(game.id).firstOrNull()
                if (existing != null) {
                    saveStates.clear()
                    saveStates.addAll(existing)
                }
            } catch (_: Exception) {}
        }
    }

    // High performance frame loop
    LaunchedEffect(Unit) {
        runCatching { gamepadFocusRequester.requestFocus() }
    }

    var frameTicker by remember { mutableStateOf(0L) }
    LaunchedEffect(ndsRuntime) {
        var nextFrameDeadlineNs = System.nanoTime()
        val frameAccumulator = FrameStepAccumulator()
        while (true) {
            val runtime = ndsRuntime
            if (emulatorCore.isPaused) {
                runtime?.setPaused(true)
            } else if (runtime != null) {
                runtime.setPaused(false)
                // Batch core frames at the native rate while refreshing Compose at ~60 Hz.
                // Fractional speed values (for example 1.5x) are accumulated without drift.
                val steps = frameAccumulator.nextSteps(emulatorCore.fastForwardMultiplier)
                val inputSnapshot = emulatorCore.inputState.copy()
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        repeat(steps) {
                            if (emulatorCore.isPaused) return@runCatching
                            runtime.runFrame(inputSnapshot).getOrThrow()
                        }
                    }
                }
                if (result.isFailure) {
                    ndsStatus = result.exceptionOrNull()?.message ?: "Nintendo DS emulation stopped."
                    runtime.close()
                    ndsRuntime = null
                }
            } else {
                // Legacy/demo systems already apply fast-forward steps inside tick().
                emulatorCore.tick()
            }
            frameTicker = emulatorCore.frameCounter

            // Pace at the core's native refresh rate. Fast-forward changes frames per
            // refresh tick rather than causing unnecessary Compose redraws at 1000 Hz+.
            val frameRate = runtime?.frameRate ?: 60.0
            val frameIntervalNs = (1_000_000_000.0 / frameRate).toLong().coerceAtLeast(1L)
            nextFrameDeadlineNs += frameIntervalNs
            val remainingNs = nextFrameDeadlineNs - System.nanoTime()
            if (remainingNs >= 1_000_000L) {
                delay((remainingNs / 1_000_000L).coerceAtLeast(1L))
            } else {
                if (remainingNs < 0L) nextFrameDeadlineNs = System.nanoTime()
                kotlinx.coroutines.yield()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C14))
            .testTag("emulator_screen")
            .focusRequester(gamepadFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isDown = event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN
                val input = emulatorCore.inputState
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> { input.dpadUp = isDown; true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { input.dpadDown = isDown; true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { input.dpadLeft = isDown; true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { input.dpadRight = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_A -> { input.buttonA = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_B -> { input.buttonB = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_X -> { input.buttonX = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_Y -> { input.buttonY = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_L1 -> { input.triggerL = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_R1 -> { input.triggerR = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_START -> { input.start = isDown; true }
                    AndroidKeyEvent.KEYCODE_BUTTON_SELECT -> { input.select = isDown; true }
                    else -> false
                }
            }
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
                                ndsRuntime?.close()
                                ndsRuntime = null
                                emulatorCore.stopExternalCore()
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
                                text = "${game.system} • ${emulatorCore.playTimeSeconds}s",
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
                                listOf(1.0f, 2.0f, 4.0f, 8.0f, 16.0f).forEach { spd ->
                                    DropdownMenuItem(
                                        text = { Text("${spd.toInt()}x Speed") },
                                        onClick = {
                                            speedMultiplier = spd
                                            emulatorCore.fastForwardMultiplier = spd
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                Text(
                                    text = "EXPERIMENTAL",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                listOf(32, 64, 128, 256, 512, 1000).forEach { requestedSpeed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${requestedSpeed}x Speed • Experimental",
                                                color = Color(0xFFFFB74D)
                                            )
                                        },
                                        onClick = {
                                            speedMultiplier = requestedSpeed.toFloat()
                                            emulatorCore.fastForwardMultiplier = requestedSpeed.toFloat()
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

                        // Quick Save Slot 0 Button
                        IconButton(
                            onClick = {
                                if (isNdsGame) {
                                    saveNdsStateToSlot(
                                        slot = 0L,
                                        game = game,
                                        runtime = ndsRuntime,
                                        store = ndsSaveStore,
                                        emulatorCore = emulatorCore,
                                        saveStates = saveStates,
                                        coroutineScope = coroutineScope,
                                        context = context,
                                        onSaved = {
                                            Toast.makeText(context, "Quick Saved to Slot 1", Toast.LENGTH_SHORT).show()
                                        },
                                        onSaveFailed = {
                                            Toast.makeText(context, "This core could not create a save state.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
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
                                }
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
                screenBitmap = emulatorCore.screenBitmap,
                bottomScreenBitmap = emulatorCore.bottomScreenBitmap,
                isRewinding = emulatorCore.isRewinding,
                fastForwardMultiplier = emulatorCore.fastForwardMultiplier,
                isPaused = emulatorCore.isPaused,
                enableScanlines = enableScanlines,
                frameVersion = frameTicker,
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

        if (ndsStatus != null && emulatorCore.currentSystem == RetroSystem.NDS) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                color = Color(0xEE7A1F1F),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = ndsStatus ?: "",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Save State Slots Dialog
        if (showSlotDialog) {
            SaveSlotDialog(
                gameTitle = game.title,
                systemName = game.system,
                existingSaves = saveStates,
                isLocalOnly = isNdsGame,
                onSaveToSlot = { slot ->
                    if (isNdsGame) {
                        saveNdsStateToSlot(
                            slot = slot,
                            game = game,
                            runtime = ndsRuntime,
                            store = ndsSaveStore,
                            emulatorCore = emulatorCore,
                            saveStates = saveStates,
                            coroutineScope = coroutineScope,
                            context = context,
                            onSaved = {
                                Toast.makeText(context, "State saved to Slot ${slot + 1}", Toast.LENGTH_SHORT).show()
                            },
                            onSaveFailed = {
                                Toast.makeText(context, "This core could not create a save state.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
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
                    }
                },
                onLoadFromSlot = { dto ->
                    if (isNdsGame) {
                        val runtime = ndsRuntime
                        if (runtime == null) {
                            Toast.makeText(context, "Nintendo DS core is not running.", Toast.LENGTH_SHORT).show()
                        } else {
                            val wasPaused = emulatorCore.isPaused
                            emulatorCore.isPaused = true
                            runtime.setPaused(true)
                            coroutineScope.launch {
                                try {
                                    val stateBytes = withContext(Dispatchers.IO) {
                                        ndsSaveStore.load(game.id, dto.slot.toInt())
                                            ?: throw IllegalStateException("Save slot is empty.")
                                    }
                                    val restored = withContext(Dispatchers.Default) {
                                        runtime.loadState(stateBytes)
                                    }
                                    check(restored) { "The Nintendo DS core rejected this save state." }
                                    Toast.makeText(context, "Loaded Slot ${dto.slot + 1}", Toast.LENGTH_SHORT).show()
                                    showSlotDialog = false
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (failure: Throwable) {
                                    Toast.makeText(
                                        context,
                                        failure.message ?: "Failed to load state.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    emulatorCore.isPaused = wasPaused
                                    runtime.setPaused(wasPaused)
                                }
                            }
                        }
                    } else {
                        val success = emulatorCore.loadSaveStateBase64(dto.stateData)
                        if (success) {
                            Toast.makeText(context, "Loaded Slot ${dto.slot + 1}", Toast.LENGTH_SHORT).show()
                            showSlotDialog = false
                        } else {
                            Toast.makeText(context, "Failed to load state", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDeleteSlot = { saveId ->
                    coroutineScope.launch {
                        if (isNdsGame) {
                            saveId.substringAfterLast('-').toIntOrNull()?.let { slot ->
                                withContext(Dispatchers.IO) { ndsSaveStore.delete(game.id, slot) }
                            }
                        } else {
                            saveStateRepository?.deleteSaveState(saveId)
                        }
                        saveStates.removeAll { it.id == saveId }
                    }
                },
                onDismiss = { showSlotDialog = false }
            )
        }
    }
}

private fun saveNdsStateToSlot(
    slot: Long,
    game: RetroGame,
    runtime: LibretroRuntime?,
    store: NdsSaveStateStore,
    emulatorCore: EmulatorCore,
    saveStates: MutableList<SaveStateDto>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onSaved: () -> Unit,
    onSaveFailed: () -> Unit
) {
    val activeRuntime = runtime
    if (activeRuntime == null) {
        onSaveFailed()
        return
    }
    val wasPaused = emulatorCore.isPaused
    emulatorCore.isPaused = true
    activeRuntime.setPaused(true)
    coroutineScope.launch {
        try {
            val stateBytes = withContext(Dispatchers.Default) {
                activeRuntime.saveState()
                    ?: throw IllegalStateException("The loaded core does not support save states.")
            }
            val entry = withContext(Dispatchers.IO) {
                store.save(game.id, slot.toInt(), stateBytes)
            }
            val timestamp = Timestamp(Date(entry.updatedAtMillis))
            val dto = SaveStateDto(
                id = "nds-slot-${entry.slot}",
                userId = "local_user",
                gameId = game.id,
                gameTitle = game.title,
                system = game.system,
                slot = entry.slot.toLong(),
                stateData = "",
                playTimeSeconds = emulatorCore.playTimeSeconds,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            val existingIndex = saveStates.indexOfFirst { it.slot == dto.slot }
            if (existingIndex >= 0) saveStates[existingIndex] = dto else saveStates.add(dto)
            onSaved()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Toast.makeText(
                context,
                failure.message ?: "Could not save Nintendo DS state.",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            emulatorCore.isPaused = wasPaused
            activeRuntime.setPaused(wasPaused)
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
    if (stateBase64.isBlank()) return
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

    val existingIndex = saveStates.indexOfFirst { it.slot == slot }
    if (existingIndex >= 0) saveStates[existingIndex] = newSave else saveStates.add(newSave)

    if (Firebase.auth.currentUser != null && saveStateRepository != null) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            saveStateRepository.saveState(newSave)
        }
    }
    onSaved()
}
