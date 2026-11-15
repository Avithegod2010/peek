package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.avi.peek.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.LocalCircle
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.rememberPrivateUnlock
import com.avi.peek.util.PrivateAccess
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

enum class PrivateFeedType(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    REELS("Reels"),
    CAROUSELS("Carousels"),
}

enum class PrivateFeedSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    MOST_LIKED("Most liked"),
}

data class PrivateFeedFilters(
    val type: PrivateFeedType = PrivateFeedType.ALL,
    val sort: PrivateFeedSort = PrivateFeedSort.NEWEST,
    val newOnly: Boolean = false,
    val circleId: String? = null,
)

class PrivateFeedViewModel : ViewModel() {
    private val repo = ServiceLocator.repository
    private val store = ServiceLocator.libraryStore

    val settings: StateFlow<PeekSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekSettings())
    val circles: StateFlow<List<LocalCircle>> = store.circles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filters = MutableStateFlow(PrivateFeedFilters())
    val filters: StateFlow<PrivateFeedFilters> = _filters.asStateFlow()
    private val _baseline = MutableStateFlow(0L)
    val baseline: StateFlow<Long> = _baseline.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            _baseline.value = store.lastVisit(PRIVATE_VISIT_KEY) / 1000L
            store.markVisited(PRIVATE_VISIT_KEY, System.currentTimeMillis())
        }
    }

    val newCount: StateFlow<Int> = combine(repo.privateFeed, _baseline) { media, baseline ->
        if (baseline <= 0L) 0 else media.count { (it.takenAt ?: 0L) > baseline }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val media: StateFlow<List<MediaEntity>> = combine(
        repo.privateFeed,
        _query,
        _filters,
        _baseline,
        circles,
    ) { all, rawQuery, filters, baseline, circleList ->
        var items = all
        filters.circleId?.let { id ->
            val usernames = circleList.firstOrNull { it.id == id }?.usernames.orEmpty().toSet()
            if (usernames.isNotEmpty()) items = items.filter { it.username in usernames }
        }
        val query = rawQuery.trim()
        if (query.isNotEmpty()) {
            items = items.filter { item ->
                buildString {
                    append(item.caption.orEmpty()).append(' ')
                    append(item.username).append(' ')
                    append(item.locationName.orEmpty()).append(' ')
                    append(item.audioTitle.orEmpty())
                }.lowercase().contains(query.lowercase())
            }
        }
        if (filters.newOnly && baseline > 0L) {
            items = items.filter { (it.takenAt ?: 0L) > baseline }
        }
        items = when (filters.type) {
            PrivateFeedType.ALL -> items
            PrivateFeedType.PHOTOS -> items.filter { !it.isVideo && !it.isCarousel }
            PrivateFeedType.VIDEOS -> items.filter { it.isVideo }
            PrivateFeedType.REELS -> items.filter { it.isReel }
            PrivateFeedType.CAROUSELS -> items.filter { it.isCarousel }
        }
        when (filters.sort) {
            PrivateFeedSort.NEWEST -> items.sortedByDescending { it.takenAt ?: 0L }
            PrivateFeedSort.OLDEST -> items.sortedBy { it.takenAt ?: Long.MAX_VALUE }
            PrivateFeedSort.MOST_LIKED -> items.sortedByDescending { it.likeCount ?: 0L }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) { _query.value = value.take(80) }
    fun setFilters(value: PrivateFeedFilters) { _filters.value = value }
    fun markSeen() {
        val now = System.currentTimeMillis()
        _baseline.value = now / 1000L
        viewModelScope.launch { store.markVisited(PRIVATE_VISIT_KEY, now) }
    }

    fun download(codes: List<String>) {
        if (codes.isEmpty()) return
        viewModelScope.launch {
            var blocked: String? = null
            codes.forEach { code ->
                val reason = ServiceLocator.downloadQueue.enqueueChecked(code)
                if (reason != null) blocked = reason
            }
            _messages.emit(blocked ?: "Queued ${codes.size} ${if (codes.size == 1) "post" else "posts"}")
        }
    }

    fun addToCollection(collectionId: String, codes: List<String>) {
        if (codes.isEmpty()) return
        viewModelScope.launch {
            store.addToCollection(collectionId, codes)
            _messages.emit("Added ${codes.size} ${if (codes.size == 1) "post" else "posts"} to the collection")
        }
    }

    fun createCollection(name: String, codes: List<String>) {
        viewModelScope.launch {
            store.createCollection(name, codes)
            _messages.emit("Collection created")
        }
    }

    fun message(text: String) = _messages.tryEmit(text)

    companion object {
        const val PRIVATE_VISIT_KEY = "__private_feed__"
    }
}

@Composable
fun PrivateFeedScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenDiscover: () -> Unit,
    viewModel: PrivateFeedViewModel = viewModel(
        factory = SimpleViewModelFactory { PrivateFeedViewModel() },
    ),
) {
    SecureScreen()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val revealed by PrivateAccess.revealed.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val circles by viewModel.circles.collectAsStateWithLifecycle()
    val newCount by viewModel.newCount.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val unlock = rememberPrivateUnlock { message -> viewModel.message(message) }
    var selectedCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCollections by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf("") }
    val collections by ServiceLocator.libraryStore.collections.collectAsStateWithLifecycle(emptyList())
    val hidden = settings.privateContentHidden && !revealed

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = stringResource(R.string.feed_title),
            subtitle = if (hidden) "Locked" else "${media.size} cached private posts",
            onBack = onBack,
        )
        if (hidden) {
            EmptyState(
                title = "Private content is hidden",
                message = "Your private follows and their cached posts are locked. Unlock with your device biometric or screen lock.",
                icon = Icons.Rounded.Lock,
                actionLabel = "Unlock private content",
                onAction = {
                    unlock {
                        PrivateAccess.reveal()
                        scope.launch {
                            runCatching { ServiceLocator.repository.openPrivateVault() }
                                .onSuccess { viewModel.message("Private content revealed") }
                                .onFailure { viewModel.message(it.message ?: "Couldn't open the private vault") }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else if (media.isEmpty() && query.isBlank() && filters.type == PrivateFeedType.ALL) {
            EmptyState(
                title = "No private posts yet",
                message = "Follow a private account you can already see with your Instagram session. Its cached posts will appear here.",
                icon = Icons.Rounded.LockOpen,
                actionLabel = "Find profiles",
                onAction = onOpenDiscover,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.feed_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    PrivateFeedChip("All", filters.type == PrivateFeedType.ALL) {
                        viewModel.setFilters(filters.copy(type = PrivateFeedType.ALL))
                    }
                    PrivateFeedType.entries.filter { it != PrivateFeedType.ALL }.forEach { type ->
                        PrivateFeedChip(type.label, filters.type == type) {
                            viewModel.setFilters(filters.copy(type = type))
                        }
                    }
                    PrivateFeedChip(
                        if (filters.sort == PrivateFeedSort.NEWEST) "Newest" else filters.sort.label,
                        filters.sort != PrivateFeedSort.NEWEST,
                    ) {
                        val next = when (filters.sort) {
                            PrivateFeedSort.NEWEST -> PrivateFeedSort.OLDEST
                            PrivateFeedSort.OLDEST -> PrivateFeedSort.MOST_LIKED
                            PrivateFeedSort.MOST_LIKED -> PrivateFeedSort.NEWEST
                        }
                        viewModel.setFilters(filters.copy(sort = next))
                    }
                    if (newCount > 0) {
                        PrivateFeedChip("New since visit · $newCount", filters.newOnly) {
                            viewModel.setFilters(filters.copy(newOnly = !filters.newOnly))
                        }
                    }
                }
                if (circles.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 5.dp),
                    ) {
                        PrivateFeedChip("All circles", filters.circleId == null) {
                            viewModel.setFilters(filters.copy(circleId = null))
                        }
                        circles.forEach { circle ->
                            PrivateFeedChip(circle.name, filters.circleId == circle.id) {
                                viewModel.setFilters(filters.copy(circleId = circle.id))
                            }
                        }
                    }
                }
                if (newCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$newCount new since your last visit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::markSeen) {
                            Text(stringResource(R.string.feed_mark_seen))
                        }
                    }
                }
            }
            if (selectedCodes.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedCodes = emptySet() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                    }
                    Text("${selectedCodes.size} selected", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { selectedCodes = media.map { it.code }.toSet() }) { Text("Select all") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.download(selectedCodes.toList())
                            selectedCodes = emptySet()
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Offline")
                    }
                    Button(onClick = { showCollections = true }, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Collect")
                    }
                }
            }
            if (media.isEmpty()) {
                EmptyState(
                    title = "No matches",
                    message = "Try a different search, media type or circle filter.",
                    icon = Icons.Rounded.Search,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(media, key = { it.code }) { item ->
                        MediaGridItem(
                            media = item,
                            selected = item.code in selectedCodes,
                            onClick = {
                                if (selectedCodes.isNotEmpty()) {
                                    selectedCodes = if (item.code in selectedCodes) {
                                        selectedCodes - item.code
                                    } else {
                                        selectedCodes + item.code
                                    }
                                } else {
                                    onOpenPost(item.code)
                                }
                            },
                            onLongClick = { selectedCodes = selectedCodes + item.code },
                        )
                    }
                }
            }
        }
    }

    if (showCollections) {
        AlertDialog(
            onDismissRequest = { showCollections = false },
            title = { Text("Add to collection") },
            text = {
                Column {
                    collections.forEach { collection ->
                        Surface(
                            onClick = {
                                viewModel.addToCollection(collection.id, selectedCodes.toList())
                                selectedCodes = emptySet()
                                showCollections = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(collection.name, modifier = Modifier.weight(1f))
                                Text("${collection.codes.size}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCollection,
                        onValueChange = { newCollection = it },
                        label = { Text("New collection") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newCollection.isNotBlank(),
                    onClick = {
                        viewModel.createCollection(newCollection, selectedCodes.toList())
                        newCollection = ""
                        selectedCodes = emptySet()
                        showCollections = false
                    },
                ) { Text("Create & add") }
            },
            dismissButton = { TextButton(onClick = { showCollections = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrivateFeedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}
