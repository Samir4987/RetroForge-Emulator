package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.model.SaveStateDto
import com.example.data.repository.SaveStateRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    saveStateRepository: SaveStateRepository?,
    onBack: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser = Firebase.auth.currentUser
    val cloudSaves = remember { mutableStateListOf<SaveStateDto>() }
    var isLoading by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    fun refreshCloudSaves() {
        if (currentUser != null && saveStateRepository != null) {
            coroutineScope.launch {
                isLoading = true
                try {
                    val list = saveStateRepository.observeAllSaveStates().firstOrNull()
                    cloudSaves.clear()
                    if (list != null) {
                        cloudSaves.addAll(list)
                    }
                } catch (_: Exception) {}
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentUser) {
        refreshCloudSaves()
    }

    // JSON file picker to restore saves
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val count = restoreSavesFromUri(context, uri, saveStateRepository)
                Toast.makeText(context, "Restored $count save states", Toast.LENGTH_SHORT).show()
                refreshCloudSaves()
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag("cloud_sync_screen"),
        containerColor = Color(0xFF0F111A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cloud Sync & Backup",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("button_back_from_cloud")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshCloudSaves() }, modifier = Modifier.testTag("button_refresh_cloud")) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Cloud",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141724))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // User Auth Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_status_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2D))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (currentUser != null) Color(0xFF00E5FF) else Color(0xFF33384C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (currentUser != null) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (currentUser != null) (currentUser.displayName ?: "Google User") else "Local Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (currentUser != null) (currentUser.email ?: "Cloud Sync Active") else "Signed in as Guest",
                                fontSize = 12.sp,
                                color = Color(0xFFB0B3C7)
                            )
                        }
                    }

                    if (currentUser != null) {
                        OutlinedButton(
                            onClick = {
                                Firebase.auth.signOut()
                                coroutineScope.launch {
                                    try {
                                        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                                    } catch (_: Exception) {}
                                    cloudSaves.clear()
                                }
                            },
                            modifier = Modifier.testTag("button_sign_out"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                        ) {
                            Text("Sign Out", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onNavigateToAuth,
                            modifier = Modifier.testTag("button_sign_in_nav"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Sign In", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup & Restore Actions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_restore_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Backup & Restore Vault",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Export your save states to a portable file or restore game saves from Google Drive / storage.",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B3C7),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                exportSavesToFile(context, cloudSaves)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_export_backup"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3246))
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Backup", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                restoreFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_import_backup"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore File", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Save States Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cloud Save States (${cloudSaves.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF00E5FF))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (cloudSaves.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentUser == null) "Sign in to view and synchronize cloud saves" else "No cloud saves found. Use 'Save State' while playing a game!",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cloudSaves, key = { it.id }) { save ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF2E3246), RoundedCornerShape(10.dp)),
                            color = Color(0xFF1E2130)
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
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = save.system,
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = save.gameTitle,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    val dateStr = save.createdAt?.toDate()?.let {
                                        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(it)
                                    } ?: "Synced"
                                    Text(
                                        text = "Slot ${save.slot + 1} • $dateStr",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB0B3C7)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            saveStateRepository?.deleteSaveState(save.id)
                                            cloudSaves.remove(save)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Cloud Save",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportSavesToFile(context: Context, saves: List<SaveStateDto>) {
    try {
        val rootArray = JSONArray()
        saves.forEach { save ->
            val obj = JSONObject().apply {
                put("id", save.id)
                put("gameId", save.gameId)
                put("gameTitle", save.gameTitle)
                put("system", save.system)
                put("slot", save.slot)
                put("stateData", save.stateData)
                put("thumbnailBase64", save.thumbnailBase64)
                put("playTimeSeconds", save.playTimeSeconds)
            }
            rootArray.put(obj)
        }

        val jsonString = rootArray.toString(2)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, jsonString)
            putExtra(Intent.EXTRA_SUBJECT, "RetroForge_SaveStates_Backup.json")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Save States Backup")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun restoreSavesFromUri(
    context: Context,
    uri: Uri,
    repository: SaveStateRepository?
): Int {
    if (repository == null) return 0
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val text = inputStream?.bufferedReader().use { it?.readText() } ?: return 0
        val rootArray = JSONArray(text)
        var restoredCount = 0

        val uid = Firebase.auth.currentUser?.uid ?: "local_user"
        for (i in 0 until rootArray.length()) {
            val obj = rootArray.getJSONObject(i)
            val dto = SaveStateDto(
                id = obj.optString("id", "${obj.getString("gameId")}_slot_${obj.getLong("slot")}"),
                userId = uid,
                gameId = obj.getString("gameId"),
                gameTitle = obj.optString("gameTitle", "Restored Game"),
                system = obj.optString("system", "GB"),
                slot = obj.optLong("slot", 0L),
                stateData = obj.getString("stateData"),
                thumbnailBase64 = obj.optString("thumbnailBase64", ""),
                playTimeSeconds = obj.optLong("playTimeSeconds", 0L)
            )
            repository.saveState(dto)
            restoredCount++
        }
        restoredCount
    } catch (_: Exception) {
        0
    }
}
