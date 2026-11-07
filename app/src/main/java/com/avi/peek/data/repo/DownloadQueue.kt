package com.avi.peek.data.repo

import android.content.Context
import android.os.BatteryManager
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, FAILED, DONE }

data class DownloadTask(
    val code: String,
    val status: DownloadStatus,
    val fraction: Float = 0f,
    val error: String? = null,
)

class DownloadQueue(
    private val repository: PeekRepository,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val context: Context,
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _pausedAll = MutableStateFlow(false)
    val pausedAll: StateFlow<Boolean> = _pausedAll.asStateFlow()

    private var latestSettings = PeekSettings()

    private val jobs = mutableMapOf<String, Job>()
    private val semaphore = Semaphore(2)

    init {
        scope.launch {
            settings.settings.collect { latestSettings = it }
        }
        scope.launch {
            repository.downloadProgress.collect { progress ->
                _tasks.update { list ->
                    list.map { task ->
                        val item = progress[task.code]
                        if (item != null && task.status == DownloadStatus.RUNNING) {
                            task.copy(fraction = item.fraction)
                        } else {
                            task
                        }
                    }
                }
            }
        }
    }

    fun blockReason(): String? {
        if (_pausedAll.value) return "Downloads are paused"
        if (latestSettings.wifiOnlySync && !network.wifi.value) return "Wi-Fi only is on"
        if (latestSettings.downloadChargingOnly && !isCharging()) return "Waiting for a charger"
        return null
    }

    fun enqueue(code: String): String? {
        blockReason()?.let { return it }
        val existing = _tasks.value.firstOrNull { it.code == code }
        if (existing != null && existing.status in setOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
            )
        ) {
            return null
        }
        _tasks.update { current ->
            current.filterNot { it.code == code } + DownloadTask(code, DownloadStatus.QUEUED)
        }
        start(code)
        return null
    }

    fun pauseAll() {
        _pausedAll.value = true
        jobs.keys.toList().forEach { code -> jobs[code]?.cancel() }
        jobs.clear()
        _tasks.update { list ->
            list.map {
                if (it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED) {
                    it.copy(status = DownloadStatus.PAUSED)
                } else it
            }
        }
    }

    fun resumeAll() {
        _pausedAll.value = false
        val paused = _tasks.value.filter { it.status == DownloadStatus.PAUSED }.map { it.code }
        paused.forEach { code -> resume(code) }
    }

    private fun isCharging(): Boolean {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return false
        return manager.isCharging
    }

    fun pause(code: String) {
        jobs[code]?.cancel()
        jobs.remove(code)
        _tasks.update { list ->
            list.map { if (it.code == code && it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.PAUSED) else it }
        }
    }

    fun resume(code: String) {
        val task = _tasks.value.firstOrNull { it.code == code } ?: return
        if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
            _tasks.update { list -> list.map { if (it.code == code) it.copy(status = DownloadStatus.QUEUED, fraction = 0f, error = null) else it } }
            start(code)
        }
    }

    fun retry(code: String) = resume(code)

    fun remove(code: String) {
        jobs[code]?.cancel()
        jobs.remove(code)
        _tasks.update { it.filterNot { task -> task.code == code } }
    }

    fun clearFinished() {
        _tasks.update { it.filterNot { task -> task.status == DownloadStatus.DONE } }
    }

    private fun start(code: String) {
        if (jobs.containsKey(code)) return
        jobs[code] = scope.launch {
            semaphore.withPermit {
                _tasks.update { list -> list.map { if (it.code == code) it.copy(status = DownloadStatus.RUNNING, error = null) else it } }
                try {
                    repository.download(code)
                    _tasks.update { list -> list.map { if (it.code == code) it.copy(status = DownloadStatus.DONE, fraction = 1f) else it } }
                } catch (e: CancellationException) {
                    _tasks.update { list ->
                        list.map { if (it.code == code) it.copy(status = DownloadStatus.PAUSED) else it }
                    }
                    throw e
                } catch (e: Exception) {
                    _tasks.update { list ->
                        list.map {
                            if (it.code == code) it.copy(status = DownloadStatus.FAILED, error = e.message)
                            else it
                        }
                    }
                } finally {
                    jobs.remove(code)
                }
            }
        }
    }
}
