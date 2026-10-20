package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.data.DefaultGames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RomLibraryRepository(private val dao: RomLibraryDao) {

    val allRoms: Flow<List<ScannedRomEntity>> = dao.getAllRoms()

    fun getRomsBySystem(system: String): Flow<List<ScannedRomEntity>> {
        return if (system.equals("ALL", ignoreCase = true)) {
            dao.getAllRoms()
        } else {
            dao.getRomsBySystem(system.uppercase())
        }
    }

    suspend fun scanAndInsertUri(context: Context, uri: Uri): ScannedRomEntity? = withContext(Dispatchers.IO) {
        val entity = RomHeaderScanner.scanUri(context, uri)
        if (entity != null) {
            dao.insertRom(entity)
        }
        entity
    }

    suspend fun scanAndInsertMultipleUris(context: Context, uris: List<Uri>): List<ScannedRomEntity> = withContext(Dispatchers.IO) {
        val scanned = mutableListOf<ScannedRomEntity>()
        for (uri in uris) {
            val entity = RomHeaderScanner.scanUri(context, uri)
            if (entity != null) {
                scanned.add(entity)
            }
        }
        if (scanned.isNotEmpty()) {
            dao.insertRoms(scanned)
        }
        scanned
    }

    suspend fun deleteRom(id: String) = withContext(Dispatchers.IO) {
        dao.deleteRomById(id)
    }

    suspend fun recordPlayStats(id: String, addedPlayTimeSeconds: Long) = withContext(Dispatchers.IO) {
        dao.updatePlayStats(id, System.currentTimeMillis(), addedPlayTimeSeconds)
    }

    suspend fun seedBuiltInGamesIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.count() == 0) {
            val defaultEntities = DefaultGames.list.map { game ->
                val (pColor, sColor, key) = when (game.system) {
                    "GB" -> Triple(0xFF9BBB0F, 0xFF262626, "cover_gb")
                    "GBC" -> Triple(0xFF8E44AD, 0xFF2C3E50, "cover_gbc")
                    "GBA" -> Triple(0xFF2980B9, 0xFF1A5276, "cover_gba")
                    "NDS" -> Triple(0xFF16A085, 0xFF117A65, "cover_nds")
                    "N64" -> Triple(0xFFD35400, 0xFF962D00, "cover_n64")
                    else -> Triple(0xFF00E5FF, 0xFF1C1D24, "cover_default")
                }

                val genre = when (game.system) {
                    "GB" -> "Dungeon RPG"
                    "GBC" -> "Color Platformer"
                    "GBA" -> "Mode 7 Racer"
                    "NDS" -> "Touch Defense"
                    "N64" -> "3D Space Combat"
                    else -> "Retro Action"
                }

                ScannedRomEntity(
                    id = game.id,
                    title = game.title,
                    system = game.system,
                    fileName = "${game.id}.${game.system.lowercase()}",
                    fileUri = "builtin://${game.id}",
                    fileSize = when (game.system) {
                        "GB" -> 131072L // 128 KB
                        "GBC" -> 524288L // 512 KB
                        "GBA" -> 4194304L // 4 MB
                        "NDS" -> 16777216L // 16 MB
                        "N64" -> 12582912L // 12 MB
                        else -> 1048576L
                    },
                    gameCode = when (game.system) {
                        "GB" -> "DMG-PQT-USA"
                        "GBC" -> "CGB-CRY-USA"
                        "GBA" -> "AGB-ASW-USA"
                        "NDS" -> "NTR-DTA-USA"
                        "N64" -> "NUS-NPLY-USA"
                        else -> "RETRO-001"
                    },
                    publisher = game.developer,
                    genre = genre,
                    releaseYear = game.releaseYear,
                    cartridgeMapper = when (game.system) {
                        "GB" -> "MBC1 + Battery"
                        "GBC" -> "MBC5 Color"
                        "GBA" -> "32-Bit Linear Flash"
                        "NDS" -> "Dual-Core Card"
                        "N64" -> "Reality Coprocessor 3D"
                        else -> "Standard"
                    },
                    headerChecksum = "VALID",
                    boxArtCoverKey = key,
                    boxArtPrimaryColor = pColor,
                    boxArtSecondaryColor = sColor,
                    iconEmoji = game.iconEmoji,
                    isBuiltIn = true,
                    scannedAt = System.currentTimeMillis()
                )
            }
            dao.insertRoms(defaultEntities)
        }
    }
}
