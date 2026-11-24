package com.avi.peek.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.peekLibraryData: DataStore<Preferences> by preferencesDataStore(name = "peek_library")

@Serializable
data class LocalCollection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val codes: List<String> = emptyList(),
    val coverCode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class LocalCircle(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val usernames: List<String> = emptyList(),
    val closeFriends: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Device-only lists that are too small for a Room table: collections, private
 * circles, per-profile visit marks and archive timestamps.
 */
class LibraryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val collectionsKey = stringPreferencesKey("collections")
    private val circlesKey = stringPreferencesKey("circles")
    private val visitsKey = stringPreferencesKey("visits")
    private val archivesKey = stringPreferencesKey("archives")
    private val seenHighlightsKey = stringPreferencesKey("seen_highlights")
    private val notesKey = stringPreferencesKey("notes")
    private val collectionSerializer = ListSerializer(LocalCollection.serializer())
    private val circleSerializer = ListSerializer(LocalCircle.serializer())
    private val mapSerializer = MapSerializer(String.serializer(), Long.serializer())
    private val notesSerializer = MapSerializer(String.serializer(), String.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())

    private val prefs = context.peekLibraryData.data
        .catch { emit(emptyPreferences()) }

    val collections: Flow<List<LocalCollection>> = prefs.map { readCollections(it[collectionsKey]) }
    val circles: Flow<List<LocalCircle>> = prefs.map { readCircles(it[circlesKey]) }
    val seenHighlights: Flow<Set<String>> = prefs.map { preferences ->
        runCatching {
            preferences[seenHighlightsKey]?.let {
                json.decodeFromString(stringListSerializer, it).toSet()
            } ?: emptySet()
        }.getOrDefault(emptySet())
    }

    val notes: Flow<Map<String, String>> = prefs.map { preferences ->
        val raw = FieldCipher.decrypt(preferences[notesKey]) ?: return@map emptyMap()
        runCatching { json.decodeFromString(notesSerializer, raw) }.getOrDefault(emptyMap())
    }

    suspend fun noteFor(code: String): String? = notes.first()[code]

    suspend fun setNote(code: String, note: String) {
        val clean = note.trim().take(500)
        context.peekLibraryData.edit { prefs ->
            val current = runCatching {
                val raw = FieldCipher.decrypt(prefs[notesKey]) ?: return@runCatching emptyMap()
                json.decodeFromString(notesSerializer, raw)
            }.getOrDefault(emptyMap())
            val next = if (clean.isBlank()) current - code else current + (code to clean)
            val encoded = json.encodeToString(notesSerializer, next)
            FieldCipher.encrypt(encoded)?.let { prefs[notesKey] = it }
        }
    }

    suspend fun createCollection(name: String, codes: List<String>): String? {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return null
        val id = UUID.randomUUID().toString()
        context.peekLibraryData.edit { prefs ->
            val current = readCollections(prefs[collectionsKey])
            val entry = LocalCollection(
                id = id,
                name = clean,
                codes = codes.distinct(),
                coverCode = codes.firstOrNull(),
            )
            prefs[collectionsKey] = json.encodeToString(
                collectionSerializer,
                listOf(entry) + current.filterNot { it.name.equals(clean, ignoreCase = true) },
            )
        }
        return id
    }

    suspend fun addToCollection(id: String, codes: List<String>) {
        if (codes.isEmpty()) return
        context.peekLibraryData.edit { prefs ->
            val next = readCollections(prefs[collectionsKey]).map { collection ->
                if (collection.id != id) collection
                else collection.copy(
                    codes = (collection.codes + codes).distinct(),
                    coverCode = collection.coverCode ?: codes.firstOrNull(),
                )
            }
            prefs[collectionsKey] = json.encodeToString(collectionSerializer, next)
        }
    }

    suspend fun renameCollection(id: String, name: String) {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return
        context.peekLibraryData.edit { prefs ->
            val next = readCollections(prefs[collectionsKey]).map {
                if (it.id == id) it.copy(name = clean) else it
            }
            prefs[collectionsKey] = json.encodeToString(collectionSerializer, next)
        }
    }

    suspend fun deleteCollection(id: String) {
        context.peekLibraryData.edit { prefs ->
            prefs[collectionsKey] = json.encodeToString(
                collectionSerializer,
                readCollections(prefs[collectionsKey]).filterNot { it.id == id },
            )
        }
    }

    suspend fun saveCircle(
        name: String,
        usernames: List<String>,
        closeFriends: Boolean,
        id: String? = null,
    ): String? {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return null
        val actualId = id ?: UUID.randomUUID().toString()
        context.peekLibraryData.edit { prefs ->
            val current = readCircles(prefs[circlesKey])
            val entry = LocalCircle(
                id = actualId,
                name = clean,
                usernames = usernames.map { it.removePrefix("@").lowercase() }.distinct(),
                closeFriends = closeFriends,
            )
            prefs[circlesKey] = json.encodeToString(
                circleSerializer,
                listOf(entry) + current.filterNot { it.id == actualId },
            )
        }
        return actualId
    }

    suspend fun deleteCircle(id: String) {
        context.peekLibraryData.edit { prefs ->
            prefs[circlesKey] = json.encodeToString(
                circleSerializer,
                readCircles(prefs[circlesKey]).filterNot { it.id == id },
            )
        }
    }

    suspend fun lastVisit(username: String): Long =
        readLongMap(readString(visitsKey))[username] ?: 0L

    suspend fun markVisited(username: String, at: Long) {
        context.peekLibraryData.edit { prefs ->
            val next = readLongMap(prefs[visitsKey]) + (username to at)
            prefs[visitsKey] = json.encodeToString(mapSerializer, next)
        }
    }

    suspend fun lastArchiveAt(username: String): Long =
        readLongMap(readString(archivesKey))[username] ?: 0L

    suspend fun markArchive(username: String, at: Long) {
        context.peekLibraryData.edit { prefs ->
            val next = readLongMap(prefs[archivesKey]) + (username to at)
            prefs[archivesKey] = json.encodeToString(mapSerializer, next)
        }
    }

    suspend fun markHighlightSeen(id: String) {
        if (id.isBlank()) return
        context.peekLibraryData.edit { prefs ->
            val current = runCatching {
                prefs[seenHighlightsKey]?.let {
                    json.decodeFromString(stringListSerializer, it).toSet()
                } ?: emptySet()
            }.getOrDefault(emptySet())
            prefs[seenHighlightsKey] = json.encodeToString(
                stringListSerializer,
                (current + id).toList(),
            )
        }
    }

    private fun readCollections(raw: String?): List<LocalCollection> = runCatching {
        raw?.let { json.decodeFromString(collectionSerializer, it) } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readCircles(raw: String?): List<LocalCircle> = runCatching {
        raw?.let { json.decodeFromString(circleSerializer, it) } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readLongMap(raw: String?): Map<String, Long> = runCatching {
        raw?.let { json.decodeFromString(mapSerializer, it) } ?: emptyMap()
    }.getOrDefault(emptyMap())

    private suspend fun readString(key: Preferences.Key<String>): String? =
        context.peekLibraryData.data
            .catch { emit(emptyPreferences()) }
            .first()[key]
}
