package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.directory.PopularCreator
import com.avi.peek.data.local.AccountState
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.SessionHealth
import com.avi.peek.data.local.ProfileWithMedia
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.repo.ProfileDownloadState
import com.avi.peek.data.repo.VisibilityPolicy
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.PrivateAccess
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

enum class ProfileMediaType(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    REELS("Reels"),
    CAROUSELS("Carousels"),
}

enum class ProfileDateRange(val label: String) {
    ANY("Any time"),
    WEEK("Last 7 days"),
    MONTH("Last 30 days"),
    YEAR("Last year"),
}

enum class ProfileSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    MOST_LIKED("Most liked"),
}

data class ProfileFilters(
    val type: ProfileMediaType = ProfileMediaType.ALL,
    val date: ProfileDateRange = ProfileDateRange.ANY,
    val sort: ProfileSort = ProfileSort.NEWEST,
    val onlyNew: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(
            type != ProfileMediaType.ALL,
            date != ProfileDateRange.ANY,
            sort != ProfileSort.NEWEST,
            onlyNew,
        ).count { it }
}

enum class ProfileLockReason {
    HIDDEN,
    SIGNED_OUT,
    SESSION_EXPIRED,
    NOT_FOLLOWING,
    RATE_LIMITED,
    NETWORK,
    BLOCKED,
    UNKNOWN,
}

data class ProfileDiagnosis(
    val locked: Boolean = false,
    val reason: ProfileLockReason = ProfileLockReason.UNKNOWN,
    val title: String = "",
    val message: String = "",
    val detail: String? = null,
)

class ProfileViewModel(private val username: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    val data: StateFlow<ProfileWithMedia?> = repo.observeProfile(username)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val following = repo.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val account: StateFlow<AccountState> = ServiceLocator.accountManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())

    val diagnosis: StateFlow<ProfileDiagnosis> = combine(
        data,
        account,
        settings,
        PrivateAccess.revealed,
    ) { value, currentAccount, currentSettings, revealed ->
        val profile = value?.profile
        val private = profile?.isPrivate == true
        val media = value?.media.orEmpty()
        val hidden = !VisibilityPolicy.canShow(private, revealed, currentSettings.privateContentHidden)
        if (!private || (media.isNotEmpty() && !hidden)) {
            return@combine ProfileDiagnosis()
        }
        val error = profile?.lastError
        val reason = when {
            hidden -> ProfileLockReason.HIDDEN
            !currentAccount.signedIn -> ProfileLockReason.SIGNED_OUT
            currentAccount.health == SessionHealth.EXPIRED -> ProfileLockReason.SESSION_EXPIRED
            error?.contains("rate limit", ignoreCase = true) == true -> ProfileLockReason.RATE_LIMITED
            error?.contains("reach Instagram", ignoreCase = true) == true -> ProfileLockReason.NETWORK
            error?.contains("login", ignoreCase = true) == true -> ProfileLockReason.SESSION_EXPIRED
            error != null -> ProfileLockReason.BLOCKED
            currentAccount.signedIn -> ProfileLockReason.NOT_FOLLOWING
            else -> ProfileLockReason.UNKNOWN
        }
        val title = when (reason) {
            ProfileLockReason.HIDDEN -> "Private content is hidden"
            ProfileLockReason.SIGNED_OUT -> "This account is private"
            ProfileLockReason.SESSION_EXPIRED -> "Instagram session expired"
            ProfileLockReason.NOT_FOLLOWING -> "This account is private"
            ProfileLockReason.RATE_LIMITED -> "Instagram is rate limiting"
            ProfileLockReason.NETWORK -> "Couldn't reach Instagram"
            ProfileLockReason.BLOCKED -> "Private content unavailable"
            ProfileLockReason.UNKNOWN -> "This account is private"
        }
        val message = when (reason) {
            ProfileLockReason.HIDDEN ->
                "Private profiles and their cached posts are locked. Unlock with your device biometric or screen lock."
            ProfileLockReason.SIGNED_OUT ->
                "Private accounts only show posts to signed-in followers. Sign in to Instagram in Peek, then refresh."
            ProfileLockReason.SESSION_EXPIRED ->
                "Your saved Instagram session is no longer accepted. Reconnect in Settings → Instagram account."
            ProfileLockReason.NOT_FOLLOWING ->
                "Peek is signed in, but Instagram did not reveal @$username's posts. This usually means the session does not follow that account. Follow it on Instagram, then refresh."
            ProfileLockReason.RATE_LIMITED ->
                error ?: "Instagram is asking Peek to slow down. Try again later or open the rate-limit dashboard."
            ProfileLockReason.NETWORK ->
                "Check your connection and try again. Cached public content still works."
            ProfileLockReason.BLOCKED ->
                error ?: "Instagram didn't return this private account's posts to this session."
            ProfileLockReason.UNKNOWN ->
                "Peek couldn't read this private account. Refresh to retry, or reconnect your Instagram session."
        }
        ProfileDiagnosis(
            locked = true,
            reason = reason,
            title = title,
            message = message,
            detail = listOfNotNull(error, currentAccount.lastError).distinct().joinToString(" · ").ifBlank { null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDiagnosis())

    val downloadProgress = repo.downloadProgress
    private val queue = ServiceLocator.downloadQueue

    val bulkDownload: StateFlow<ProfileDownloadState?> = ServiceLocator.profileDownloadManager.states
        .map { it[username] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tab = MutableStateFlow(ProfileTab.POSTS)
    val tab: StateFlow<ProfileTab> = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(ProfileFilters())
    val filters: StateFlow<ProfileFilters> = _filters.asStateFlow()

    private val _visitBaseline = MutableStateFlow(0L)
    val visitBaseline: StateFlow<Long> = _visitBaseline.asStateFlow()

    val newSinceVisit: StateFlow<Int> = combine(data, _visitBaseline) { value, baseline ->
        if (baseline <= 0L) 0
        else value?.media?.count { (it.takenAt ?: 0L) > baseline } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val visibleMedia: StateFlow<List<MediaEntity>> = combine(
        data,
        _tab,
        _query,
        _filters,
        _visitBaseline,
    ) { value, tab, rawQuery, filters, baseline ->
        val now = System.currentTimeMillis()
        val all = value?.media.orEmpty()
        var media = when (filters.type) {
            ProfileMediaType.REELS -> all.filter { it.isReel }
            ProfileMediaType.PHOTOS -> all.filter { !it.isVideo && !it.isCarousel }
            ProfileMediaType.VIDEOS -> all.filter { it.isVideo }
            ProfileMediaType.CAROUSELS -> all.filter { it.isCarousel }
            ProfileMediaType.ALL -> when (tab) {
                ProfileTab.POSTS -> all.filter { !it.isReel }
                ProfileTab.REELS -> all.filter { it.isReel }
                ProfileTab.SAVED -> all.filter { it.isSaved }
            }
        }
        val query = rawQuery.trim()
        if (query.isNotEmpty()) {
            media = media.filter { item ->
                val haystack = buildString {
                    append(item.caption.orEmpty()).append(' ')
                    append(item.locationName.orEmpty()).append(' ')
                    append(item.audioTitle.orEmpty()).append(' ')
                    append(item.audioArtist.orEmpty()).append(' ')
                    append(item.accessibilityCaption.orEmpty()).append(' ')
                    item.takenAt?.let { seconds ->
                        append(java.time.Instant.ofEpochSecond(seconds).toString()).append(' ')
                    }
                }.lowercase()
                query.lowercase().split(Regex("\\s+")).all { haystack.contains(it) }
            }
        }
        if (filters.onlyNew && baseline > 0L) {
            media = media.filter { (it.takenAt ?: 0L) > baseline }
        }
        media = when (filters.type) {
            ProfileMediaType.ALL -> media
            ProfileMediaType.PHOTOS -> media.filter { !it.isVideo && !it.isCarousel }
            ProfileMediaType.VIDEOS -> media.filter { it.isVideo }
            ProfileMediaType.REELS -> media.filter { it.isReel }
            ProfileMediaType.CAROUSELS -> media.filter { it.isCarousel }
        }
        val cutoff = when (filters.date) {
            ProfileDateRange.ANY -> null
            ProfileDateRange.WEEK -> (now / 1000L) - 7L * 24 * 60 * 60
            ProfileDateRange.MONTH -> (now / 1000L) - 30L * 24 * 60 * 60
            ProfileDateRange.YEAR -> (now / 1000L) - 365L * 24 * 60 * 60
        }
        if (cutoff != null) {
            media = media.filter { (it.takenAt ?: 0L) >= cutoff }
        }
        media = when (filters.sort) {
            ProfileSort.NEWEST -> media.sortedByDescending { it.takenAt ?: 0L }
            ProfileSort.OLDEST -> media.sortedBy { it.takenAt ?: Long.MAX_VALUE }
            ProfileSort.MOST_LIKED -> media.sortedByDescending { it.likeCount ?: 0L }
        }
        media
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
            val previousVisit = ServiceLocator.libraryStore.lastVisit(username) / 1000L
            _visitBaseline.value = previousVisit
            ServiceLocator.libraryStore.markVisited(username, System.currentTimeMillis())
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

    fun setQuery(value: String) {
        _query.value = value.take(80)
    }

    fun setFilters(filters: ProfileFilters) {
        _filters.value = filters
    }

    fun clearFilters() {
        _query.value = ""
        _filters.value = ProfileFilters()
    }

    fun markSeen() {
        val now = System.currentTimeMillis()
        _visitBaseline.value = now / 1000L
        viewModelScope.launch { ServiceLocator.libraryStore.markVisited(username, now) }
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
        viewModelScope.launch {
            val blocked = queue.enqueueChecked(code)
            _messages.tryEmit(blocked ?: "Added to the download queue")
        }
    }

    fun downloadProfile(saveToGallery: Boolean) {
        val posts = data.value?.media.orEmpty()
        if (posts.isEmpty()) {
            _messages.tryEmit("No posts are loaded yet. Refresh, then try again.")
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
                "Loading the full profile, then downloading it to your gallery"
            } else {
                "Loading the full profile, then downloading it offline"
            },
        )
    }

    fun clearBulkDownload() = ServiceLocator.profileDownloadManager.clear(username)

    /** Refresh the first page, then queue only posts that are not already offline. */
    fun downloadNewPosts(saveToGallery: Boolean) {
        viewModelScope.launch {
            _messages.emit("Checking for new posts…")
            runCatching {
                repo.refreshProfile(username)
                val newPosts = repo.profileMedia(username).filter { !it.isDownloaded }
                if (newPosts.isEmpty()) {
                    _messages.emit("No new posts to download")
                    return@runCatching
                }
                ServiceLocator.profileDownloadManager.start(
                    username = username,
                    codes = newPosts.map { it.code },
                    saveToGallery = saveToGallery,
                )
                _messages.emit("Queued ${newPosts.size} new ${if (newPosts.size == 1) "post" else "posts"}")
            }.onFailure { _messages.emit(it.userMessage()) }
        }
    }

    fun downloadCodes(codes: List<String>) {
        if (codes.isEmpty()) return
        viewModelScope.launch {
            var blocked: String? = null
            codes.forEach { code ->
                val reason = queue.enqueueChecked(code)
                if (reason != null) blocked = reason
            }
            _messages.emit(
                blocked ?: "Queued ${codes.size} ${if (codes.size == 1) "post" else "posts"}",
            )
        }
    }

    fun saveCodesToGallery(codes: List<String>) {
        if (codes.isEmpty()) return
        viewModelScope.launch {
            _messages.emit("Preparing ${codes.size} gallery ${if (codes.size == 1) "copy" else "copies"}…")
            var files = 0
            var failed = 0
            codes.forEach { code ->
                runCatching { repo.saveToGallery(code) }
                    .onSuccess { files += it.total }
                    .onFailure { failed++ }
            }
            _messages.emit(
                if (failed == 0) "Saved $files ${if (files == 1) "file" else "files"} to the gallery"
                else "Saved $files files · $failed posts failed",
            )
        }
    }

    fun exportSelection(uri: android.net.Uri, codes: List<String>, html: Boolean) {
        viewModelScope.launch {
            _messages.emit("Writing archive…")
            runCatching { ServiceLocator.backupManager.exportMediaArchive(uri, codes, html) }
                .onSuccess { _messages.emit(if (html) "HTML archive exported" else "JSON archive exported") }
                .onFailure { _messages.emit(it.userMessage()) }
        }
    }

    fun exportProfileArchive(uri: android.net.Uri, html: Boolean, since: Long) {
        viewModelScope.launch {
            _messages.emit("Writing profile archive…")
            runCatching {
                ServiceLocator.backupManager.exportProfileArchive(uri, username, html, since)
                ServiceLocator.libraryStore.markArchive(username, System.currentTimeMillis())
            }.onSuccess {
                _messages.emit(if (html) "HTML archive exported" else "JSON archive exported")
            }.onFailure { _messages.emit(it.userMessage()) }
        }
    }

    fun addSelectionToCollection(id: String, codes: List<String>) {
        viewModelScope.launch {
            ServiceLocator.libraryStore.addToCollection(id, codes)
            _messages.emit("Added ${codes.size} ${if (codes.size == 1) "post" else "posts"} to the collection")
        }
    }

    fun createCollectionWithSelection(name: String, codes: List<String>) {
        viewModelScope.launch {
            ServiceLocator.libraryStore.createCollection(name, codes)
            _messages.emit("Collection created")
        }
    }

    fun clearSelection() {
        // Selection is intentionally screen state; this hook documents the action.
    }

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
