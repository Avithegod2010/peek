package com.avi.peek

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avi.peek.data.local.CommentEntity
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.local.SearchHistoryEntity
import com.avi.peek.data.local.SecureCommentDao
import com.avi.peek.data.local.SecureMediaDao
import com.avi.peek.data.local.SecureProfileDao
import com.avi.peek.data.local.SecureSearchDao
import com.avi.peek.util.PrivateAccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encryption and visibility tests for the secure DAO layer.
 *
 * Requires a device or emulator because Android Keystore is unavailable in
 * plain JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class SecureDaoInstrumentedTest {
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
        PrivateAccess.hide()
    }

    @Test
    fun mediaMetadataIsCiphertextAtRestAndPlaintextWhenRead() = runBlocking {
        val secure = SecureMediaDao(db.media())
        secure.upsert(
            MediaEntity(
                code = "media-1",
                username = "tester",
                caption = "private caption",
                displayUrl = "https://cdn.example/image.jpg",
                locationName = "Somewhere",
            ),
        )
        val raw = db.media().get("media-1")!!
        assertTrue(raw.caption!!.startsWith("enc:v1:"))
        assertTrue(raw.displayUrl!!.startsWith("enc:v1:"))
        assertFalse(raw.caption!!.contains("private caption"))
        assertFalse(raw.displayUrl!!.contains("cdn.example"))

        val read = secure.get("media-1")!!
        assertEquals("private caption", read.caption)
        assertEquals("https://cdn.example/image.jpg", read.displayUrl)
        assertEquals("Somewhere", read.locationName)
    }

    @Test
    fun profileMetadataIsEncrypted() = runBlocking {
        val secure = SecureProfileDao(db.profiles())
        secure.upsert(
            ProfileEntity(
                username = "tester",
                fullName = "Test Person",
                biography = "A private bio",
                profilePicUrl = "https://cdn.example/avatar.jpg",
            ),
        )
        val raw = db.profiles().get("tester")!!
        assertTrue(raw.fullName.startsWith("enc:v1:"))
        assertTrue(raw.biography.startsWith("enc:v1:"))
        assertFalse(raw.biography.contains("private bio"))

        val read = secure.get("tester")!!
        assertEquals("Test Person", read.fullName)
        assertEquals("A private bio", read.biography)
    }

    @Test
    fun commentsAreEncryptedButThreadsStayReadable() = runBlocking {
        val secure = SecureCommentDao(db.comments())
        secure.upsertAll(
            listOf(
                CommentEntity(pk = "c1", mediaCode = "m1", username = "alice", text = "top secret"),
                CommentEntity(pk = "c2", mediaCode = "m1", parentPk = "c1", username = "bob", text = "reply"),
            ),
        )
        val raw = db.comments().forMedia("m1")
        assertTrue(raw.all { it.text.startsWith("enc:v1:") })
        assertFalse(raw.any { it.text.contains("top secret") })

        val read = secure.forMedia("m1")
        assertEquals(2, read.size)
        assertEquals("top secret", read.first { it.pk == "c1" }.text)
        assertEquals("c1", read.first { it.pk == "c2" }.parentPk)
    }

    @Test
    fun searchHistoryIsEncryptedAndDeduplicatedCaseInsensitively() = runBlocking {
        val secure = SecureSearchDao(db.searches())
        secure.add(SearchHistoryEntity(query = "nasa", searchedAt = 1L))
        secure.add(SearchHistoryEntity(query = "NASA", searchedAt = 2L))

        val raw = db.searches().allOnce()
        assertEquals(1, raw.size)
        assertTrue(raw[0].query.startsWith("enc:v1:"))
        assertFalse(raw[0].query.contains("NASA", ignoreCase = true))

        val read = secure.observeRecent().first()
        assertEquals(1, read.size)
        assertEquals("NASA", read[0].query)
    }

    @Test
    fun lockingInvokesTheSealCallbackOnlyWhenRevealed() {
        var locked = 0
        val previous = PrivateAccess.onLocked
        PrivateAccess.onLocked = { locked++ }
        try {
            PrivateAccess.setAutoLockMinutes(0)
            PrivateAccess.hide()
            assertEquals(0, locked)
            PrivateAccess.reveal()
            PrivateAccess.hide()
            assertEquals(1, locked)
        } finally {
            PrivateAccess.onLocked = previous
            PrivateAccess.setAutoLockMinutes(2)
        }
    }
}
