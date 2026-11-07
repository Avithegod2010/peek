package com.avi.peek.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.peekSearchData: DataStore<Preferences> by preferencesDataStore(name = "peek_search")

@Serializable
data class SavedSearch(
    val query: String,
    val targetUsername: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

class SearchStore(private val context: Context) {
    private val key = stringPreferencesKey("saved_searches")
    private val json = Json { ignoreUnknownKeys = true }

    val saved: Flow<List<SavedSearch>> = context.peekSearchData.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            prefs[key]?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(SavedSearch.serializer()), raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(query: String, targetUsername: String? = null) {
        val clean = query.trim()
        if (clean.isBlank()) return
        context.peekSearchData.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(ListSerializer(SavedSearch.serializer()), it) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val entry = SavedSearch(
                query = clean,
                targetUsername = targetUsername,
                createdAt = System.currentTimeMillis(),
            )
            val next = (listOf(entry) + current.filterNot { it.query.equals(clean, ignoreCase = true) })
                .take(24)
            prefs[key] = json.encodeToString(ListSerializer(SavedSearch.serializer()), next)
        }
    }

    suspend fun remove(query: String) {
        context.peekSearchData.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(ListSerializer(SavedSearch.serializer()), it) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(
                ListSerializer(SavedSearch.serializer()),
                current.filterNot { it.query.equals(query, ignoreCase = true) },
            )
        }
    }
}
