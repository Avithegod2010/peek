package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.remote.ScrapedProfile
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiscoverViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    val following = repo.following.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = repo.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _preview = MutableStateFlow<ScrapedProfile?>(null)
    val preview = _preview.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _busyUsername = MutableStateFlow<String?>(null)
    val busyUsername = _busyUsername.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun updateQuery(value: String) {
        _query.value = value
        _error.value = null
    }

    fun lookup(raw: String = _query.value) {
        if (raw.isBlank() || _loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val profile = repo.lookup(raw)
                _preview.value = profile
                _query.value = profile.username
                repo.saveProfileLinkToHistory(raw.trim())
            } catch (e: Exception) {
                _preview.value = null
                _error.value = e.userMessage()
            } finally {
                _loading.value = false
            }
        }
    }

    fun followPreview() {
        val profile = _preview.value ?: return
        viewModelScope.launch {
            _busyUsername.value = profile.username
            try {
                repo.followScraped(profile)
                _messages.emit("@${profile.username} added to your circle")
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _busyUsername.value = null
            }
        }
    }

    fun addFromStarter(username: String) {
        _query.value = username
        lookup(username)
    }

    fun unfollow(username: String) {
        viewModelScope.launch {
            repo.unfollow(username)
            _messages.emit("@$username removed from your circle")
        }
    }

    fun addToHistory(value: String) {
        if (value.isBlank()) return
        viewModelScope.launch { repo.saveProfileLinkToHistory(value.trim()) }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearSearchHistory() }
    }

    fun useRecent(value: String) {
        _query.value = value
        lookup(value)
    }
}
