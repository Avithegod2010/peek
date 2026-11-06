package com.avi.peek.data.repo

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val app: String = "Peek",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val follows: List<String> = emptyList(),
    val saved: List<String> = emptyList(),
    val liked: List<String> = emptyList(),
)

class BackupManager(
    private val context: Context,
    private val repository: PeekRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): Int {
        val data = repository.exportBackup()
        val text = json.encodeToString(BackupData.serializer(), data)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error("Could not open that file for writing")
        return data.follows.size
    }

    suspend fun importFrom(uri: Uri): ImportSummary {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read that file")
        val data = runCatching { json.decodeFromString(BackupData.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("That doesn't look like a Peek backup") }
        if (data.app != "Peek") throw IllegalArgumentException("That backup belongs to another app")
        return repository.importBackup(data)
    }
}
