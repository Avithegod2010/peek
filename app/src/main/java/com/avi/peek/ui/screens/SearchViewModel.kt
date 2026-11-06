package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.directory.PopularCreator
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

data class SearchResultItem(
    val username: String,
    val displayName: String,
    val category: String? = null,
    val fromCatalog: Boolean = false,
    val profile: ProfileEntity? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

class SearchViewModel(initialQuery: String = "") : ViewModel() {

    private val repo = ServiceLocator.repository
    private val webSearch = ServiceLocator.globalSearch

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val results: StateFlow<List<SearchResultItem>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _noResults = MutableStateFlow(false)
    val noResults: StateFlow<Boolean> = _noResults.asStateFlow()

    private val _webIssue = MutableStateFlow(false)
    val webIssue: StateFlow<Boolean> = _webIssue.asStateFlow()

    private val _busyFollow = MutableStateFlow<Set<String>>(emptySet())
    val busyFollow: StateFlow<Set<String>> = _busyFollow.asStateFlow()

    val following = repo.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent = repo.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val suggestions: List<String> = CreatorDirectory.suggestedSearches

    private var searchJob: Job? = null
    private var generation = 0

    init {
        if (initialQuery.isNotBlank()) {
            submit(initialQuery)
        }
    }

    fun updateQuery(value: String) {
        _query.value = value
        if (value.isBlank()) {
            _results.value = emptyList()
            _noResults.value = false
            _webIssue.value = false
        }
    }

    fun submit(raw: String = _query.value) {
        val term = raw.trim()
        if (term.isBlank()) return
        _query.value = term
        searchJob?.cancel()
        val myGeneration = ++generation
        searchJob = viewModelScope.launch {
            _searching.value = true
            _noResults.value = false
            _webIssue.value = false

            val local = CreatorDirectory.search(term, limit = 6)
            val directUsername = GuestInstagramClient.normalizeUsername(term)
                ?.takeIf {
                    local.isEmpty() &&
                        (!term.contains(' ') || term.startsWith("@") || term.contains("instagram.com"))
                }
            val direct = directUsername?.let { username ->
                if (local.any { it.username == username }) null
                else SearchResultItem(
                    username = username,
                    displayName = username.replaceFirstChar { it.uppercase() },
                    loading = true,
                )
            }

            val initial = buildList {
                local.forEach { creator ->
                    add(
                        SearchResultItem(
                            username = creator.username,
                            displayName = creator.name,
                            category = creator.category.label,
                            fromCatalog = true,
                            loading = true,
                        ),
                    )
                }
                direct?.let(::add)
            }.distinctBy { it.username }.take(8)

            _results.value = initial

            val webDeferred = async {
                runCatching { webSearch.search(term, limit = 8) }
                    .onFailure { if (myGeneration == generation) _webIssue.value = true }
                    .getOrDefault(emptyList())
            }

            hydrate(initial, myGeneration)
            if (myGeneration != generation) return@launch

            val webHits = webDeferred.await()
            if (myGeneration != generation) return@launch
            val webItems = buildList {
                val known = _results.value.map { it.username }.toMutableSet()
                webHits.forEach { hit ->
                    if (known.add(hit.username)) {
                        add(
                            SearchResultItem(
                                username = hit.username,
                                displayName = hit.displayName,
                                loading = true,
                            ),
                        )
                    }
                }
            }.take((8 - _results.value.size).coerceAtLeast(0))

            if (webItems.isNotEmpty()) {
                _results.update { it + webItems }
                hydrate(webItems, myGeneration)
            }

            if (myGeneration == generation) {
                _searching.value = false
                _noResults.value = _results.value.isEmpty()
                repo.saveProfileLinkToHistory(term)
            }
        }
    }

    private suspend fun hydrate(items: List<SearchResultItem>, myGeneration: Int) = coroutineScope {
        val semaphore = Semaphore(2)
        items.forEach { item ->
            launch {
                semaphore.withPermit {
                    val profile = runCatching { repo.prefetchDirectoryProfile(item.username) }.getOrNull()
                    if (myGeneration == generation) {
                        _results.update { list ->
                            list.map {
                                if (it.username == item.username) {
                                    it.copy(
                                        profile = profile,
                                        displayName = profile?.fullName?.takeIf { name -> name.isNotBlank() } ?: it.displayName,
                                        category = it.category ?: CreatorDirectory.byUsername(it.username)?.category?.label,
                                        loading = false,
                                        failed = profile == null,
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                    }
                    delay(120)
                }
            }
        }
    }

    fun follow(item: SearchResultItem) {
        if (_busyFollow.value.contains(item.username)) return
        viewModelScope.launch {
            _busyFollow.update { it + item.username }
            try {
                val followedFromCache = repo.followCached(item.username)
                if (!followedFromCache) repo.follow(item.username)
                _messages.emit("@${item.username} added to your circle")
            } catch (e: Exception) {
                _messages.emit(e.userMessage())
            } finally {
                _busyFollow.update { it - item.username }
            }
        }
    }

    fun retry(item: SearchResultItem) {
        viewModelScope.launch {
            _results.update { list ->
                list.map { if (it.username == item.username) it.copy(loading = true, failed = false) else it }
            }
            hydrate(listOf(item.copy(loading = true, failed = false)), generation)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearSearchHistory() }
    }

    fun useSuggestion(value: String) {
        _query.value = value
        submit(value)
    }

    fun localMatch(query: String): List<PopularCreator> = CreatorDirectory.search(query, 4)
}
