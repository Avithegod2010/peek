package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.local.PeekSettings
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

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

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

    fun download() {
        val blocked = queue.enqueue(code)
        _messages.tryEmit(blocked ?: "Added to the download queue · Library → Downloads")
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
