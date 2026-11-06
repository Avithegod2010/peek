package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.directory.CreatorCategory
import com.avi.peek.data.directory.CreatorDirectory
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

class ExploreViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    val following = repo.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _category = MutableStateFlow(CreatorCategory.ALL)
    val category: StateFlow<CreatorCategory> = _category.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, ProfileEntity>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileEntity>> = _profiles.asStateFlow()

    private val _loading = MutableStateFlow<Set<String>>(emptySet())
    val loading: StateFlow<Set<String>> = _loading.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    private val _busyFollow = MutableStateFlow<Set<String>>(emptySet())
    val busyFollow: StateFlow<Set<String>> = _busyFollow.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val starter: List<PopularCreator> = CreatorDirectory.starter
    val categories: List<CreatorCategory> = CreatorDirectory.creatorCategories

    private val prefetchSemaphore = Semaphore(2)

    init {
        prefetch(starter)
        prefetch(CreatorDirectory.categoryCreators(CreatorCategory.ALL).take(8))
    }

    fun creatorsFor(category: CreatorCategory): List<PopularCreator> {
        return if (category == CreatorCategory.ALL) {
            CreatorDirectory.trending.take(8)
        } else {
            CreatorDirectory.forCategory(category)
        }
    }

    fun selectCategory(value: CreatorCategory) {
        _category.value = value
        prefetch(creatorsFor(value))
    }

    fun refresh() {
        _failed.value = emptySet()
        prefetch(starter)
        prefetch(creatorsFor(_category.value))
    }

    fun prefetch(creators: List<PopularCreator>) {
        val usernames = creators.map { it.username }
        val toLoad = usernames.filter {
            !_loading.value.contains(it) && !_profiles.value.containsKey(it)
        }
        if (toLoad.isEmpty()) return
        _loading.update { it + toLoad }
        toLoad.forEach { username ->
            viewModelScope.launch {
                prefetchSemaphore.withPermit {
                    val profile = runCatching { repo.prefetchDirectoryProfile(username) }.getOrNull()
                    _profiles.update { current ->
                        if (profile != null) current + (username to profile) else current
                    }
                    _failed.update { current ->
                        if (profile == null) current + username else current - username
                    }
                    _loading.update { it - username }
                    delay(180)
                }
            }
        }
    }

    fun follow(creator: PopularCreator) {
        if (_busyFollow.value.contains(creator.username)) return
        viewModelScope.launch {
            _busyFollow.update { it + creator.username }
            try {
                val cached = repo.followCached(creator.username)
                if (!cached) repo.follow(creator.username)
                _messages.emit("@${creator.username} added to your circle")
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _busyFollow.update { it - creator.username }
            }
        }
    }
}
