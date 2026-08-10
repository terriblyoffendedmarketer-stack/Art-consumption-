package com.artconsumption.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArtPost::class], version = 1)
abstract class ArtDatabase : RoomDatabase() {
    abstract fun artDao(): ArtDao

    companion object {
        @Volatile
        private var instance: ArtDatabase? = null

        fun get(context: Context): ArtDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArtDatabase::class.java,
                    "art_consumption.db"
                ).build().also { instance = it }
            }
        }
    }
}
