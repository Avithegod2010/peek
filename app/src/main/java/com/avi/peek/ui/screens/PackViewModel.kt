package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.directory.CreatorPack
import com.avi.peek.data.directory.PackTransfer
import com.avi.peek.data.directory.PopularCreator
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PackViewModel(packId: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    val pack: CreatorPack = if (packId == "imported") {
        CreatorPack(
            id = "imported",
            title = PackTransfer.title,
            subtitle = "Shared with you",
            usernames = PackTransfer.usernames,
        )
    } else {
        CreatorDirectory.packById(packId)
            ?: CreatorPack(packId, "Shared pack", "", PackTransfer.usernames)
    }

    val creators: List<PopularCreator> = pack.usernames.mapNotNull { username ->
        CreatorDirectory.byUsername(username) ?: PopularCreator(
            name = username.replaceFirstChar { it.uppercase() },
            username = username,
            category = com.avi.peek.data.directory.CreatorCategory.ALL,
            tagline = "Shared creator",
            aliases = emptyList(),
        )
    }

    val following = repo.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _profiles = MutableStateFlow<Map<String, ProfileEntity>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileEntity>> = _profiles.asStateFlow()

    private val _loading = MutableStateFlow<Set<String>>(emptySet())
    val loading: StateFlow<Set<String>> = _loading.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val semaphore = Semaphore(2)

    init {
        load(creators.map { it.username })
    }

    private fun load(usernames: List<String>) {
        val toLoad = usernames.filter { !_loading.value.contains(it) && !_profiles.value.containsKey(it) }
        _loading.update { it + toLoad }
        toLoad.forEach { username ->
            viewModelScope.launch {
                semaphore.withPermit {
                    val profile = runCatching { repo.prefetchDirectoryProfile(username) }.getOrNull()
                    if (profile != null) _profiles.update { it + (username to profile) }
                    _loading.update { it - username }
                    delay(160)
                }
            }
        }
    }

    fun follow(creator: PopularCreator) {
        if (_busy.value.contains(creator.username)) return
        viewModelScope.launch {
            _busy.update { it + creator.username }
            try {
                val cached = repo.followCached(creator.username)
                if (!cached) repo.follow(creator.username)
                _messages.emit("@${creator.username} added")
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _busy.update { it - creator.username }
            }
        }
    }

    fun followAll() {
        creators.forEach { follow(it) }
    }
}
