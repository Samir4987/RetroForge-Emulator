package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RomLibraryDao {

    @Query("SELECT * FROM rom_library ORDER BY isBuiltIn DESC, scannedAt DESC")
    fun getAllRoms(): Flow<List<ScannedRomEntity>>

    @Query("SELECT * FROM rom_library WHERE system = :system ORDER BY isBuiltIn DESC, scannedAt DESC")
    fun getRomsBySystem(system: String): Flow<List<ScannedRomEntity>>

    @Query("SELECT * FROM rom_library WHERE id = :id LIMIT 1")
    fun getRomById(id: String): Flow<ScannedRomEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRom(rom: ScannedRomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoms(roms: List<ScannedRomEntity>)

    @Query("DELETE FROM rom_library WHERE id = :id")
    suspend fun deleteRomById(id: String)

    @Query("UPDATE rom_library SET lastPlayedAt = :lastPlayed, playTimeSeconds = playTimeSeconds + :addedPlayTime WHERE id = :id")
    suspend fun updatePlayStats(id: String, lastPlayed: Long, addedPlayTime: Long)

    @Query("SELECT COUNT(*) FROM rom_library")
    suspend fun count(): Int
}
