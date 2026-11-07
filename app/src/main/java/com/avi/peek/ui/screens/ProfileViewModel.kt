package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.directory.PopularCreator
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.ProfileWithMedia
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.repo.ProfileDownloadState
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class ProfileTab { POSTS, REELS, SAVED }

class ProfileViewModel(private val username: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    val data: StateFlow<ProfileWithMedia?> = repo.observeProfile(username)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val following = repo.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadProgress = repo.downloadProgress
    private val queue = ServiceLocator.downloadQueue

    val bulkDownload: StateFlow<ProfileDownloadState?> = ServiceLocator.profileDownloadManager.states
        .map { it[username] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tab = MutableStateFlow(ProfileTab.POSTS)
    val tab: StateFlow<ProfileTab> = _tab.asStateFlow()

    val visibleMedia: StateFlow<List<MediaEntity>> = combine(data, _tab) { value, tab ->
        val media = value?.media.orEmpty()
        when (tab) {
            ProfileTab.POSTS -> media.filter { !it.isReel }
            ProfileTab.REELS -> media.filter { it.isReel }
            ProfileTab.SAVED -> media.filter { it.isSaved }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    val relatedCreators: List<PopularCreator> = run {
        val category = CreatorDirectory.byUsername(username)?.category
        if (category == null) {
            CreatorDirectory.trending.filterNot { it.username.equals(username, true) }.take(6)
        } else {
            CreatorDirectory.all.filter {
                it.category == category && !it.username.equals(username, true)
            }.take(8)
        }
    }

    private val _relatedProfiles = MutableStateFlow<Map<String, com.avi.peek.data.local.ProfileEntity>>(emptyMap())
    val relatedProfiles: StateFlow<Map<String, com.avi.peek.data.local.ProfileEntity>> = _relatedProfiles.asStateFlow()

    private val _relatedBusy = MutableStateFlow<Set<String>>(emptySet())
    val relatedBusy: StateFlow<Set<String>> = _relatedBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repo.markViewed(username)
            val current = repo.observeProfile(username).first()?.profile
            if (current == null ||
                System.currentTimeMillis() - current.lastFetchedAt > 15 * 60 * 1000 ||
                current.nextCursor == null
            ) {
                refresh()
            }
        }
        prefetchRelated()
    }

    private fun prefetchRelated() {
        val semaphore = Semaphore(2)
        relatedCreators.forEach { creator ->
            if (_relatedProfiles.value.containsKey(creator.username)) return@forEach
            viewModelScope.launch {
                semaphore.withPermit {
                    val profile = runCatching { repo.prefetchDirectoryProfile(creator.username) }.getOrNull()
                    if (profile != null) _relatedProfiles.update { it + (creator.username to profile) }
                    delay(140)
                }
            }
        }
    }

    fun followRelated(creator: PopularCreator) {
        if (_relatedBusy.value.contains(creator.username)) return
        viewModelScope.launch {
            _relatedBusy.update { it + creator.username }
            try {
                val cached = repo.followCached(creator.username)
                if (!cached) repo.follow(creator.username)
                _messages.emit("@${creator.username} added to your circle")
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _relatedBusy.update { it - creator.username }
            }
        }
    }

    fun setTab(value: ProfileTab) {
        _tab.value = value
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repo.refreshProfile(username)
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadMore() {
        if (_loadingMore.value) return
        val current = data.value?.profile ?: return
        if (!current.hasMorePosts) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                repo.loadMoreProfilePosts(username)
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun unfollow() {
        viewModelScope.launch {
            repo.unfollow(username)
            _messages.emit("@$username is no longer in your circle")
        }
    }

    fun removeProfile() {
        viewModelScope.launch {
            repo.removeProfile(username)
            _messages.emit("@$username removed from Peek")
        }
    }

    fun togglePinned() {
        viewModelScope.launch {
            val current = data.value?.profile ?: return@launch
            repo.setPinned(username, !current.isPinned)
            _messages.emit(if (current.isPinned) "Unpinned" else "Pinned to the top")
        }
    }

    fun postMessage(text: String) {
        _messages.tryEmit(text)
    }

    fun toggleLike(code: String) {
        viewModelScope.launch { repo.toggleLike(code) }
    }

    fun toggleSave(code: String) {
        viewModelScope.launch { repo.toggleSaved(code) }
    }

    fun download(code: String) {
        queue.enqueue(code)
        _messages.tryEmit("Added to the download queue")
    }

    fun downloadProfile(saveToGallery: Boolean) {
        val posts = data.value?.media.orEmpty()
        if (posts.isEmpty()) {
            _messages.tryEmit("No public posts are loaded yet. Refresh, then try again.")
            return
        }
        if (settings.value.wifiOnlySync && !ServiceLocator.network.wifi.value) {
            _messages.tryEmit("Wi-Fi only is on. Connect to Wi-Fi to download a full profile.")
            return
        }
        ServiceLocator.profileDownloadManager.startAll(
            username = username,
            saveToGallery = saveToGallery,
        )
        _messages.tryEmit(
            if (saveToGallery) {
                "Loading the full public profile, then downloading it to your gallery"
            } else {
                "Loading the full public profile, then downloading it offline"
            },
        )
    }

    fun clearBulkDownload() = ServiceLocator.profileDownloadManager.clear(username)

    fun follow() {
        viewModelScope.launch {
            try {
                repo.follow(username)
                _messages.emit("@$username added to your circle")
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            }
        }
    }
}
