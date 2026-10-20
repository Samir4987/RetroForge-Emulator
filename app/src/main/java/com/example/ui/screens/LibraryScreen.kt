package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.RomLibraryRepository
import com.example.data.local.ScannedRomEntity
import com.example.data.model.RetroGame
import com.example.emulator.RetroSystem
import com.example.ui.components.BoxArtCard
import com.example.ui.components.ScannedRomDetailSheet
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    romLibraryRepository: RomLibraryRepository,
    onGameSelected: (RetroGame) -> Unit,
    onOpenCloudVault: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedSystemFilter by remember { mutableStateOf("ALL") }
    var isGridView by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(false) }
    var inspectRom by remember { mutableStateOf<ScannedRomEntity?>(null) }

    val systems = listOf("ALL", "GB", "GBC", "GBA", "NDS", "N64")

    // Collect games reactively from Room Database
    val allRomsFlow = remember(selectedSystemFilter) {
        romLibraryRepository.getRomsBySystem(selectedSystemFilter)
    }
    val romList by allRomsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Multi-file ROM scanner
    val multiRomPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isScanning = true
            coroutineScope.launch {
                val scanned = romLibraryRepository.scanAndInsertMultipleUris(context, uris)
                isScanning = false
                Toast.makeText(context, "Scanned and indexed ${scanned.size} ROMs in local database", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Single file ROM loader
    val singleRomPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isScanning = true
            coroutineScope.launch {
                val entity = romLibraryRepository.scanAndInsertUri(context, uri)
                isScanning = false
                if (entity != null) {
                    Toast.makeText(context, "Indexed ${entity.title} (${entity.system})", Toast.LENGTH_SHORT).show()
                    val bytes = readRomBytes(context, uri)
                    onGameSelected(entity.toRetroGame(bytes))
                }
            }
        }
    }

    val currentUser = Firebase.auth.currentUser

    // Ensure built-in games are seeded into Room DB
    LaunchedEffect(Unit) {
        romLibraryRepository.seedBuiltInGamesIfEmpty()
    }

    Scaffold(
        modifier = Modifier.testTag("library_screen"),
        containerColor = Color(0xFF0F111A),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RetroForge",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "LOCAL DB",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // View Mode Toggle (Grid vs List)
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("toggle_view_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List View",
                            tint = Color.White
                        )
                    }

                    // Cloud Vault Button
                    IconButton(
                        onClick = onOpenCloudVault,
                        modifier = Modifier.testTag("cloud_vault_button")
                    ) {
                        Icon(
                            imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = "Cloud Save Vault",
                            tint = if (currentUser != null) Color(0xFF00E5FF) else Color(0xFFB0B3C7)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Emulator Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131522))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    singleRomPickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/x-gameboy-rom",
                            "application/x-gba-rom",
                            "application/x-n64-rom",
                            "*/*"
                        )
                    )
                },
                containerColor = Color(0xFF00E5FF),
                contentColor = Color.Black,
                modifier = Modifier.testTag("load_rom_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Load ROM")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Play File", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scan ROMs Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("scan_roms_banner"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C29))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ROM Scanner & Box Art",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${romList.size} IN ROOM DB",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Parses headers for GB, GBC, GBA, NDS, and N64",
                            color = Color(0xFFB0B3C7),
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            multiRomPickerLauncher.launch(
                                arrayOf(
                                    "application/octet-stream",
                                    "application/x-gameboy-rom",
                                    "application/x-gba-rom",
                                    "application/x-n64-rom",
                                    "*/*"
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("scan_roms_button"),
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = "Scan ROMs",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan ROMs", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // System Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(systems) { sys ->
                    val isSelected = selectedSystemFilter == sys
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSystemFilter = sys },
                        label = {
                            Text(
                                text = sys,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF),
                            containerColor = Color(0xFF1C1E2D)
                        ),
                        modifier = Modifier.testTag("filter_chip_$sys")
                    )
                }
            }

            // Games Display (Box Art Grid or List View)
            if (romList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ROMs found for $selectedSystemFilter. Tap 'Scan ROMs' above to index your game files!",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else if (isGridView) {
                // Box Art Grid View (2-column physical box art presentation)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .testTag("rom_box_art_grid"),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(romList, key = { it.id }) { rom ->
                        BoxArtCard(
                            rom = rom,
                            onPlay = {
                                coroutineScope.launch {
                                    romLibraryRepository.recordPlayStats(rom.id, 0L)
                                    val bytes = readRomBytes(context, Uri.parse(rom.fileUri))
                                    onGameSelected(rom.toRetroGame(bytes))
                                }
                            },
                            onShowDetails = {
                                inspectRom = rom
                            }
                        )
                    }
                }
            } else {
                // Compact List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("rom_list_view"),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(romList, key = { it.id }) { rom ->
                        ListRomCardItem(
                            rom = rom,
                            onPlay = {
                                coroutineScope.launch {
                                    romLibraryRepository.recordPlayStats(rom.id, 0L)
                                    val bytes = readRomBytes(context, Uri.parse(rom.fileUri))
                                    onGameSelected(rom.toRetroGame(bytes))
                                }
                            },
                            onShowDetails = {
                                inspectRom = rom
                            }
                        )
                    }
                }
            }
        }

        // Details Modal Sheet
        inspectRom?.let { rom ->
            ScannedRomDetailSheet(
                rom = rom,
                onPlay = {
                    inspectRom = null
                    coroutineScope.launch {
                        romLibraryRepository.recordPlayStats(rom.id, 0L)
                        val bytes = readRomBytes(context, Uri.parse(rom.fileUri))
                        onGameSelected(rom.toRetroGame(bytes))
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        romLibraryRepository.deleteRom(rom.id)
                        inspectRom = null
                    }
                },
                onDismiss = { inspectRom = null }
            )
        }
    }
}

@Composable
private fun ListRomCardItem(
    rom: ScannedRomEntity,
    onPlay: () -> Unit,
    onShowDetails: () -> Unit
) {
    val systemEnum = RetroSystem.fromCode(rom.system)
    val systemColor = when (systemEnum) {
        RetroSystem.GB -> Color(0xFF9BBB0F)
        RetroSystem.GBC -> Color(0xFF8E44AD)
        RetroSystem.GBA -> Color(0xFF2980B9)
        RetroSystem.NDS -> Color(0xFF16A085)
        RetroSystem.N64 -> Color(0xFFD35400)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_list_card_${rom.id}")
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C29))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(rom.boxArtPrimaryColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = rom.iconEmoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = systemColor.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = rom.system,
                            color = systemColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${rom.genre}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = rom.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${rom.publisher} • ${rom.cartridgeMapper} • ${(rom.fileSize / 1024).coerceAtLeast(1)} KB",
                    color = Color(0xFFB0B3C7),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onShowDetails, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Details",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun readRomBytes(context: Context, uri: Uri): ByteArray? {
    if (uri.scheme == "builtin") return null
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        bytes
    } catch (_: Exception) {
        null
    }
}
