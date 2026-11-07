package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.repo.FeedPost
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReelsViewModel : ViewModel() {
    private val repo = ServiceLocator.repository
    private val loading = mutableSetOf<String>()

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val reels: StateFlow<List<FeedPost>> = repo.feed
        .map { posts -> posts.filter { it.media.isReel || it.media.isVideo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloads = repo.downloadProgress
    private val queue = ServiceLocator.downloadQueue

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun ensureDetail(code: String) {
        if (!loading.add(code)) return
        viewModelScope.launch {
            runCatching { repo.loadMediaDetail(code) }
                .onFailure { _messages.tryEmit(it.userMessage()) }
            loading.remove(code)
        }
    }

    fun refresh() {
        viewModelScope.launch { repo.syncAll(force = true) }
    }

    fun toggleLike(code: String) {
        viewModelScope.launch { repo.toggleLike(code) }
    }

    fun toggleSave(code: String) {
        viewModelScope.launch { repo.toggleSaved(code) }
    }

    fun download(code: String) {
        val blocked = queue.enqueue(code)
        _messages.tryEmit(blocked ?: "Reel added to the download queue")
    }
}
