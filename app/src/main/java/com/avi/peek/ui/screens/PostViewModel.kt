package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.AccountState
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.remote.ScrapedComment
import com.avi.peek.data.repo.DownloadProgress
import com.avi.peek.data.repo.GallerySaver
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PostViewModel(private val code: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    val media = repo.observeMedia(code)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val downloadProgress: StateFlow<DownloadProgress?> = repo.downloadProgress
        .map { it[code] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val queue = ServiceLocator.downloadQueue

    val account: StateFlow<AccountState> = ServiceLocator.accountManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _comments = kotlinx.coroutines.flow.MutableStateFlow<List<ScrapedComment>>(emptyList())
    val comments: StateFlow<List<ScrapedComment>> = _comments.asStateFlow()

    private val _commentsLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val commentsLoading = _commentsLoading.asStateFlow()

    private val _commentsError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val commentsError = _commentsError.asStateFlow()

    private val _commentsHasMore = kotlinx.coroutines.flow.MutableStateFlow(false)
    val commentsHasMore = _commentsHasMore.asStateFlow()

    private var commentsNext: String? = null
    private var commentsLoaded = false

    private val _loading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    init {
        load(false)
    }

    fun refresh() {
        load(true)
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            runCatching { repo.loadMediaDetail(code, force = force) }
                .onFailure { _messages.emit(it.userMessage()) }
            _loading.value = false
        }
    }

    fun toggleLike() {
        viewModelScope.launch { repo.toggleLike(code) }
    }

    fun toggleSave() {
        viewModelScope.launch { repo.toggleSaved(code) }
    }

    fun loadComments(reset: Boolean = false) {
        if (_commentsLoading.value) return
        if (!reset && commentsLoaded && commentsNext == null) return
        viewModelScope.launch {
            _commentsLoading.value = true
            _commentsError.value = null
            runCatching {
                repo.loadComments(code, after = if (reset) null else commentsNext)
            }.onSuccess { page ->
                _comments.value = if (reset) page.comments else _comments.value + page.comments
                commentsNext = page.nextMaxId
                _commentsHasMore.value = page.nextMaxId != null
                commentsLoaded = true
            }.onFailure {
                _commentsError.value = it.userMessage()
            }
            _commentsLoading.value = false
        }
    }

    fun moreComments() = loadComments(reset = false)

    fun retryComments() {
        commentsLoaded = false
        commentsNext = null
        loadComments(reset = true)
    }

    fun download() {
        viewModelScope.launch {
            val blocked = queue.enqueueChecked(code)
            _messages.tryEmit(blocked ?: "Added to the download queue · Library → Downloads")
        }
    }

    fun saveToGallery() {
        viewModelScope.launch {
            _messages.emit("Preparing the gallery copy…")
            runCatching { repo.saveToGallery(code) }
                .onSuccess { saved ->
                    val where = "Pictures/Peek"
                    _messages.emit(
                        when {
                            saved.images > 0 && saved.videos > 0 ->
                                "Saved ${saved.images} photos and ${saved.videos} video to $where"
                            saved.videos > 0 -> "Saved video to $where"
                            else -> "Saved ${saved.images} ${if (saved.images == 1) "photo" else "photos"} to $where"
                        },
                    )
                }
                .onFailure { error ->
                    _messages.emit(
                        if (error is GallerySaver.PermissionRequired) {
                            "Allow storage access to save pictures to your gallery"
                        } else {
                            error.userMessage()
                        },
                    )
                }
        }
    }

    fun encouragingMessage(text: String) {
        _messages.tryEmit(text)
    }
}
