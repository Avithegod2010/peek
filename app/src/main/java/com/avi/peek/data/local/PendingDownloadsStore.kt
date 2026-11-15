package com.avi.peek.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.peekDownloadData: DataStore<Preferences> by preferencesDataStore(name = "peek_downloads")

/**
 * Persists the codes in the download queue so a process death does not lose
 * the user's pending downloads. Partial bytes are not kept; a restored code
 * restarts that post's download.
 */
class PendingDownloadsStore(private val context: Context) {
    private val key = stringSetPreferencesKey("pending_codes")

    val pending: Flow<Set<String>> = context.peekDownloadData.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[key].orEmpty() }

    suspend fun add(code: String) {
        context.peekDownloadData.edit { prefs ->
            prefs[key] = prefs[key].orEmpty() + code
        }
    }

    suspend fun remove(code: String) {
        context.peekDownloadData.edit { prefs ->
            prefs[key] = prefs[key].orEmpty() - code
        }
    }

    suspend fun clear() {
        context.peekDownloadData.edit { prefs -> prefs.remove(key) }
    }
}
