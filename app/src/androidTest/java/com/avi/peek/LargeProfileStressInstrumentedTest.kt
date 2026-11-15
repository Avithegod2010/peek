package com.avi.peek

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Large-profile harness. Requires a device/emulator because it uses Room.
 * It writes raw rows (not the secure DAO) so the test stays fast; the point is
 * to exercise the query planner and pagination for a 10k-post profile.
 */
@RunWith(AndroidJUnit4::class)
class LargeProfileStressInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: PeekDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PeekDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test(timeout = 30_000)
    fun tenThousandPostProfileQueriesUseAnIndexAndReturnAllRows() = runBlocking {
        val items = (0 until 10_000).map { index ->
            MediaEntity(
                code = "stress-$index",
                username = "stress-${index % 10}",
                caption = null,
                takenAt = index.toLong(),
                sortIndex = index,
            )
        }
        db.media().upsertAll(items)

        val page = db.media().forProfile("stress-0")
        assertEquals(1_000, page.size)
        assertEquals("stress-9990", page.first().code)

        val plan = db.query(
            "EXPLAIN QUERY PLAN SELECT * FROM media WHERE username = ?",
            arrayOf("stress-0"),
        ).use { cursor ->
            buildString {
                while (cursor.moveToNext()) {
                    append(cursor.getString(cursor.getColumnIndex("detail"))).append('\n')
                }
            }
        }
        assertTrue("Expected an index in query plan, got:\n$plan", plan.contains("INDEX", ignoreCase = true))
    }
}
