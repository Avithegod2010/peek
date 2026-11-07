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
    val saveToGallery: Boolean = false,
    val galleryFiles: Int = 0,
    val galleryFailures: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
)

/**
 * Downloads every post Peek can currently see for a profile, then optionally
 * copies those posts into the device gallery.
 *
 * This deliberately uses the posts already loaded from the public profile page.
 * Instagram does not expose older timeline pages to logged-out guests, so a
 * huge account is usually limited to the latest public grid, not its full
 * archive.
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
        if (clean.isEmpty()) return
        if (jobs[username]?.isActive == true) return

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
            clean.forEach { queue.enqueue(it) }

            val done = queue.tasks
                .map { tasks ->
                    var completed = 0
                    var failures = 0
                    clean.forEach { code ->
                        when (tasks.firstOrNull { it.code == code }?.status) {
                            DownloadStatus.DONE -> completed++
                            DownloadStatus.FAILED -> failures++
                            else -> Unit
                        }
                    }
                    completed to failures
                }
                .first { (completed, failures) -> completed + failures >= clean.size }
                .first

            val snapshot = queue.tasks.value
            val successful = clean.filter { code ->
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
                        downloaded = done,
                        failed = clean.size - successful.size,
                        galleryFiles = galleryFiles,
                        galleryFailures = galleryFailures,
                        running = false,
                        finished = true,
                    )
                    )
            }
            jobs.remove(username)
        }
    }

    fun clear(username: String) {
        _states.update { it - username }
    }
}
