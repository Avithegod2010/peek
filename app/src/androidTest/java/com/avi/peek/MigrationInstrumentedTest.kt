package com.avi.peek

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avi.peek.data.local.PeekDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room migration smoke tests.
 *
 * These need a device or emulator (`connectedDebugAndroidTest`). They are
 * written now so the suite is ready when hardware is available.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "peek-migration-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesVersionOneThroughFiveAndKeepsPrivateState() {
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE profiles (
                                username TEXT NOT NULL PRIMARY KEY,
                                pk TEXT, userId TEXT,
                                fullName TEXT NOT NULL, biography TEXT NOT NULL,
                                profilePicUrl TEXT,
                                isPrivate INTEGER NOT NULL, isVerified INTEGER NOT NULL,
                                followerCount INTEGER NOT NULL, followingCount INTEGER NOT NULL,
                                bioLinksJson TEXT NOT NULL, highlightsJson TEXT NOT NULL,
                                hasClips INTEGER NOT NULL, latestReelMedia INTEGER,
                                isFollowed INTEGER NOT NULL, followedAt INTEGER,
                                isPinned INTEGER NOT NULL, addedAt INTEGER NOT NULL,
                                lastFetchedAt INTEGER NOT NULL, lastViewedAt INTEGER,
                                lastError TEXT, source TEXT NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            CREATE TABLE media (
                                code TEXT NOT NULL PRIMARY KEY, pk TEXT,
                                username TEXT NOT NULL, userAvatar TEXT,
                                userFullName TEXT NOT NULL, userVerified INTEGER NOT NULL,
                                mediaType INTEGER NOT NULL, productType TEXT,
                                caption TEXT, displayUrl TEXT, videoUrl TEXT,
                                likeCount INTEGER, commentCount INTEGER,
                                takenAt INTEGER, fetchedAt INTEGER NOT NULL,
                                detailFetchedAt INTEGER, carouselCount INTEGER NOT NULL,
                                childrenJson TEXT, width INTEGER, height INTEGER,
                                accessibilityCaption TEXT, locationName TEXT,
                                audioTitle TEXT, audioArtist TEXT,
                                isLiked INTEGER NOT NULL, isSaved INTEGER NOT NULL,
                                savedAt INTEGER, isDownloaded INTEGER NOT NULL,
                                downloadedAt INTEGER, localImagePathsJson TEXT,
                                localVideoPath TEXT, sortIndex INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "INSERT INTO profiles (username, fullName, biography, isPrivate, " +
                                "isVerified, followerCount, followingCount, bioLinksJson, " +
                                "highlightsJson, hasClips, isFollowed, isPinned, addedAt, " +
                                "lastFetchedAt, source) VALUES ('secret', 'Secret', '', 1, 0, 0, 0, " +
                                "'[]', '[]', 0, 1, 0, 0, 0, 'manual')",
                        )
                        db.execSQL(
                            "INSERT INTO media (code, username, userFullName, userVerified, " +
                                "mediaType, fetchedAt, carouselCount, isLiked, isSaved, " +
                                "isDownloaded, sortIndex) VALUES ('m1', 'secret', '', 0, 1, 0, 0, " +
                                "0, 0, 0, 0)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

        val db = helper.writableDatabase
        try {
            PeekDatabase.MIGRATION_1_2.migrate(db)
            PeekDatabase.MIGRATION_2_3.migrate(db)
            PeekDatabase.MIGRATION_3_4.migrate(db)
            PeekDatabase.MIGRATION_4_5.migrate(db)

            assertTrue("postCount" in columns(db, "profiles"))
            assertTrue("nextCursor" in columns(db, "profiles"))
            assertTrue("isPrivate" in columns(db, "media"))

            val private = db.query("SELECT isPrivate FROM media WHERE code = 'm1'").use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(1, private)

            val commentColumns = columns(db, "comments")
            assertTrue("pk" in commentColumns)
            assertTrue("mediaCode" in commentColumns)
            assertTrue("parentPk" in commentColumns)
            assertTrue("text" in commentColumns)

            val mediaIndices = indices(db, "media")
            assertTrue("index_media_isPrivate" in mediaIndices)
            assertTrue("index_media_username_takenAt" in mediaIndices)
            val commentIndices = indices(db, "comments")
            assertTrue("index_comments_mediaCode" in commentIndices)
            assertTrue("index_comments_parentPk" in commentIndices)
        } finally {
            helper.close()
        }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun indices(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
}
