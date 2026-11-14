package com.avi.peek

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avi.peek.data.local.SessionCookie
import com.avi.peek.data.local.SessionStore
import com.avi.peek.data.local.StoredSession
import com.avi.peek.data.repo.VaultStore
import com.avi.peek.util.PrivateAccess
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecurityInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vault = VaultStore(context)

    @After
    fun cleanup() {
        runCatching { vault.purge("instrumented-test") }
    }

    @Test
    fun sessionCookiesAreEncryptedAndRestored() = runBlocking {
        val store = SessionStore(context)
        store.clear()
        store.save(
            StoredSession(
                cookies = listOf(SessionCookie("sessionid", "test-value")),
                username = "tester",
            ),
        )
        val restored = store.load()
        assertNotNull(restored)
        assertTrue(restored!!.cookies.any { it.name == "sessionid" && it.value == "test-value" })
        store.clear()
    }

    @Test
    fun vaultSealsAndOpensAFile() = runBlocking {
        val code = "instrumented-test"
        val root = File(context.cacheDir, "vault-downloads").apply { mkdirs() }
        val sourceDir = File(root, code).apply { mkdirs() }
        val source = File(sourceDir, "image_0.jpg")
        source.writeText("vault payload")
        vault.seal(listOf(code), root)
        assertFalse(source.exists())
        assertTrue(vault.hasSealed(code))
        vault.open(listOf(code), root)
        assertTrue(source.exists())
        assertTrue(source.readText() == "vault payload")
        source.delete()
        sourceDir.delete()
        root.delete()
    }

    @Test
    fun privateAccessLocksAndReveals() {
        PrivateAccess.hide()
        assertFalse(PrivateAccess.isRevealed())
        PrivateAccess.reveal()
        assertTrue(PrivateAccess.isRevealed())
        PrivateAccess.hide()
    }
}
