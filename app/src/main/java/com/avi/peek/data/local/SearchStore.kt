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
            prefs[key]?.let { stored ->
                val raw = FieldCipher.decrypt(stored) ?: return@map emptyList()
                runCatching { json.decodeFromString(ListSerializer(SavedSearch.serializer()), raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(query: String, targetUsername: String? = null) {
        val clean = query.trim()
        if (clean.isBlank()) return
        context.peekSearchData.edit { prefs ->
            val current = decode(prefs[key])
            val entry = SavedSearch(
                query = clean,
                targetUsername = targetUsername,
                createdAt = System.currentTimeMillis(),
            )
            val next = (listOf(entry) + current.filterNot { it.query.equals(clean, ignoreCase = true) })
                .take(24)
            prefs[key] = encode(next)
        }
    }

    suspend fun remove(query: String) {
        context.peekSearchData.edit { prefs ->
            val current = decode(prefs[key])
            prefs[key] = encode(
                current.filterNot { it.query.equals(query, ignoreCase = true) },
            )
        }
    }

    private fun decode(stored: String?): List<SavedSearch> {
        if (stored == null) return emptyList()
        val raw = FieldCipher.decrypt(stored) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SavedSearch.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun encode(entries: List<SavedSearch>): String {
        val raw = json.encodeToString(ListSerializer(SavedSearch.serializer()), entries)
        return FieldCipher.encrypt(raw) ?: error("Could not encrypt saved searches")
    }
}
