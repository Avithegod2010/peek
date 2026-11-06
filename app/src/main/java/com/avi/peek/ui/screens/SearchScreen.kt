package com.avi.peek.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.ShimmerBox
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.compactCount
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    initialQuery: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(
        factory = SimpleViewModelFactory { SearchViewModel(initialQuery) },
    ),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val noResults by viewModel.noResults.collectAsStateWithLifecycle()
    val webIssue by viewModel.webIssue.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val busyFollow by viewModel.busyFollow.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
                IconButton(onClick = onBack) {
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
                                "Name, @username or link",
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
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        if (searching) {
            androidx.compose.material3.LinearProgressIndicator(
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
                            "You don't need to know the exact username. Try a name, a nickname, or paste a profile link. Peek finds the public profile, then reads it directly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "suggestions") {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(
                            "Try a name",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Recent",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearHistory) {
                                    Text("Clear")
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                recent.forEach { entry ->
                                    SearchChip(entry.query, icon = true) {
                                        viewModel.useSuggestion(entry.query)
                                    }
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
                            Icon(
                                Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Name search uses public web results to discover the profile, then Peek reads the public profile directly. No login is used.",
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
                        Text(
                            "People",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (searching) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Searching…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (results.isNotEmpty()) {
                            Text(
                                "${results.size} found",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (webIssue && results.isNotEmpty()) {
                    item(key = "web-issue") {
                        WebIssueNote()
                    }
                }

                items(results, key = { it.username }) { item ->
                    SearchResultCard(
                        item = item,
                        following = following.any { it.username.equals(item.username, true) },
                        busy = busyFollow.contains(item.username),
                        onClick = { onOpenProfile(item.username) },
                        onFollow = { haptics.confirm(); viewModel.follow(item) },
                        onRetry = { viewModel.retry(item) },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (!searching && results.isEmpty() && noResults) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No public profile found",
                            message = "Try the exact @username, a simpler name, or paste the profile link. Private accounts won't show posts.",
                            icon = Icons.Rounded.Search,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }

                if (results.isNotEmpty()) {
                    item(key = "search-footer") {
                        Text(
                            "Profiles are public Instagram pages. Name discovery uses public web search; the profile itself is read directly, without logging in.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
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
            Icon(
                if (icon) Icons.Rounded.History else Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    following: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
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
                Avatar(
                    url = profile?.profilePicUrl,
                    username = item.username,
                    size = 58.dp,
                    ring = true,
                )
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
                    Text(
                        "@${item.username}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (profile != null) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (profile.followerCount > 0) {
                                Text(
                                    "${profile.followerCount.compactCount()} followers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            val category = item.category ?: CreatorDirectory.byUsername(item.username)?.category?.label
                            if (!category.isNullOrBlank()) {
                                if (profile.followerCount > 0) Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FollowButton(
                    following = following,
                    busy = busy,
                    onFollow = onFollow,
                )
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
                        Text(
                            bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowButton(
    following: Boolean,
    busy: Boolean,
    onFollow: () -> Unit,
) {
    Surface(
        onClick = onFollow,
        enabled = !following && !busy,
        shape = CircleShape,
        color = if (following) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary,
        contentColor = if (following) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 1.8.dp, modifier = Modifier.size(13.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(
                    if (following) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (following) "Following" else "Follow",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
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
