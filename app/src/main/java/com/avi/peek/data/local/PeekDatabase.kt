package com.avi.peek.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, MediaEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PeekDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun media(): MediaDao
    abstract fun searches(): SearchDao

    companion object {
        fun build(context: Context): PeekDatabase = Room.databaseBuilder(
            context.applicationContext,
            PeekDatabase::class.java,
            "peek.db",
        ).fallbackToDestructiveMigration().build()
    }
}
