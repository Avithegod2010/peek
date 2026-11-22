package com.avi.peek.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.directory.CreatorCategory
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.local.SavedSearch
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.ShimmerBox
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.categoryIcon
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.compactCount
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    initialQuery: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(
        factory = SimpleViewModelFactory { SearchViewModel(initialQuery) },
    ),
) {
    // Search history is private metadata: keep it out of screenshots and the
    // recents thumbnail, like the security center and vault already are.
    SecureScreen()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val cachedMedia by viewModel.cachedMedia.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val noResults by viewModel.noResults.collectAsStateWithLifecycle()
    val webIssue by viewModel.webIssue.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle()
    val busyFollow by viewModel.busyFollow.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showFilters by remember { mutableStateOf(false) }

    val visibleResults = remember(results, filters) {
        results.filter { filters.matches(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        if (initialQuery.isBlank()) {
            delay(240)
            focusRequester.requestFocus()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                "Name, @handle or link",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::updateQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                viewModel.submit()
                            }),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.updateQuery("")
                                focusRequester.requestFocus()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { showFilters = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "Search filters",
                        tint = if (filters.activeCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filters.activeCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 7.dp, end = 5.dp)
                            .size(15.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            filters.activeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(2.dp),
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (query.isBlank()) {
                item(key = "intro") {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Text(
                            "Search by name.",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "You don't need the exact username. Try a name, a nickname, an @handle, or paste a profile or post link. Peek finds the public profile, then reads it directly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "suggestions") {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text("Try a name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            viewModel.suggestions.forEach { suggestion ->
                                SearchChip(suggestion) {
                                    haptics.tap()
                                    viewModel.useSuggestion(suggestion)
                                }
                            }
                        }
                    }
                }
                if (savedSearches.isNotEmpty()) {
                    item(key = "saved") {
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Saved searches", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(
                                    "Tap to run · × to remove",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                savedSearches.forEach { saved ->
                                    SavedSearchChip(
                                        saved = saved,
                                        onClick = { viewModel.runSaved(saved) },
                                        onRemove = { viewModel.removeSaved(saved.query) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Recent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearHistory) { Text("Clear") }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                recent.forEach { entry ->
                                    SearchChip(entry.query, icon = true) { viewModel.useSuggestion(entry.query) }
                                }
                            }
                        }
                    }
                }
                item(key = "tip") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Name search uses public web results to discover a profile, then reads the profile directly. Public search needs no login; if you connect an Instagram session in Settings, only that session is used. Saved aliases keep a name → @username link on this device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item(key = "results-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (filters.activeCount > 0) {
                                Text(
                                    "${visibleResults.size} of ${results.size} match filters",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (searching) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                        } else if (results.isNotEmpty()) {
                            TextButton(onClick = { viewModel.saveCurrentSearch(); haptics.tap() }) {
                                Text("Save search")
                            }
                        }
                    }
                }

                if (webIssue && results.isNotEmpty()) {
                    item(key = "web-issue") {
                        WebIssueNote()
                    }
                }

                if (filters.activeCount > 0 && visibleResults.isEmpty() && results.isNotEmpty()) {
                    item(key = "no-filter-results") {
                        EmptyState(
                            title = "No matches with those filters",
                            message = "Try relaxing the follower range, category or country.",
                            icon = Icons.Rounded.Tune,
                            actionLabel = "Clear filters",
                            onAction = { viewModel.clearFilters() },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                items(visibleResults, key = { it.username }) { item ->
                    SearchResultCard(
                        item = item,
                        following = following.any { it.username.equals(item.username, true) },
                        savedAlias = savedSearches.any {
                            it.query.equals(query, true) && it.targetUsername.equals(item.username, true)
                        },
                        busy = busyFollow.contains(item.username),
                        onClick = { onOpenProfile(item.username) },
                        onFollow = { haptics.confirm(); viewModel.follow(item) },
                        onSaveAlias = { haptics.tap(); viewModel.saveAlias(item) },
                        onRetry = { viewModel.retry(item) },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (cachedMedia.isNotEmpty()) {
                    item(key = "cached-media-header") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                "In your cache",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${cachedMedia.size} posts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "cached-media") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(cachedMedia, key = { "cached-${it.code}" }) { media ->
                                MediaGridItem(
                                    media = media,
                                    modifier = Modifier.width(116.dp),
                                    onClick = { onOpenPost(media.code) },
                                )
                            }
                        }
                    }
                }

                if (!searching && results.isEmpty() && noResults) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No public profile found",
                            message = "Try the exact @username, a simpler name, or paste the profile/post link. Private accounts won't show posts.",
                            icon = Icons.Rounded.Search,
                            modifier = Modifier.padding(top = 20.dp),
                            actionLabel = if (filters.activeCount > 0) "Clear filters" else null,
                            onAction = if (filters.activeCount > 0) viewModel::clearFilters else null,
                        )
                    }
                }

                if (results.isNotEmpty()) {
                    item(key = "search-footer") {
                        Text(
                            "Profiles are public Instagram pages. Name discovery uses public web search; the profile itself is read directly, without logging in. Save an alias to keep a display name linked to an @handle.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = filters,
            countries = viewModel.countries,
            onDismiss = { showFilters = false },
            onApply = {
                viewModel.setFilters(it)
                showFilters = false
            },
        )
    }
}

@Composable
private fun SearchChip(value: String, icon: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (icon) Icons.Rounded.History else Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SavedSearchChip(saved: SavedSearch, onClick: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.BookmarkAdded, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(saved.query, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (saved.targetUsername != null) {
                    Spacer(Modifier.width(4.dp))
                    Text("@${saved.targetUsername}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove saved search", modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    following: Boolean,
    savedAlias: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
    onSaveAlias: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = item.profile
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = profile?.profilePicUrl, username = item.username, size = 58.dp, ring = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile?.fullName?.takeIf { it.isNotBlank() } ?: item.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profile?.isVerified == true) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${item.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        if (profile?.isPrivate == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = "Private account",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                    if (profile != null) {
                        Spacer(Modifier.height(3.dp))
                        if (profile.followerCount > 0) {
                            Text(
                                "${profile.followerCount.compactCount()} followers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                        item.category?.let { category ->
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onSaveAlias, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (savedAlias) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = if (savedAlias) "Alias saved" else "Save alias",
                        tint = if (savedAlias) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                FollowButton(following = following, busy = busy, onFollow = onFollow)
            }
            when {
                item.loading -> {
                    Spacer(Modifier.height(10.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(10.dp), RoundedCornerShape(5.dp))
                    Spacer(Modifier.height(5.dp))
                    ShimmerBox(Modifier.width(220.dp).height(10.dp), RoundedCornerShape(5.dp))
                }
                item.failed -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Preview unavailable. Tap the profile to try again.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
                else -> {
                    val bio = profile?.biography?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
                    if (!bio.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.fromLink || item.fromCache) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                item.fromLink -> "Found from a post link"
                                item.fromCache -> "From your local cache"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowButton(following: Boolean, busy: Boolean, onFollow: () -> Unit) {
    Surface(
        onClick = onFollow,
        enabled = !following && !busy,
        shape = CircleShape,
        color = if (following) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary,
        contentColor = if (following) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.height(34.dp),
    ) {
        Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 1.8.dp, modifier = Modifier.size(13.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (following) Icons.Rounded.Check else Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterSheet(
    filters: SearchFilters,
    countries: List<String>,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember { mutableStateOf(filters.category) }
    var verified by remember { mutableStateOf(filters.verifiedOnly) }
    var range by remember { mutableStateOf(filters.followerRange) }
    var country by remember { mutableStateOf(filters.country) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Text("Search filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Narrow the people list. Filters apply to loaded results.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))

            Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterPill("Any", category == null) { category = null }
                CreatorCategory.entries.filter { it != CreatorCategory.ALL }.forEach { item ->
                    FilterPill(item.label, category == item, icon = item) { category = item }
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Verified only", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Blue check or nothing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = verified, onCheckedChange = { verified = it })
            }
            Spacer(Modifier.height(14.dp))

            Text("Followers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FollowerRange.entries.forEach { item ->
                    FilterPill(item.label, range == item) { range = item }
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("Country", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterPill("Anywhere", country == null) { country = null }
                countries.forEach { item ->
                    FilterPill(item, country == item) { country = item }
                }
            }
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onApply(SearchFilters()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Reset") }
                Button(
                    onClick = {
                        onApply(
                            SearchFilters(
                                category = category,
                                verifiedOnly = verified,
                                followerRange = range,
                                country = country,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    icon: CreatorCategory? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(categoryIcon(icon), contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun WebIssueNote() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Network name search was unavailable. Showing profiles Peek already knows about.",
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
