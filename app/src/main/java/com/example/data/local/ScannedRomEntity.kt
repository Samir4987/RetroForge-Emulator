package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.RetroGame

@Entity(tableName = "rom_library")
data class ScannedRomEntity(
    @PrimaryKey val id: String,
    val title: String,
    val system: String, // "GB", "GBC", "GBA", "NDS", "N64"
    val fileName: String,
    val fileUri: String,
    val fileSize: Long,
    val gameCode: String,
    val publisher: String,
    val genre: String,
    val releaseYear: String,
    val cartridgeMapper: String,
    val headerChecksum: String,
    val boxArtCoverKey: String,
    val boxArtPrimaryColor: Long,
    val boxArtSecondaryColor: Long,
    val iconEmoji: String,
    val isBuiltIn: Boolean = false,
    val scannedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val playTimeSeconds: Long = 0L
) {
    fun toRetroGame(romBytes: ByteArray? = null): RetroGame {
        return RetroGame(
            id = id,
            title = title,
            system = system,
            developer = publisher,
            description = "$genre • $cartridgeMapper • ${(fileSize / 1024).coerceAtLeast(1)} KB",
            isBuiltIn = isBuiltIn,
            romFileName = fileName,
            romBytes = romBytes,
            iconEmoji = iconEmoji,
            releaseYear = releaseYear
        )
    }
}
