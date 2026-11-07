package com.avi.peek.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RequestEvent(
    val at: Long = System.currentTimeMillis(),
    val label: String,
    val outcome: String,
    val rateLimited: Boolean = false,
)

/** Small persistent log of the guest requests Peek makes. */
class RequestLog(context: Context) {
    private val file = File(context.filesDir, "request-log.jsonl")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<RequestEvent>> = _events.asStateFlow()

    fun record(label: String, outcome: String, rateLimited: Boolean = false) {
        val event = RequestEvent(label = label, outcome = outcome, rateLimited = rateLimited)
        synchronized(lock) {
            runCatching {
                file.appendText(json.encodeToString(RequestEvent.serializer(), event) + "\n")
            }
            _events.update { (it + event).takeLast(150) }
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file.delete() }
            _events.value = emptyList()
        }
    }

    private fun load(): List<RequestEvent> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines()
            .takeLast(150)
            .mapNotNull { line ->
                runCatching { json.decodeFromString(RequestEvent.serializer(), line) }.getOrNull()
            }
    }.getOrDefault(emptyList())
}
