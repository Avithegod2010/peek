package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.ProfileWithMedia
import com.avi.peek.data.local.PeekSettings
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProfileTab { POSTS, REELS, SAVED }

class ProfileViewModel(private val username: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    val data: StateFlow<ProfileWithMedia?> = repo.observeProfile(username)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())

    val downloadProgress = repo.downloadProgress

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

    init {
        viewModelScope.launch {
            repo.markViewed(username)
            val current = repo.observeProfile(username).first()?.profile
            if (current == null || System.currentTimeMillis() - current.lastFetchedAt > 15 * 60 * 1000) {
                refresh()
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
            runCatching { repo.download(code) }
                .onFailure { _messages.emit(it.userMessage()) }
                .onSuccess { _messages.emit("Saved offline") }
        }
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
