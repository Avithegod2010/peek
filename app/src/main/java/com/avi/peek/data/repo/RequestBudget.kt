package com.avi.peek.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GuestErrorKind
import com.avi.peek.data.remote.GuestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.peekRequestBudget: DataStore<Preferences> by preferencesDataStore("peek_request_budget")

data class RequestBudgetState(
    val usedThisHour: Int = 0,
    val windowStartedAt: Long = 0L,
    val windowResetsAt: Long = 0L,
    val pauseUntil: Long = 0L,
    val challengeUntil: Long = 0L,
    val backoffLevel: Int = 0,
    val lastError: String? = null,
    val lastSuccessAt: Long = 0L,
) {
    val paused: Boolean get() = pauseUntil > System.currentTimeMillis()
}

/**
 * A small local request budget for Instagram calls. It is intentionally
 * conservative: hourly caps, exponential backoff after rate limits, and
 * optional quiet hours for background sync.
 */
class RequestBudget(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private object Keys {
        val WINDOW_START = longPreferencesKey("window_start")
        val COUNT = intPreferencesKey("count")
        val PAUSE_UNTIL = longPreferencesKey("pause_until")
        val CHALLENGE_UNTIL = longPreferencesKey("challenge_until")
        val BACKOFF = intPreferencesKey("backoff")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_SUCCESS = longPreferencesKey("last_success")
    }

    private val prefs = context.peekRequestBudget.data
        .catch { emit(emptyPreferences()) }

    val state: Flow<RequestBudgetState> = prefs.map { read(it) }

    suspend fun snapshot(): RequestBudgetState = state.first()

    suspend fun acquire(background: Boolean = false) {
        val now = System.currentTimeMillis()
        val current = snapshot()
        if (current.pauseUntil > now) {
            throw GuestException(
                GuestErrorKind.RATE_LIMITED,
                "Peek is pausing requests for ${remainingMinutes(current.pauseUntil)} more minutes. Open the rate-limit dashboard to resume.",
            )
        }
        if (background && current.challengeUntil > now) {
            throw GuestException(
                GuestErrorKind.RATE_LIMITED,
                "Instagram challenged the session. Background requests are paused for ${remainingMinutes(current.challengeUntil)} more minutes.",
            )
        }

        val prefsNow = settings.settings.first()
        if (background && prefsNow.quietHoursEnabled && inQuietHours(prefsNow.quietStartHour, prefsNow.quietEndHour)) {
            throw GuestException(
                GuestErrorKind.RATE_LIMITED,
                "Quiet hours are on. Background requests wait until ${prefsNow.quietEndHour}:00.",
            )
        }

        val windowStart = if (now - current.windowStartedAt >= HOUR_MS || current.windowStartedAt <= 0L) {
            now
        } else {
            current.windowStartedAt
        }
        val used = if (windowStart != current.windowStartedAt) 0 else current.usedThisHour
        if (used >= prefsNow.requestBudgetPerHour) {
            throw GuestException(
                GuestErrorKind.RATE_LIMITED,
                "The hourly request budget (${prefsNow.requestBudgetPerHour}) is used up. Try again after ${clock(windowStart + HOUR_MS)}.",
            )
        }
        context.peekRequestBudget.edit {
            it[Keys.WINDOW_START] = windowStart
            it[Keys.COUNT] = used + 1
        }
    }

    suspend fun recordSuccess() {
        context.peekRequestBudget.edit {
            it[Keys.LAST_SUCCESS] = System.currentTimeMillis()
            it[Keys.LAST_ERROR] = ""
            it[Keys.PAUSE_UNTIL] = 0L
            it[Keys.CHALLENGE_UNTIL] = 0L
            it[Keys.BACKOFF] = 0
        }
    }

    suspend fun recordChallenge(message: String) {
        context.peekRequestBudget.edit {
            it[Keys.LAST_ERROR] = message
            it[Keys.CHALLENGE_UNTIL] = System.currentTimeMillis() + 10 * 60_000L
        }
    }

    suspend fun recordRateLimit(message: String) {
        val current = snapshot()
        val level = (current.backoffLevel + 1).coerceAtMost(BACKOFF_MINUTES.size)
        val waitMinutes = BACKOFF_MINUTES[level - 1]
        context.peekRequestBudget.edit {
            it[Keys.LAST_ERROR] = message
            it[Keys.BACKOFF] = level
            it[Keys.PAUSE_UNTIL] = System.currentTimeMillis() + waitMinutes * 60_000L
        }
    }

    suspend fun clearPause() {
        context.peekRequestBudget.edit {
            it[Keys.PAUSE_UNTIL] = 0L
            it[Keys.CHALLENGE_UNTIL] = 0L
            it[Keys.BACKOFF] = 0
            it[Keys.LAST_ERROR] = ""
        }
    }

    suspend fun resetWindow() {
        context.peekRequestBudget.edit {
            it[Keys.WINDOW_START] = 0L
            it[Keys.COUNT] = 0
        }
    }

    private fun read(prefs: Preferences): RequestBudgetState {
        val now = System.currentTimeMillis()
        val rawStart = prefs[Keys.WINDOW_START] ?: 0L
        val active = rawStart > 0L && now - rawStart < HOUR_MS
        val windowStart = if (active) rawStart else 0L
        return RequestBudgetState(
            usedThisHour = if (active) prefs[Keys.COUNT] ?: 0 else 0,
            windowStartedAt = windowStart,
            windowResetsAt = if (windowStart > 0L) windowStart + HOUR_MS else 0L,
            pauseUntil = prefs[Keys.PAUSE_UNTIL] ?: 0L,
            challengeUntil = prefs[Keys.CHALLENGE_UNTIL] ?: 0L,
            backoffLevel = prefs[Keys.BACKOFF] ?: 0,
            lastError = prefs[Keys.LAST_ERROR]?.takeIf { it.isNotBlank() },
            lastSuccessAt = prefs[Keys.LAST_SUCCESS] ?: 0L,
        )
    }

    fun inQuietHours(startHour: Int, endHour: Int): Boolean {
        if (startHour == endHour) return false
        val now = LocalTime.now()
        val start = LocalTime.of(startHour.coerceIn(0, 23), 0)
        val end = LocalTime.of(endHour.coerceIn(0, 23), 0)
        return if (start < end) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
    }

    private fun remainingMinutes(until: Long): Long =
        ((until - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)

    private fun clock(epochMs: Long): String {
        val time = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        val BACKOFF_MINUTES = longArrayOf(5, 15, 60, 360)
    }
}
