package com.avi.peek

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.local.SecureMediaDao
import com.avi.peek.data.local.SecureProfileDao
import com.avi.peek.data.local.SessionStore
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.repo.AccountManager
import com.avi.peek.data.repo.DownloadStore
import com.avi.peek.data.repo.PeekRepository
import com.avi.peek.data.repo.RequestBudget
import com.avi.peek.data.repo.VaultStore
import com.avi.peek.util.NetworkMonitor
import com.avi.peek.util.PrivateAccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository-level private-state filtering. Requires a device/emulator because
 * Room and Android Keystore are involved.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryVisibilityInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: PeekDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repository: PeekRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PeekDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsStore(context)
        val client = GuestInstagramClient()
        repository = PeekRepository(
            context = context,
            db = db,
            client = client,
            settings = settings,
            network = NetworkMonitor(context),
            downloads = DownloadStore(context),
            accountManager = AccountManager(SessionStore(context), client),
            requestBudget = RequestBudget(context, settings),
            vault = VaultStore(context, requireUserAuth = false),
        )
    }

    @After
    fun tearDown() = runBlocking {
        PrivateAccess.setAutoLockMinutes(0)
        PrivateAccess.hide()
        settings.setPrivateContentHidden(false)
        db.close()
    }

    @Test
    fun privateFeedAndMediaStayHiddenUntilRevealed() = runBlocking {
        SecureProfileDao(db.profiles()).upsert(
            ProfileEntity(username = "secret", isPrivate = true, isFollowed = true),
        )
        SecureMediaDao(db.media()).upsert(
            MediaEntity(code = "m1", username = "secret", isPrivate = true, caption = "hidden"),
        )
        PrivateAccess.setAutoLockMinutes(0)
        settings.setPrivateContentHidden(true)
        PrivateAccess.hide()

        assertTrue(repository.privateFeed.first().isEmpty())
        assertTrue(repository.mediaByCodes(listOf("m1")).first().isEmpty())

        PrivateAccess.reveal()
        assertEquals(1, repository.privateFeed.first().size)
        assertEquals("hidden", repository.mediaByCodes(listOf("m1")).first().single().caption)
    }
}
