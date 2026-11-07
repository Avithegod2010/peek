package com.avi.peek.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, MediaEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PeekDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun media(): MediaDao
    abstract fun searches(): SearchDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN postCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN hasMorePosts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN nextCursor TEXT")
            }
        }

        fun build(context: Context): PeekDatabase = Room.databaseBuilder(
            context.applicationContext,
            PeekDatabase::class.java,
            "peek.db",
        ).addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
}
