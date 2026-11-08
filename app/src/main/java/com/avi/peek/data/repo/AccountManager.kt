package com.avi.peek.data.repo

import com.avi.peek.data.local.AccountState
import com.avi.peek.data.local.SessionCookie
import com.avi.peek.data.local.SessionStore
import com.avi.peek.data.local.StoredSession
import com.avi.peek.data.remote.GuestInstagramClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the optional Instagram web session. The default guest reader is never
 * disabled; this only adds cookies when the user explicitly signs in.
 */
class AccountManager(
    private val store: SessionStore,
    private val client: GuestInstagramClient,
) {
    private val restoreMutex = Mutex()

    @Volatile
    private var restored = false

    val state: Flow<AccountState> = store.state

    suspend fun ensureRestored() {
        if (restored) return
        restoreMutex.withLock {
            if (restored) return
            val session = runCatching { store.load() }.getOrNull()
            if (session != null && session.cookies.isNotEmpty()) {
                client.restoreSession(session.cookies.map { it.name to it.value })
            }
            restored = true
        }
    }

    suspend fun connect(rawCookies: List<String>): AccountState {
        val parsed = rawCookies
            .flatMap { raw -> raw.split(';') }
            .mapNotNull { part ->
                val trimmed = part.trim()
                val index = trimmed.indexOf('=')
                if (index <= 0) null
                else SessionCookie(
                    name = trimmed.substring(0, index).trim(),
                    value = trimmed.substring(index + 1).trim(),
                )
            }
            .filter { it.name.isNotBlank() && it.value.isNotBlank() }
            .distinctBy { it.name }

        if (parsed.none { it.name == "sessionid" && it.value.isNotBlank() }) {
            throw IllegalArgumentException("Instagram didn't finish signing in")
        }

        client.restoreSession(parsed.map { it.name to it.value })
        val session = StoredSession(
            cookies = parsed,
            username = null,
            userId = client.sessionUserId(),
            savedAt = System.currentTimeMillis(),
        )
        try {
            store.save(session)
        } catch (e: Exception) {
            client.clearSession()
            throw e
        }
        restored = true
        return AccountState(
            signedIn = true,
            username = session.username,
            userId = session.userId,
            savedAt = session.savedAt,
        )
    }

    suspend fun disconnect() {
        client.clearSession()
        store.clear()
        restored = true
    }

    suspend fun refreshIdentity() {
        ensureRestored()
        if (!client.hasSession()) return
        val viewer = runCatching { client.resolveViewer() }.getOrNull() ?: return
        val existing = store.load() ?: return
        store.save(
            existing.copy(
                username = viewer.username ?: existing.username,
                userId = viewer.userId ?: existing.userId,
                savedAt = existing.savedAt,
            ),
        )
    }
}
