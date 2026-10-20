package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.data.model.RetroGame
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SaveStateRepository
import com.example.emulator.AudioSynthesizer
import com.example.emulator.ControllerSkin
import com.example.emulator.EmulatorCore
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.EmulatorScreen
import com.example.data.local.RetroForgeDatabase
import com.example.data.local.RomLibraryRepository
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

enum class AppScreen {
    AUTH,
    LIBRARY,
    EMULATOR,
    CLOUD_SYNC,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var audioSynthesizer: AudioSynthesizer
    private lateinit var emulatorCore: EmulatorCore
    private var saveStateRepository: SaveStateRepository? = null
    private var preferencesRepository: PreferencesRepository? = null
    private lateinit var romLibraryRepository: RomLibraryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioSynthesizer = AudioSynthesizer()
        emulatorCore = EmulatorCore(audioSynthesizer)

        val romDatabase = RetroForgeDatabase.getInstance(this)
        romLibraryRepository = RomLibraryRepository(romDatabase.romLibraryDao())

        try {
            saveStateRepository = SaveStateRepository(this)
            preferencesRepository = PreferencesRepository(this)
        } catch (_: Exception) {}

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F111A)
                ) {
                    AppRootContent(
                        emulatorCore = emulatorCore,
                        saveStateRepository = saveStateRepository,
                        preferencesRepository = preferencesRepository,
                        romLibraryRepository = romLibraryRepository
                    )
                }
            }
        }
    }
}

@Composable
fun AppRootContent(
    emulatorCore: EmulatorCore,
    saveStateRepository: SaveStateRepository?,
    preferencesRepository: PreferencesRepository?,
    romLibraryRepository: RomLibraryRepository
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var currentScreen by remember {
        mutableStateOf(if (Firebase.auth.currentUser != null) AppScreen.LIBRARY else AppScreen.LIBRARY)
    }
    var activeGame by remember { mutableStateOf<RetroGame?>(null) }
    var activeSkin by remember { mutableStateOf(ControllerSkin.CLASSIC_DMG) }

    // Listen to Firebase Auth state
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Load saved preferences if available
    LaunchedEffect(currentUser) {
        if (currentUser != null && preferencesRepository != null) {
            try {
                val prefs = preferencesRepository.observePreferences().firstOrNull()
                if (prefs != null) {
                    activeSkin = ControllerSkin.fromId(prefs.activeSkin)
                    emulatorCore.audioSynthesizer.setEnabled(prefs.audioEnabled)
                    emulatorCore.audioSynthesizer.setVolume(prefs.audioVolume / 100f)
                }
            } catch (_: Exception) {}
        }
    }

    when (currentScreen) {
        AppScreen.AUTH -> {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    currentScreen = AppScreen.LIBRARY
                },
                onContinueOffline = {
                    currentScreen = AppScreen.LIBRARY
                }
            )
        }

        AppScreen.LIBRARY -> {
            LibraryScreen(
                romLibraryRepository = romLibraryRepository,
                onGameSelected = { game ->
                    activeGame = game
                    currentScreen = AppScreen.EMULATOR
                },
                onOpenCloudVault = {
                    currentScreen = AppScreen.CLOUD_SYNC
                },
                onOpenSettings = {
                    currentScreen = AppScreen.SETTINGS
                }
            )
        }

        AppScreen.EMULATOR -> {
            activeGame?.let { game ->
                EmulatorScreen(
                    game = game,
                    emulatorCore = emulatorCore,
                    saveStateRepository = saveStateRepository,
                    romLibraryRepository = romLibraryRepository,
                    initialSkin = activeSkin,
                    onBackToLibrary = {
                        currentScreen = AppScreen.LIBRARY
                    }
                )
            } ?: run {
                currentScreen = AppScreen.LIBRARY
            }
        }

        AppScreen.CLOUD_SYNC -> {
            CloudSyncScreen(
                saveStateRepository = saveStateRepository,
                onBack = { currentScreen = AppScreen.LIBRARY },
                onNavigateToAuth = { currentScreen = AppScreen.AUTH }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                currentSkin = activeSkin,
                onSkinChanged = { skin -> activeSkin = skin },
                emulatorCore = emulatorCore,
                preferencesRepository = preferencesRepository,
                onBack = { currentScreen = AppScreen.LIBRARY }
            )
        }
    }
}
