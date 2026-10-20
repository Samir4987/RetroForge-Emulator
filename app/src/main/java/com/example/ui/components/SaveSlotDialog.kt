package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.SaveStateDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaveSlotDialog(
    gameTitle: String,
    systemName: String,
    existingSaves: List<SaveStateDto>,
    onSaveToSlot: (slot: Long) -> Unit,
    onLoadFromSlot: (dto: SaveStateDto) -> Unit,
    onDeleteSlot: (saveId: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .testTag("save_slot_dialog")
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E202B),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Save State Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$gameTitle ($systemName)",
                            fontSize = 12.sp,
                            color = Color(0xFF90CAF9)
                        )
                    }
                    Surface(
                        color = Color(0xFF2C3246),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "CLOUD SYNC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D8FF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 5 dedicated slots (0 to 4)
                    items((0L..4L).toList()) { slotIndex ->
                        val existing = existingSaves.firstOrNull { it.slot == slotIndex }
                        SlotItemCard(
                            slotIndex = slotIndex,
                            existingSave = existing,
                            onSave = { onSaveToSlot(slotIndex) },
                            onLoad = { existing?.let { onLoadFromSlot(it) } },
                            onDelete = { existing?.let { onDeleteSlot(it.id) } }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_save_dialog_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33384C))
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SlotItemCard(
    slotIndex: Long,
    existingSave: SaveStateDto?,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbnailBitmap = remember(existingSave?.thumbnailBase64) {
        if (!existingSave?.thumbnailBase64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(existingSave?.thumbnailBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        } else null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("slot_card_$slotIndex")
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF3A3F55), RoundedCornerShape(10.dp)),
        color = Color(0xFF252837)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview or Placeholder
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF151722)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = "Slot $slotIndex Preview",
                        modifier = Modifier.size(width = 64.dp, height = 48.dp)
                    )
                } else {
                    Text(
                        text = "EMPTY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Slot ${slotIndex + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                if (existingSave != null) {
                    val dateStr = existingSave.createdAt?.toDate()?.let {
                        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                    } ?: "Saved"
                    Text(
                        text = "$dateStr • ${existingSave.playTimeSeconds}s played",
                        fontSize = 11.sp,
                        color = Color(0xFFB0B3C7)
                    )
                } else {
                    Text(
                        text = "No saved state",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Save Button
                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("save_slot_${slotIndex}_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save to Slot $slotIndex",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Load Button (if existing)
                if (existingSave != null) {
                    IconButton(
                        onClick = onLoad,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("load_slot_${slotIndex}_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Load from Slot $slotIndex",
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_slot_${slotIndex}_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Slot $slotIndex",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
