package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScannedRomEntity::class], version = 1, exportSchema = false)
abstract class RetroForgeDatabase : RoomDatabase() {

    abstract fun romLibraryDao(): RomLibraryDao

    companion object {
        @Volatile
        private var INSTANCE: RetroForgeDatabase? = null

        fun getInstance(context: Context): RetroForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RetroForgeDatabase::class.java,
                    "retroforge_roms.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
