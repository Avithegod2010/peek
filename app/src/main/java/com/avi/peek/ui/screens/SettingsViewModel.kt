package com.avi.peek.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.PaletteStyle
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.ThemeMode
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import com.avi.peek.work.SyncScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val repo = ServiceLocator.repository
    private val store = ServiceLocator.settings
    private val appContext = ServiceLocator.appContext

    val settings: StateFlow<PeekSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val syncState = repo.syncState

    private val _storage = MutableStateFlow(0L to 0L)
    val storage: StateFlow<Pair<Long, Long>> = _storage.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = runCatching { repo.storageUsage() }.getOrDefault(0L to 0L)
        }
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { store.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { store.setDynamicColor(value) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { store.setAmoled(value) }
    fun setPalette(value: PaletteStyle) = viewModelScope.launch { store.setPalette(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { store.setHaptics(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { store.setReduceMotion(value) }
    fun setSyncOnOpen(value: Boolean) = viewModelScope.launch { store.setSyncOnOpen(value) }
    fun setEnrichFeed(value: Boolean) = viewModelScope.launch { store.setEnrichFeed(value) }
    fun setSyncDepth(value: Int) = viewModelScope.launch { store.setSyncDepth(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch {
        store.setWifiOnly(value)
        val current = settings.value.copy(wifiOnlySync = value)
        if (current.backgroundSync) {
            SyncScheduler.schedule(appContext, current.backgroundIntervalHours, current.wifiOnlySync)
        }
    }
    fun setDataSaver(value: Boolean) = viewModelScope.launch { store.setDataSaver(value) }
    fun setHideLikeCounts(value: Boolean) = viewModelScope.launch { store.setHideLikeCounts(value) }
    fun setAutoplayReels(value: Boolean) = viewModelScope.launch { store.setAutoplayReels(value) }
    fun setMuteVideos(value: Boolean) = viewModelScope.launch { store.setMuteVideos(value) }

    fun setNotifications(value: Boolean) {
        viewModelScope.launch {
            store.setNotifications(value)
            if (!value) syncMessage("Notifications off")
        }
    }

    fun setBackgroundSync(value: Boolean) = viewModelScope.launch {
        store.setBackgroundSync(value)
        val current = settings.value.copy(backgroundSync = value)
        if (value) {
            SyncScheduler.schedule(appContext, current.backgroundIntervalHours, current.wifiOnlySync)
            syncMessage("Background sync scheduled every ${current.backgroundIntervalHours}h")
        } else {
            SyncScheduler.cancel(appContext)
        }
    }

    fun setBackgroundInterval(hours: Int) = viewModelScope.launch {
        store.setBackgroundInterval(hours)
        val current = settings.value.copy(backgroundIntervalHours = hours)
        if (current.backgroundSync) {
            SyncScheduler.schedule(appContext, current.backgroundIntervalHours, current.wifiOnlySync)
            syncMessage("Background sync scheduled every ${hours}h")
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncMessage("Syncing your circle…")
            val report = repo.syncAll(force = true)
            if (report != null) {
                syncMessage(
                    if (report.errors == 0) "Circle up to date" else "Refreshed with ${report.errors} hiccups",
                )
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            runCatching { ServiceLocator.imageLoader.diskCache?.clear() }
            repo.deleteCachedMedia()
            refreshStorage()
            syncMessage("Image cache cleared")
        }
    }

    fun clearEverything() {
        viewModelScope.launch {
            runCatching { ServiceLocator.imageLoader.diskCache?.clear() }
            repo.clearEverything()
            store.setLastFullSync(0L)
            store.setOnboarded(false)
            refreshStorage()
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { ServiceLocator.backupManager.exportTo(uri) }
                .onSuccess { count -> syncMessage("Exported $count follows") }
                .onFailure { syncMessage(it.userMessage()) }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            syncMessage("Restoring…")
            runCatching { ServiceLocator.backupManager.importFrom(uri) }
                .onSuccess { summary ->
                    syncMessage("Restored ${summary.followed} follows, ${summary.restoredSaved} bookmarks")
                }
                .onFailure { syncMessage(it.userMessage()) }
        }
    }

    fun syncMessage(text: String) {
        _messages.tryEmit(text)
    }
}
