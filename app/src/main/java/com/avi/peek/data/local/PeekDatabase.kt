package com.avi.peek.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        MediaEntity::class,
        SearchHistoryEntity::class,
        CommentEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class PeekDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun media(): MediaDao
    abstract fun searches(): SearchDao
    abstract fun comments(): CommentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN postCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN hasMorePosts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN nextCursor TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE media SET isPrivate = 1 WHERE username IN " +
                        "(SELECT username FROM profiles WHERE isPrivate = 1)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS comments (
                        pk TEXT NOT NULL PRIMARY KEY,
                        mediaCode TEXT NOT NULL,
                        parentPk TEXT,
                        username TEXT NOT NULL,
                        avatarUrl TEXT,
                        text TEXT NOT NULL,
                        createdAt INTEGER,
                        likeCount INTEGER NOT NULL DEFAULT 0,
                        verified INTEGER NOT NULL DEFAULT 0,
                        fetchedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comments_mediaCode ON comments(mediaCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comments_parentPk ON comments(parentPk)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_isPrivate ON media(isPrivate)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_username_takenAt " +
                        "ON media(username, takenAt)",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_profiles_lastViewedAt ON profiles(lastViewedAt)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_profiles_isFollowed_isPinned_latestReelMedia " +
                        "ON profiles(isFollowed, isPinned, latestReelMedia)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_isLiked ON media(isLiked)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_downloadedAt ON media(downloadedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_isSaved_savedAt ON media(isSaved, savedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_isPrivate_takenAt ON media(isPrivate, takenAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_audioTitle ON media(audioTitle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_productType ON media(productType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_searches_searchedAt ON searches(searchedAt)")
            }
        }

        fun build(context: Context): PeekDatabase = Room.databaseBuilder(
            context.applicationContext,
            PeekDatabase::class.java,
            "peek.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }
}
