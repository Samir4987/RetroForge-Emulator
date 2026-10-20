package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserPreferencesDto
import com.example.data.repository.PreferencesRepository
import com.example.emulator.AudioSynthesizer
import com.example.emulator.ControllerSkin
import com.example.emulator.EmulatorCore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSkin: ControllerSkin,
    onSkinChanged: (ControllerSkin) -> Unit,
    emulatorCore: EmulatorCore,
    preferencesRepository: PreferencesRepository?,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedSkin by remember { mutableStateOf(currentSkin) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    var audioEnabled by remember { mutableStateOf(true) }
    var audioVolume by remember { mutableFloatStateOf(0.8f) }
    var defaultFastForward by remember { mutableFloatStateOf(2.0f) }
    var rewindFrames by remember { mutableFloatStateOf(300f) }

    BackHandler {
        onBack()
    }

    fun persistPreferences() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null && preferencesRepository != null) {
            coroutineScope.launch {
                preferencesRepository.savePreferences(
                    UserPreferencesDto(
                        userId = uid,
                        activeSkin = selectedSkin.id,
                        hapticsEnabled = hapticsEnabled,
                        fastForwardSpeed = defaultFastForward.toLong(),
                        rewindCapacityFrames = rewindFrames.toLong(),
                        audioEnabled = audioEnabled,
                        audioVolume = (audioVolume * 100).toLong(),
                        screenScaleMode = "ASPECT_FIT"
                    )
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settings_screen"),
        containerColor = Color(0xFF0F111A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Emulator Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("button_back_from_settings")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141724))
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customizable Controller Skins Section
            item {
                Text(
                    text = "CONTROLLER SKINS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ControllerSkin.entries) { skin ->
                        val isSelected = selectedSkin == skin
                        SkinPreviewCard(
                            skin = skin,
                            isSelected = isSelected,
                            onSelect = {
                                selectedSkin = skin
                                onSkinChanged(skin)
                                persistPreferences()
                            }
                        )
                    }
                }
            }

            // Gameplay & Performance Section
            item {
                Text(
                    text = "FAST-FORWARD & REWIND ENGINE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Turbo Fast-Forward Multiplier",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Default speed when tapping TURBO",
                                    color = Color(0xFFB0B3C7),
                                    fontSize = 12.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00C853)
                            ) {
                                Text(
                                    text = "${defaultFastForward}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Slider(
                            value = defaultFastForward,
                            onValueChange = {
                                defaultFastForward = it
                                persistPreferences()
                            },
                            valueRange = 1.5f..8.0f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Rewind Buffer Capacity",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Hold REWIND to reverse mistakes (~${(rewindFrames / 30).toInt()}s history)",
                                    color = Color(0xFFB0B3C7),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "${rewindFrames.toInt()}f",
                                color = Color(0xFFFF0055),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = rewindFrames,
                            onValueChange = {
                                rewindFrames = it
                                persistPreferences()
                            },
                            valueRange = 60f..600f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF0055),
                                activeTrackColor = Color(0xFFFF0055)
                            )
                        )
                    }
                }
            }

            // Audio & Haptics Section
            item {
                Text(
                    text = "AUDIO & HAPTIC VIBRATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Controller Haptic Vibration",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tactile feedback on D-Pad and action buttons",
                                    color = Color(0xFFB0B3C7),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = hapticsEnabled,
                                onCheckedChange = {
                                    hapticsEnabled = it
                                    persistPreferences()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Chiptune Audio Synthesizer",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Pulse waves, square waves, and noise bursts",
                                    color = Color(0xFFB0B3C7),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = audioEnabled,
                                onCheckedChange = {
                                    audioEnabled = it
                                    emulatorCore.audioSynthesizer.setEnabled(it)
                                    persistPreferences()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                            )
                        }

                        if (audioEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Synthesizer Volume: ${(audioVolume * 100).toInt()}%",
                                color = Color(0xFFB0B3C7),
                                fontSize = 12.sp
                            )
                            Slider(
                                value = audioVolume,
                                onValueChange = {
                                    audioVolume = it
                                    emulatorCore.audioSynthesizer.setVolume(it)
                                    persistPreferences()
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinPreviewCard(
    skin: ControllerSkin,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .testTag("skin_card_${skin.id}")
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2C2F42),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual Miniature Skin Controller Preview
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(skin.bodyColor),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // D-Pad preview
                    Box(modifier = Modifier.size(16.dp).background(skin.dpadColor, CircleShape))
                    // Buttons preview
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(skin.buttonBColor, CircleShape))
                        Box(modifier = Modifier.size(10.dp).background(skin.buttonAColor, CircleShape))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = skin.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )

            if (isSelected) {
                Surface(
                    color = Color(0xFF00E5FF),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
