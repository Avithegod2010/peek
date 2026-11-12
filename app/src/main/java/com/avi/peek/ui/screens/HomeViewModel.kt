package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.repo.FeedPost
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedFilter { ALL, PHOTOS, REELS, VIDEOS }

class HomeViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val following = repo.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncState = repo.syncState

    val downloadProgress = repo.downloadProgress
    private val queue = ServiceLocator.downloadQueue

    private val _filter = MutableStateFlow(FeedFilter.ALL)
    val filter: StateFlow<FeedFilter> = _filter.asStateFlow()

    val feed: StateFlow<List<FeedPost>> = combine(repo.feed, _filter) { items, filter ->
        when (filter) {
            FeedFilter.ALL -> items
            FeedFilter.PHOTOS -> items.filter { !it.media.isVideo && !it.media.isReel }
            FeedFilter.REELS -> items.filter { it.media.isReel }
            FeedFilter.VIDEOS -> items.filter { it.media.isVideo }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var lastAutoRefresh = 0L

    fun setFilter(value: FeedFilter) {
        _filter.value = value
    }

    fun refresh() {
        viewModelScope.launch { repo.syncAll(force = true) }
    }

    fun autoRefreshIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefresh < 60_000) return
        lastAutoRefresh = now
        viewModelScope.launch {
            if (settings.value.syncOnOpen) {
                repo.syncAll(force = false)
            }
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
}
