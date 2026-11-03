package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import android.view.View
import android.util.Base64
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.emulator.LibretroAudioOutput
import com.example.emulator.LibretroCoreSpec
import com.example.emulator.LibretroRuntime
import com.example.emulator.LibretroVideoConverter
import com.example.emulator.N3dsSaveStateStore
import com.example.emulator.N3dsSystemDataImporter
import com.example.emulator.N3dsInputMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
import com.example.ui.components.DualScreenLayout
import com.example.ui.components.RetroScreenView
import com.example.ui.components.SaveSlotDialog
import com.example.ui.components.VirtualController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.data.local.RomLibraryRepository
import kotlinx.coroutines.delay
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
    val coroutineScope = rememberCoroutineScope()

    val isNintendo3ds = game.system.equals("N3DS", ignoreCase = true)
    val n3dsRuntime = remember(game.id, isNintendo3ds) {
        if (isNintendo3ds) {
            val frontend = File(context.applicationInfo.nativeLibraryDir, "libretroforge_libretro.so")
            LibretroRuntime(
                frontend,
                checkNotNull(LibretroCoreSpec.forSystem(com.example.emulator.RetroSystem.N3DS)),
                File(context.filesDir, "retroforge/3ds/system"),
                File(context.filesDir, "retroforge/3ds/saves")
            )
        } else {
            null
        }
    }
    var n3dsTopBitmap by remember(game.id) { mutableStateOf<Bitmap?>(null) }
    var n3dsBottomBitmap by remember(game.id) { mutableStateOf<Bitmap?>(null) }
    var n3dsError by remember(game.id) { mutableStateOf<String?>(null) }
    var n3dsFrameIntervalMs by remember(game.id) { mutableLongStateOf(16L) }
    var n3dsFramesPerSecond by remember(game.id) { mutableDoubleStateOf(60.0) }
    var n3dsAudioOutput by remember(game.id) { mutableStateOf<LibretroAudioOutput?>(null) }
    var n3dsLifecyclePaused by remember(game.id) { mutableStateOf(false) }
    var n3dsLayout by remember(game.id) { mutableStateOf(DualScreenLayout.STACKED) }
    var showN3dsLayoutMenu by remember { mutableStateOf(false) }
    var isImporting3dsData by remember(game.id) { mutableStateOf(false) }
    val n3dsSaveStore = remember(game.id) {
        if (isNintendo3ds) N3dsSaveStateStore(File(context.filesDir, "retroforge/3ds/states")) else null
    }
    val n3dsSystemDataPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            if (n3dsRuntime?.state == LibretroRuntime.State.GAME_LOADED) {
                Toast.makeText(
                    context,
                    "Close the running 3DS game before importing system data.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    Toast.makeText(context, "The folder provider did not grant persistent access; importing now.", Toast.LENGTH_SHORT).show()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(context, "The folder provider did not grant persistent access; importing now.", Toast.LENGTH_SHORT).show()
                }
                coroutineScope.launch {
                    isImporting3dsData = true
                    try {
                        val result = withContext(Dispatchers.IO) {
                            N3dsSystemDataImporter.importFromTree(
                                context,
                                treeUri,
                                File(context.filesDir, "retroforge/3ds/saves/Azahar")
                            )
                        }
                        result.onSuccess { summary ->
                            val megabytes = summary.bytesCopied / (1024L * 1024L)
                            Toast.makeText(
                                context,
                                "Imported ${summary.filesCopied} files (${megabytes} MiB). Reopen the 3DS game to apply.",
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { failure ->
                            Toast.makeText(
                                context,
                                failure.message ?: "Could not import 3DS system data.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        isImporting3dsData = false
                    }
                }
            }
        }
    }

    var activeSkin by remember { mutableStateOf(initialSkin) }
    var enableScanlines by remember { mutableStateOf(true) }
    var showSlotDialog by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSkinMenu by remember { mutableStateOf(false) }

    val saveStates = remember { mutableStateListOf<SaveStateDto>() }

    // Intercept back button to return safely to Library
    BackHandler {
        emulatorCore.isPaused = true
        n3dsAudioOutput?.pause()
        coroutineScope.launch {
            romLibraryRepository?.recordPlayStats(game.id, emulatorCore.playTimeSeconds)
        }
        onBackToLibrary()
    }

    // Load the game and start the real Azahar core for Nintendo 3DS ROMs.
    LaunchedEffect(game, n3dsRuntime) {
        emulatorCore.loadGame(game)
        n3dsError = null
        n3dsTopBitmap = null
        n3dsBottomBitmap = null
        n3dsAudioOutput?.close()
        n3dsAudioOutput = null

        if (n3dsRuntime != null) {
            try {
                val romFile = withContext(Dispatchers.IO) {
                    val extension = game.romFileName?.substringAfterLast('.', "")?.lowercase()
                        ?: throw IllegalArgumentException("Nintendo 3DS ROM filename is missing.")
                    require(extension in setOf("3ds", "3dsx", "z3dsx", "elf", "axf", "cci", "zcci", "cxi", "zcxi", "app")) {
                        "Unsupported Nintendo 3DS ROM extension: .$extension"
                    }
                    val safeId = game.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val cachedRom = File(context.cacheDir, "retroforge-$safeId.$extension")
                    val stagingRom = File(context.cacheDir, "${cachedRom.name}.part")
                    stagingRom.delete()
                    try {
                        FileOutputStream(stagingRom).use { output ->
                            val inMemoryRom = game.romBytes
                            if (inMemoryRom != null) {
                                require(inMemoryRom.size.toLong() <= MAX_N3DS_ROM_BYTES) {
                                    "Nintendo 3DS ROM exceeds the 4 GiB staging limit."
                                }
                                output.write(inMemoryRom)
                            } else {
                                val uri = game.romUri
                                    ?: throw IllegalStateException("No accessible ROM URI was provided for this Nintendo 3DS game.")
                                val input = context.contentResolver.openInputStream(Uri.parse(uri))
                                    ?: throw IllegalStateException("Android could not open the selected ROM.")
                                input.use { source ->
                                    val buffer = ByteArray(1024 * 1024)
                                    var copiedBytes = 0L
                                    while (true) {
                                        val count = source.read(buffer)
                                        if (count < 0) break
                                        copiedBytes += count
                                        require(copiedBytes <= MAX_N3DS_ROM_BYTES) {
                                            "Nintendo 3DS ROM exceeds the 4 GiB staging limit."
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            output.fd.sync()
                        }
                        require(stagingRom.isFile && stagingRom.length() in 1..MAX_N3DS_ROM_BYTES) {
                            "The copied ROM is empty or exceeds the 4 GiB staging limit."
                        }
                        check(stagingRom.renameTo(cachedRom)) {
                            "Could not safely activate the staged Nintendo 3DS ROM."
                        }
                    } catch (failure: Throwable) {
                        stagingRom.delete()
                        throw failure
                    }
                    cachedRom
                }
                withContext(Dispatchers.IO) {
                    n3dsRuntime.loadRom(romFile).getOrThrow()
                }
                val avInfo = withContext(Dispatchers.IO) {
                    n3dsRuntime.getAvInfo().getOrThrow()
                }
                n3dsFramesPerSecond = avInfo.framesPerSecond
                n3dsFrameIntervalMs = (1000.0 / avInfo.framesPerSecond)
                    .roundToInt().coerceIn(1, 100).toLong()
                n3dsAudioOutput = withContext(Dispatchers.IO) {
                    LibretroAudioOutput(avInfo.sampleRate.roundToInt())
                }
            } catch (failure: Exception) {
                n3dsError = failure.message ?: "Nintendo 3DS core could not start."
                n3dsRuntime.close()
            }
        }

        if (isNintendo3ds) {
            val localSlots = withContext(Dispatchers.IO) {
                n3dsSaveStore?.listSlots(game.id).orEmpty()
            }
            saveStates.clear()
            saveStates.addAll(localSlots.map { slot ->
                SaveStateDto(
                    id = "${game.id}_slot_$slot",
                    userId = "local_user",
                    gameId = game.id,
                    gameTitle = game.title,
                    system = game.system,
                    slot = slot,
                    label = "On-device 3DS state",
                    stateData = LOCAL_N3DS_STATE_MARKER
                )
            })
        }

        // Load cloud saves if logged in (N3DS states remain on-device because
        // full 3DS snapshots can exceed Firestore's per-document size limit).
        if (!isNintendo3ds && Firebase.auth.currentUser != null && saveStateRepository != null) {
            try {
                val existing = saveStateRepository.observeSaveStates(game.id).firstOrNull()
                if (existing != null) {
                    saveStates.clear()
                    saveStates.addAll(existing)
                }
            } catch (_: Exception) {}
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, n3dsRuntime) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    n3dsLifecyclePaused = true
                    n3dsAudioOutput?.pause()
                    emulatorCore.inputState.reset()
                }
                Lifecycle.Event.ON_RESUME -> {
                    n3dsLifecyclePaused = false
                    if (!emulatorCore.isPaused) n3dsAudioOutput?.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            n3dsAudioOutput?.close()
            n3dsAudioOutput = null
            n3dsRuntime?.close()
            emulatorCore.inputState.reset()
        }
    }

    // Native core and the demo cores share one paced frame loop. Native work
    // runs off the UI thread; Compose only receives completed display bitmaps.
    var frameTicker by remember { mutableStateOf(0L) }
    LaunchedEffect(game, n3dsRuntime) {
        while (true) {
            val frameStartedAtNanos = System.nanoTime()
            if (n3dsRuntime != null) {
                if (n3dsRuntime.state == LibretroRuntime.State.GAME_LOADED &&
                    !emulatorCore.isPaused && !n3dsLifecyclePaused) {
                    try {
                        // Snapshot mutable UI input on the main thread so one native
                        // frame receives a coherent set of controls.
                        val inputSnapshot = emulatorCore.inputState.copy()
                        val framesToRun = speedMultiplier.toInt().coerceIn(1, 1000)
                        val output = withContext(Dispatchers.Default) {
                            var latestDisplays: Pair<Bitmap, Bitmap>? = null
                            var latestAudio = ShortArray(0)
                            repeat(framesToRun) { frameIndex ->
                                sendNintendo3dsInput(n3dsRuntime, inputSnapshot)
                                n3dsRuntime.runFrame().getOrThrow()
                                if (frameIndex == framesToRun - 1) {
                                    val frame = n3dsRuntime.copyVideoFrame().getOrThrow()
                                    latestDisplays = frame?.let(LibretroVideoConverter::splitDualScreenFrame)
                                }
                                val audio = n3dsRuntime.drainAudioSamples().getOrThrow()
                                if (framesToRun == 1) latestAudio = audio
                            }
                            Triple(latestDisplays, latestAudio, framesToRun)
                        }
                        output.first?.let { (top, bottom) ->
                            n3dsTopBitmap = top
                            n3dsBottomBitmap = bottom
                        }
                        // Avoid queuing stale/overwhelming audio while the core is
                        // running ahead; normal-speed audio remains unchanged.
                        if (framesToRun == 1) n3dsAudioOutput?.write(output.second)
                        emulatorCore.recordExternalFrames(output.third, n3dsFramesPerSecond)
                        frameTicker = emulatorCore.frameCounter
                    } catch (failure: Exception) {
                        n3dsError = failure.message ?: "Nintendo 3DS emulation stopped unexpectedly."
                        emulatorCore.isPaused = true
                        n3dsAudioOutput?.pause()
                    }
                }
                if (speedMultiplier <= 1f) {
                    val elapsedFrameMs = (System.nanoTime() - frameStartedAtNanos) / 1_000_000L
                    delay((n3dsFrameIntervalMs - elapsedFrameMs).coerceAtLeast(0L))
                } else {
                    // Best-effort uncapped fast-forward: the core, not a timer,
                    // determines the achievable speed.
                    delay(0)
                }
            } else {
                emulatorCore.tick()
                frameTicker = emulatorCore.frameCounter
                if (emulatorCore.fastForwardMultiplier <= 1f) delay(16L) else delay(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C14))
            .testTag("emulator_screen")
    ) {
        if (isNintendo3ds) {
            AndroidView(
                modifier = Modifier.size(1.dp).alpha(0f),
                factory = { viewContext ->
                    object : View(viewContext) {
                        init {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }

                        override fun onAttachedToWindow() {
                            super.onAttachedToWindow()
                            requestFocus()
                        }

                        override fun onKeyDown(keyCode: Int, event: AndroidKeyEvent): Boolean =
                            N3dsInputMapper.applyKeyEvent(emulatorCore.inputState, keyCode, true) ||
                                super.onKeyDown(keyCode, event)

                        override fun onKeyUp(keyCode: Int, event: AndroidKeyEvent): Boolean =
                            N3dsInputMapper.applyKeyEvent(emulatorCore.inputState, keyCode, false) ||
                                super.onKeyUp(keyCode, event)

                        override fun onGenericMotionEvent(event: MotionEvent): Boolean =
                            N3dsInputMapper.applyMotionEvent(emulatorCore.inputState, event) ||
                                super.onGenericMotionEvent(event)
                    }
                }
            )
        }

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
                                n3dsAudioOutput?.pause()
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
                                listOf(1.0f, 2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f, 128.0f, 256.0f, 512.0f, 1000.0f).forEach { spd ->
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

                        if (isNintendo3ds) {
                            IconButton(
                                onClick = { n3dsSystemDataPicker.launch(null) },
                                enabled = !isImporting3dsData,
                                modifier = Modifier.testTag("button_n3ds_system_data")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Import 3DS system data",
                                    tint = if (isImporting3dsData) Color.Gray else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isNintendo3ds) {
                            Box {
                                IconButton(
                                    onClick = { showN3dsLayoutMenu = true },
                                    modifier = Modifier.testTag("button_n3ds_layout")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = "3DS screen layout",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showN3dsLayoutMenu,
                                    onDismissRequest = { showN3dsLayoutMenu = false }
                                ) {
                                    DualScreenLayout.entries.forEach { layout ->
                                        DropdownMenuItem(
                                            text = { Text(layout.label) },
                                            onClick = {
                                                n3dsLayout = layout
                                                showN3dsLayoutMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Pause Toggle
                        IconButton(
                            onClick = {
                                emulatorCore.isPaused = !emulatorCore.isPaused
                                if (emulatorCore.isPaused) {
                                    n3dsAudioOutput?.pause()
                                } else if (!n3dsLifecyclePaused) {
                                    n3dsAudioOutput?.resume()
                                }
                            },
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
                                coroutineScope.launch {
                                    saveStateToSlot(
                                    slot = 0,
                                    game = game,
                                    emulatorCore = emulatorCore,
                                    saveStateRepository = saveStateRepository,
                                    saveStates = saveStates,
                                    libretroRuntime = n3dsRuntime,
                                    n3dsSaveStore = n3dsSaveStore,
                                    thumbnailBitmap = n3dsTopBitmap,
                                    onSaveFailed = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    },
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
                screenBitmap = n3dsTopBitmap ?: emulatorCore.screenBitmap,
                bottomScreenBitmap = n3dsBottomBitmap ?: emulatorCore.bottomScreenBitmap,
                dualScreenLayout = if (isNintendo3ds) n3dsLayout else DualScreenLayout.STACKED,
                frameVersion = frameTicker,
                isRewinding = emulatorCore.isRewinding,
                fastForwardMultiplier = emulatorCore.fastForwardMultiplier,
                isPaused = emulatorCore.isPaused,
                enableScanlines = enableScanlines,
                onTouchInput = { nx, ny, touching ->
                    emulatorCore.inputState.touchX = nx
                    emulatorCore.inputState.touchY = ny
                    emulatorCore.inputState.isTouching = touching
                    if (n3dsRuntime?.state == LibretroRuntime.State.GAME_LOADED) {
                        n3dsRuntime.setPointer(
                            x = (nx * 65_535f - 32_768f).roundToInt(),
                            y = (ny * 65_535f - 32_768f).roundToInt(),
                            pressed = touching
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            if (n3dsError != null) {
                Text(
                    text = "3DS core: $n3dsError",
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (isNintendo3ds) {
                Text(
                    text = "Azahar requires decrypted 3DS ROMs. Some games also need system or Mii data dumped from your own console. Import the Azahar folder with the folder icon.",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }


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
                modifier = Modifier.fillMaxWidth(),
                rewindEnabled = !isNintendo3ds
            )
        }

        // Save State Slots Dialog
        if (showSlotDialog) {
            SaveSlotDialog(
                gameTitle = game.title,
                systemName = game.system,
                existingSaves = saveStates,
                onSaveToSlot = { slot ->
                    coroutineScope.launch {
                        saveStateToSlot(
                        slot = slot,
                        game = game,
                        emulatorCore = emulatorCore,
                        saveStateRepository = saveStateRepository,
                        saveStates = saveStates,
                        libretroRuntime = n3dsRuntime,
                        n3dsSaveStore = n3dsSaveStore,
                        thumbnailBitmap = n3dsTopBitmap,
                        onSaveFailed = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        },
                        onSaved = {
                            Toast.makeText(context, "State saved to Slot ${slot + 1}", Toast.LENGTH_SHORT).show()
                        }
                        )
                    }
                },
                onLoadFromSlot = { dto ->
                    coroutineScope.launch {
                        val success = if (isNintendo3ds) {
                            withContext(Dispatchers.IO) {
                                val serialized = n3dsSaveStore?.load(game.id, dto.slot)
                                serialized != null && n3dsRuntime?.loadStateBytes(serialized)?.isSuccess == true
                            }
                        } else {
                            emulatorCore.loadSaveStateBase64(dto.stateData)
                        }
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
                        val localSlot = saveStates.firstOrNull { it.id == saveId }?.slot
                        if (isNintendo3ds && localSlot != null) {
                            withContext(Dispatchers.IO) { n3dsSaveStore?.delete(game.id, localSlot) }
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

private const val LOCAL_N3DS_STATE_MARKER = "local-n3ds-state"
private const val MAX_N3DS_ROM_BYTES = 4L * 1024L * 1024L * 1024L

private fun sendNintendo3dsInput(
    runtime: LibretroRuntime,
    input: com.example.emulator.EmulatorInputState
) {
    // RETRO_DEVICE_ID_JOYPAD values from the Libretro ABI.
    val buttons = arrayOf(
        0 to input.buttonB,
        1 to input.buttonY,
        2 to input.select,
        3 to input.start,
        4 to input.dpadUp,
        5 to input.dpadDown,
        6 to input.dpadLeft,
        7 to input.dpadRight,
        8 to input.buttonA,
        9 to input.buttonX,
        10 to input.triggerL,
        11 to input.triggerR,
        12 to input.triggerZL,
        13 to input.triggerZR,
        14 to input.buttonL3,
        15 to input.buttonR3
    )
    buttons.forEach { (button, pressed) ->
        runtime.setJoypadButton(button = button, pressed = pressed)
    }
    runtime.setAnalogAxis(stick = 0, axis = 0, value = (input.stickX * 32_767f).roundToInt())
    runtime.setAnalogAxis(stick = 0, axis = 1, value = (input.stickY * 32_767f).roundToInt())
    runtime.setAnalogAxis(stick = 1, axis = 0, value = (input.stick2X * 32_767f).roundToInt())
    runtime.setAnalogAxis(stick = 1, axis = 1, value = (input.stick2Y * 32_767f).roundToInt())
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
        "Could not encode the save-state thumbnail."
    }
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private suspend fun saveStateToSlot(
    slot: Long,
    game: RetroGame,
    emulatorCore: EmulatorCore,
    saveStateRepository: SaveStateRepository?,
    saveStates: MutableList<SaveStateDto>,
    libretroRuntime: LibretroRuntime? = null,
    n3dsSaveStore: N3dsSaveStateStore? = null,
    thumbnailBitmap: Bitmap? = null,
    onSaveFailed: (String) -> Unit = {},
    onSaved: () -> Unit
) {
    val stateBase64 = if (emulatorCore.currentSystem == com.example.emulator.RetroSystem.N3DS) {
        try {
            withContext(Dispatchers.IO) {
                val serialized = libretroRuntime?.serializeStateBytes()?.getOrThrow()
                    ?: throw IllegalStateException("Nintendo 3DS save states are unavailable for this core.")
                n3dsSaveStore?.save(game.id, slot, serialized)
                    ?: throw IllegalStateException("Local Nintendo 3DS save storage is unavailable.")
            }
            LOCAL_N3DS_STATE_MARKER
        } catch (failure: Exception) {
            onSaveFailed(failure.message ?: "Could not save the Nintendo 3DS state.")
            return
        }
    } else {
        emulatorCore.exportSaveStateBase64()
    }
    val thumbnail = if (thumbnailBitmap != null) {
        withContext(Dispatchers.Default) { bitmapToBase64(thumbnailBitmap) }
    } else {
        emulatorCore.generateThumbnailBase64()
    }
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
    if (emulatorCore.currentSystem != com.example.emulator.RetroSystem.N3DS &&
        Firebase.auth.currentUser != null && saveStateRepository != null) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            saveStateRepository.saveState(newSave)
        }
    }
    onSaved()
}
