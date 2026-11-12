package com.avi.peek.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileDownloadState(
    val username: String,
    val total: Int,
    val downloaded: Int = 0,
    val failed: Int = 0,
    val preparing: Boolean = false,
    val prepared: Int = 0,
    val saveToGallery: Boolean = false,
    val galleryFiles: Int = 0,
    val galleryFailures: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
)

/**
 * Downloads every post Peek can reach for a profile, then optionally copies
 * those posts into the device gallery.
 *
 * `startAll` first walks the logged-out timeline pagination and then queues every
 * post it found. `start` is the lighter version used when the caller already has
 * a list of codes.
 */
class ProfileDownloadManager(
    private val repository: PeekRepository,
    private val queue: DownloadQueue,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, ProfileDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, ProfileDownloadState>> = _states.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun start(username: String, codes: List<String>, saveToGallery: Boolean) {
        val clean = codes.distinct().filter { it.isNotBlank() }
        if (clean.isEmpty() || jobs[username]?.isActive == true) return
        _states.update {
            it + (
                username to ProfileDownloadState(
                    username = username,
                    total = clean.size,
                    saveToGallery = saveToGallery,
                    running = true,
                )
                )
        }
        jobs[username] = scope.launch {
            repository.privateProfileBlock(username)?.let { reason ->
                _states.update { current ->
                    val state = current[username] ?: return@update current
                    current + (username to state.copy(running = false, error = reason))
                }
                jobs.remove(username)
                return@launch
            }
            val max = repository.currentSettings().downloadQueuePerProfile
            val limited = if (max > 0) clean.take(max) else clean
            _states.update { current ->
                val state = current[username] ?: return@update current
                current + (username to state.copy(total = limited.size))
            }
            runDownloads(username, limited, saveToGallery)
        }
    }

    /** Loads every reachable page first, then downloads all of it. */
    fun startAll(username: String, saveToGallery: Boolean) {
        if (jobs[username]?.isActive == true) return
        val blocked = queue.blockReason()
        if (blocked != null) {
            _states.update { current ->
                current + (
                    username to ProfileDownloadState(
                        username = username,
                        total = 0,
                        running = false,
                        error = blocked,
                    )
                    )
            }
            return
        }
        _states.update {
            it + (
                username to ProfileDownloadState(
                    username = username,
                    total = 0,
                    saveToGallery = saveToGallery,
                    running = true,
                    preparing = true,
                )
                )
        }
        jobs[username] = scope.launch {
            repository.privateProfileBlock(username)?.let { reason ->
                _states.update { current ->
                    val state = current[username] ?: return@update current
                    current + (
                        username to state.copy(
                            running = false,
                            preparing = false,
                            error = reason,
                        )
                        )
                }
                jobs.remove(username)
                return@launch
            }
            var loaded = 0
            runCatching {
                repository.loadAllProfilePosts(username) { count, _ ->
                    loaded = count
                    _states.update { current ->
                        val state = current[username] ?: return@update current
                        current + (username to state.copy(prepared = count))
                    }
                }
            }.onFailure { error ->
                _states.update { current ->
                    val state = current[username] ?: return@update current
                    current + (
                        username to state.copy(
                            running = false,
                            finished = false,
                            preparing = false,
                            error = error.message ?: "Couldn't load the profile timeline",
                        )
                        )
                }
                jobs.remove(username)
                return@launch
            }

            val allCodes = repository.allProfileMediaCodes(username)
            val max = repository.currentSettings().downloadQueuePerProfile
            val codes = if (max > 0) allCodes.take(max) else allCodes
            _states.update { current ->
                val state = current[username] ?: return@update current
                current + (
                    username to state.copy(
                        total = codes.size,
                        prepared = maxOf(loaded, allCodes.size),
                        preparing = false,
                    )
                    )
            }
            runDownloads(username, codes, saveToGallery)
        }
    }

    private suspend fun runDownloads(
        username: String,
        codes: List<String>,
        saveToGallery: Boolean,
    ) {
        val blocked = queue.blockReason()
        if (blocked != null) {
            _states.update { current ->
                val state = current[username] ?: return@update current
                current + (
                    username to state.copy(
                        running = false,
                        preparing = false,
                        error = blocked,
                    )
                    )
            }
            jobs.remove(username)
            return
        }
        if (codes.isEmpty()) {
            _states.update { current ->
                val state = current[username] ?: return@update current
                current + (username to state.copy(running = false, finished = true))
            }
            jobs.remove(username)
            return
        }

        codes.forEach { queue.enqueue(it) }

        queue.tasks
            .map { tasks ->
                var completed = 0
                var failures = 0
                codes.forEach { code ->
                    when (tasks.firstOrNull { it.code == code }?.status) {
                        DownloadStatus.DONE -> completed++
                        DownloadStatus.FAILED -> failures++
                        else -> Unit
                    }
                }
                completed to failures
            }
            .first { (completed, failures) -> completed + failures >= codes.size }

        val snapshot = queue.tasks.value
        val successful = codes.filter { code ->
            snapshot.firstOrNull { it.code == code }?.status == DownloadStatus.DONE
        }
        var galleryFiles = 0
        var galleryFailures = 0

        if (saveToGallery) {
            successful.forEach { code ->
                runCatching { repository.saveToGallery(code) }
                    .onSuccess { galleryFiles += it.total }
                    .onFailure { galleryFailures++ }
                _states.update { current ->
                    val state = current[username] ?: return@update current
                    current + (
                        username to state.copy(
                            galleryFiles = galleryFiles,
                            galleryFailures = galleryFailures,
                        )
                        )
                }
            }
        }

        _states.update { current ->
            val state = current[username] ?: return@update current
            current + (
                username to state.copy(
                    downloaded = successful.size,
                    failed = codes.size - successful.size,
                    galleryFiles = galleryFiles,
                    galleryFailures = galleryFailures,
                    running = false,
                    finished = true,
                    preparing = false,
                )
                )
        }
        jobs.remove(username)
    }

    fun clear(username: String) {
        _states.update { it - username }
    }
}
