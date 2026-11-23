package com.avi.peek.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avi.peek.data.directory.CreatorCategory
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.local.SavedSearch
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

enum class FollowerRange(val label: String, val min: Long, val max: Long?) {
    ANY("Any", 0, null),
    UNDER_1M("Under 1M", 0, 999_999),
    ONE_TO_10M("1M – 10M", 1_000_000, 9_999_999),
    TEN_TO_100M("10M – 100M", 10_000_000, 99_999_999),
    OVER_100M("100M+", 100_000_000, null),
}

data class SearchFilters(
    val category: CreatorCategory? = null,
    val verifiedOnly: Boolean = false,
    val followerRange: FollowerRange = FollowerRange.ANY,
    val country: String? = null,
) {
    val activeCount: Int
        get() = listOf(
            category != null,
            verifiedOnly,
            followerRange != FollowerRange.ANY,
            country != null,
        ).count { it }

    fun matches(item: SearchResultItem): Boolean {
        if (category != null && item.category != category) return false
        if (verifiedOnly && item.profile?.isVerified != true) return false
        if (country != null && !item.country.equals(country, ignoreCase = true)) return false
        if (followerRange != FollowerRange.ANY) {
            val followers = item.profile?.followerCount ?: return false
            if (followers < followerRange.min) return false
            if (followerRange.max != null && followers > followerRange.max) return false
        }
        return true
    }
}

data class SearchResultItem(
    val username: String,
    val displayName: String,
    val category: CreatorCategory? = null,
    val country: String? = null,
    val fromCatalog: Boolean = false,
    val fromCache: Boolean = false,
    val fromLink: Boolean = false,
    val profile: ProfileEntity? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

class SearchViewModel(initialQuery: String = "") : ViewModel() {

    private val repo = ServiceLocator.repository
    private val webSearch = ServiceLocator.globalSearch
    private val searchStore = ServiceLocator.searchStore

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val results: StateFlow<List<SearchResultItem>> = _results.asStateFlow()

    private val _cachedMedia = MutableStateFlow<List<MediaEntity>>(emptyList())
    val cachedMedia: StateFlow<List<MediaEntity>> = _cachedMedia.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

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

    val savedSearches = searchStore.saved
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val suggestions: List<String> = CreatorDirectory.suggestedSearches
    val countries: List<String> = CreatorDirectory.countries

    private var searchJob: Job? = null
    private var generation = 0

    init {
        if (initialQuery.isNotBlank()) submit(initialQuery)
    }

    fun updateQuery(value: String) {
        _query.value = value
        if (value.isBlank()) {
            _results.value = emptyList()
            _cachedMedia.value = emptyList()
            _noResults.value = false
            _webIssue.value = false
        }
    }

    fun setFilters(filters: SearchFilters) {
        _filters.value = filters
    }

    fun clearFilters() {
        _filters.value = SearchFilters()
    }

    fun submit(raw: String = _query.value) {
        val term = raw.trim()
        if (term.isBlank()) return
        _query.value = term
        searchJob?.cancel()
        val myGeneration = ++generation
        _cachedMedia.value = emptyList()

        searchJob = viewModelScope.launch {
            _searching.value = true
            _noResults.value = false
            _webIssue.value = false

            val isPostLink = repo.extractPostShortcode(term) != null &&
                (term.contains("/p/") || term.contains("/reel/") || term.contains("/reels/") || term.contains("/tv/"))

            if (isPostLink) {
                val profile = runCatching { repo.resolvePostAuthor(term) }.getOrNull()
                if (myGeneration == generation) {
                    _results.value = profile?.let {
                        listOf(itemFromProfile(it, fromLink = true))
                    }.orEmpty()
                    _searching.value = false
                    _noResults.value = profile == null
                    if (profile == null) _messages.emit("Couldn't resolve that post link")
                    repo.saveProfileLinkToHistory(term)
                }
                return@launch
            }

            val local = CreatorDirectory.search(term, limit = 6)
            val alias = savedSearches.value.firstOrNull {
                it.query.equals(term, ignoreCase = true) && !it.targetUsername.isNullOrBlank()
            }?.targetUsername
            val directUsername = GuestInstagramClient.normalizeUsername(term)
                ?.takeIf {
                    local.isEmpty() &&
                        (!term.contains(' ') || term.startsWith("@") || term.contains("instagram.com"))
                }
            val cachedProfiles = repo.searchCachedProfiles(term)
            val cachedMedia = repo.searchCachedMedia(term)
            _cachedMedia.value = cachedMedia

            val initial = ArrayList<SearchResultItem>()
            alias?.let { username ->
                initial += SearchResultItem(
                    username = username,
                    displayName = CreatorDirectory.byUsername(username)?.name ?: username.replaceFirstChar { it.uppercase() },
                    category = CreatorDirectory.byUsername(username)?.category,
                    country = CreatorDirectory.countryFor(username),
                    loading = true,
                )
            }
            local.forEach { creator ->
                initial += SearchResultItem(
                    username = creator.username,
                    displayName = creator.name,
                    category = creator.category,
                    country = CreatorDirectory.countryFor(creator.username),
                    fromCatalog = true,
                    loading = true,
                )
            }
            directUsername?.let { username ->
                initial += SearchResultItem(
                    username = username,
                    displayName = username.replaceFirstChar { it.uppercase() },
                    category = CreatorDirectory.byUsername(username)?.category,
                    country = CreatorDirectory.countryFor(username),
                    loading = true,
                )
            }
            cachedProfiles.forEach { profile ->
                initial += itemFromProfile(profile, fromCache = true)
            }
            val deduped = initial.distinctBy { it.username }.take(10)
            _results.value = deduped

            val webDeferred = async {
                runCatching { webSearch.search(term, limit = 8) }
                    .onFailure { if (myGeneration == generation) _webIssue.value = true }
                    .getOrDefault(emptyList())
            }

            hydrate(deduped.filter { it.loading }, myGeneration)
            if (myGeneration != generation) return@launch

            val webHits = webDeferred.await()
            if (myGeneration != generation) return@launch
            val webItems = buildList {
                val known = _results.value.map { it.username }.toMutableSet()
                webHits.forEach { hit ->
                    if (known.add(hit.username)) {
                        val catalog = CreatorDirectory.byUsername(hit.username)
                        add(
                            SearchResultItem(
                                username = hit.username,
                                displayName = hit.displayName.ifBlank { catalog?.name ?: hit.username },
                                category = catalog?.category,
                                country = CreatorDirectory.countryFor(hit.username),
                                loading = true,
                            ),
                        )
                    }
                }
            }.take((10 - _results.value.size).coerceAtLeast(0))

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

    private fun itemFromProfile(
        profile: ProfileEntity,
        fromCache: Boolean = false,
        fromLink: Boolean = false,
    ): SearchResultItem {
        val catalog = CreatorDirectory.byUsername(profile.username)
        return SearchResultItem(
            username = profile.username,
            displayName = profile.fullName.ifBlank { catalog?.name ?: profile.username },
            category = catalog?.category,
            country = CreatorDirectory.countryFor(profile.username),
            fromCatalog = catalog != null,
            fromCache = fromCache,
            fromLink = fromLink,
            profile = profile,
            loading = false,
            failed = false,
        )
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
                                    val catalog = CreatorDirectory.byUsername(it.username)
                                    it.copy(
                                        profile = profile,
                                        displayName = profile?.fullName?.takeIf { name -> name.isNotBlank() }
                                            ?: it.displayName,
                                        category = catalog?.category ?: it.category,
                                        country = CreatorDirectory.countryFor(it.username) ?: it.country,
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
                if (!_query.value.equals(item.username, ignoreCase = true)) {
                    searchStore.save(_query.value, item.username)
                }
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

    fun saveCurrentSearch() {
        viewModelScope.launch {
            searchStore.save(_query.value)
            _messages.emit("Saved search")
        }
    }

    fun saveAlias(item: SearchResultItem) {
        viewModelScope.launch {
            searchStore.save(_query.value, item.username)
            _messages.emit("Alias saved: “${_query.value}” → @${item.username}")
        }
    }

    fun removeSaved(query: String) {
        viewModelScope.launch { searchStore.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearSearchHistory() }
    }

    fun useSuggestion(value: String) {
        _query.value = value
        submit(value)
    }

    fun runSaved(saved: SavedSearch) {
        if (saved.targetUsername != null) {
            _query.value = saved.query
            submit(saved.query)
        } else {
            _query.value = saved.query
            submit(saved.query)
        }
    }
}
