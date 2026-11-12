package com.avi.peek.data.repo

import com.avi.peek.data.local.AccountState
import com.avi.peek.data.local.SessionCookie
import com.avi.peek.data.local.SessionHealth
import com.avi.peek.data.local.SessionStore
import com.avi.peek.data.local.StoredSession
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.util.PrivateAccess
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
            health = SessionHealth.UNKNOWN,
        )
    }

    suspend fun connectManual(raw: String): AccountState {
        val value = raw.trim()
        if (value.isBlank()) throw IllegalArgumentException("Paste a session ID or cookie header first")
        val looksLikeHeader = value.contains(';') ||
            Regex("""(^|;)\s*(sessionid|csrftoken|ds_user_id|mid|ig_did)\s*=""")
                .containsMatchIn(value)
        val cookies = if (looksLikeHeader) {
            value.split(';').map { it.trim() }.filter { it.contains('=') }
        } else {
            listOf("sessionid=$value")
        }
        return connect(cookies)
    }

    suspend fun disconnect() {
        client.clearSession()
        store.clear()
        PrivateAccess.hide()
        restored = true
    }

    suspend fun refreshIdentity(): Boolean {
        ensureRestored()
        if (!client.hasSession()) return false
        val viewer = runCatching { client.resolveViewer() }.getOrNull() ?: return false
        val now = System.currentTimeMillis()
        store.update {
            it.copy(
                username = viewer.username ?: it.username,
                userId = viewer.userId ?: it.userId,
                lastVerifiedAt = now,
                lastSuccessAt = now,
                expiredAt = 0L,
                lastError = null,
            )
        }
        return true
    }

    suspend fun markSuccess() {
        if (!client.hasSession()) return
        val now = System.currentTimeMillis()
        store.update {
            it.copy(
                lastSuccessAt = now,
                // A successful public request does not prove that private
                // access works again. Only an explicit verification does.
                lastVerifiedAt = if (it.expiredAt > 0L) it.lastVerifiedAt else now,
            )
        }
    }

    suspend fun markExpired(reason: String) {
        if (!client.hasSession()) return
        store.update {
            it.copy(
                expiredAt = System.currentTimeMillis(),
                lastError = reason,
            )
        }
    }

    suspend fun audit(): SessionAudit {
        val stored = store.load()
        val state = store.snapshot()
        return SessionAudit(
            state = state,
            cookieNames = stored?.cookies?.map { it.name }?.distinct()?.sorted().orEmpty(),
            createdAt = stored?.savedAt ?: 0L,
            lastVerifiedAt = stored?.lastVerifiedAt ?: 0L,
            lastSuccessAt = stored?.lastSuccessAt ?: 0L,
            expiredAt = stored?.expiredAt ?: 0L,
            lastError = stored?.lastError,
        )
    }
}

data class SessionAudit(
    val state: AccountState,
    val cookieNames: List<String>,
    val createdAt: Long,
    val lastVerifiedAt: Long,
    val lastSuccessAt: Long,
    val expiredAt: Long,
    val lastError: String?,
)
