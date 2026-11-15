package com.avi.peek.ui.screens

import android.net.Uri
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.PaletteStyle
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.PrivateDownloadPolicy
import com.avi.peek.data.local.ThemeMode
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.PrivateAccess
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
    fun setAppLock(value: Boolean) = viewModelScope.launch { store.setAppLock(value) }
    fun setOneHanded(value: Boolean) = viewModelScope.launch { store.setOneHanded(value) }
    fun setDownloadChargingOnly(value: Boolean) = viewModelScope.launch { store.setDownloadChargingOnly(value) }
    fun setDownloadQueuePerProfile(value: Int) = viewModelScope.launch { store.setDownloadQueuePerProfile(value) }
    fun setGallerySidecars(value: Boolean) = viewModelScope.launch { store.setGallerySidecars(value) }
    fun setAutoplayReels(value: Boolean) = viewModelScope.launch { store.setAutoplayReels(value) }
    fun setMuteVideos(value: Boolean) = viewModelScope.launch { store.setMuteVideos(value) }
    fun setPrivateDownloadPolicy(value: PrivateDownloadPolicy) =
        viewModelScope.launch { store.setPrivateDownloadPolicy(value) }
    fun setPrivateVault(value: Boolean) = viewModelScope.launch { store.setPrivateVault(value) }
    fun setPrivateAutoLockMinutes(value: Int) = viewModelScope.launch {
        store.setPrivateAutoLockMinutes(value)
        PrivateAccess.setAutoLockMinutes(value)
    }
    fun setPanicShake(value: Boolean) = viewModelScope.launch { store.setPanicShake(value) }
    fun setCustomAccent(value: Long?) = viewModelScope.launch { store.setCustomAccent(value) }
    fun setAccentSaturation(value: Float) = viewModelScope.launch { store.setAccentSaturation(value) }
    fun setAccentValue(value: Float) = viewModelScope.launch { store.setAccentValue(value) }
    fun setFontScale(value: Float) = viewModelScope.launch { store.setFontScale(value) }
    fun setHighContrast(value: Boolean) = viewModelScope.launch { store.setHighContrast(value) }
    fun setImageCacheMb(value: Int) = viewModelScope.launch {
        store.setImageCacheMb(value)
        syncMessage("Image cache limit changes after the next app start")
    }
    fun setRequestBudgetPerHour(value: Int) = viewModelScope.launch { store.setRequestBudgetPerHour(value) }
    fun setQuietHours(value: Boolean) = viewModelScope.launch { store.setQuietHours(value) }
    fun setQuietStartHour(value: Int) = viewModelScope.launch { store.setQuietStartHour(value) }
    fun setQuietEndHour(value: Int) = viewModelScope.launch { store.setQuietEndHour(value) }
    fun setIncludePrivateInBackups(value: Boolean) =
        viewModelScope.launch { store.setIncludePrivateInBackups(value) }
    fun setIncludePrivateInArchives(value: Boolean) =
        viewModelScope.launch { store.setIncludePrivateInArchives(value) }

    fun hidePrivateContent() {
        PrivateAccess.hide()
        viewModelScope.launch {
            store.setPrivateContentHidden(true)
            runCatching { repo.sealPrivateVault() }
        }
    }

    fun revealPrivateContent() {
        PrivateAccess.reveal()
        viewModelScope.launch {
            store.setPrivateContentHidden(false)
            runCatching { repo.openPrivateVault() }
        }
    }

    fun clearPrivateContent() {
        viewModelScope.launch {
            runCatching { repo.purgePrivateContent() }
            refreshStorage()
            syncMessage("Private downloads and cached private posts removed")
        }
    }

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
            runCatching { ServiceLocator.accountManager.disconnect() }
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            ServiceLocator.downloadQueue.clearPersistentQueue()
            repo.clearEverything()
            store.setLastFullSync(0L)
            store.setOnboarded(false)
            refreshStorage()
        }
    }

    fun exportBackup(uri: Uri, passphrase: String?) {
        viewModelScope.launch {
            runCatching { ServiceLocator.backupManager.exportTo(uri, passphrase) }
                .onSuccess { count ->
                    syncMessage(
                        if (passphrase.isNullOrBlank()) "Exported $count follows (unencrypted)"
                        else "Encrypted backup exported · $count follows",
                    )
                }
                .onFailure { syncMessage(it.userMessage()) }
        }
    }

    fun importBackup(uri: Uri, passphrase: String?) {
        viewModelScope.launch {
            syncMessage("Restoring…")
            runCatching { ServiceLocator.backupManager.importFrom(uri, passphrase) }
                .onSuccess { summary ->
                    syncMessage("Restored ${summary.followed} follows, ${summary.restoredSaved} bookmarks")
                }
                .onFailure { syncMessage(it.userMessage()) }
        }
    }

    fun exportArchive(uri: Uri, html: Boolean) {
        viewModelScope.launch {
            syncMessage("Writing archive…")
            runCatching {
                if (html) ServiceLocator.backupManager.exportHtmlArchive(uri)
                else ServiceLocator.backupManager.exportJsonArchive(uri)
            }.onSuccess {
                syncMessage(if (html) "HTML archive exported" else "JSON archive exported")
            }.onFailure {
                syncMessage(it.userMessage())
            }
        }
    }

    fun syncMessage(text: String) {
        _messages.tryEmit(text)
    }
}
