package com.avi.peek.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.repo.FeedPost
import com.avi.peek.data.repo.SyncState
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.FeedPostCard
import com.avi.peek.ui.components.LocalReduceMotion
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.OfflineBanner
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.SearchEntry
import com.avi.peek.ui.components.ShimmerBox
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.copyText
import com.avi.peek.util.instagramPostUrl
import com.avi.peek.util.instagramWebUrl
import com.avi.peek.util.openInstagram
import com.avi.peek.util.shareText

@Composable
fun HomeScreen(
    settings: PeekSettings,
    online: Boolean,
    syncState: SyncState,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenStories: (String) -> Unit = onOpenProfile,
    onOpenDiscover: () -> Unit,
    onOpenSearch: () -> Unit,
    storyAvatarModifier: (String) -> Modifier = { Modifier },
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val following by viewModel.following.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadProgress.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.autoRefreshIfNeeded()
    }

    PullToRefreshBox(
        isRefreshing = syncState.running,
        onRefresh = { viewModel.refresh(); haptics.tap() },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                HomeHeader(
                    followingCount = following.size,
                    syncState = syncState,
                    onRefresh = { viewModel.refresh() },
                    onAdd = onOpenDiscover,
                )
            }
            item(key = "search") {
                SearchEntry(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    onClick = { haptics.tap(); onOpenSearch() },
                )
            }
            item(key = "offline") {
                OfflineBanner(visible = !online)
            }
            if (following.isNotEmpty()) {
                item(key = "circle-label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Your circle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${following.size} ${if (following.size == 1) "follow" else "follows"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "stories") {
                    StoryStrip(
                        profiles = following,
                        onOpenProfile = onOpenProfile,
                        onOpenStories = onOpenStories,
                        onAdd = onOpenDiscover,
                        avatarModifier = storyAvatarModifier,
                    )
                }
            }
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterPill("All", filter == FeedFilter.ALL, Icons.Rounded.AutoAwesome) {
                        viewModel.setFilter(FeedFilter.ALL); haptics.tap()
                    }
                    FilterPill("Photos", filter == FeedFilter.PHOTOS, Icons.Rounded.Collections) {
                        viewModel.setFilter(FeedFilter.PHOTOS); haptics.tap()
                    }
                    FilterPill("Reels", filter == FeedFilter.REELS, Icons.Rounded.Movie) {
                        viewModel.setFilter(FeedFilter.REELS); haptics.tap()
                    }
                    FilterPill("Videos", filter == FeedFilter.VIDEOS, Icons.Rounded.Movie) {
                        viewModel.setFilter(FeedFilter.VIDEOS); haptics.tap()
                    }
                }
            }

            if (feed.isEmpty()) {
                item(key = "empty") {
                    if (following.isEmpty()) {
                        EmptyState(
                            title = "Your circle is empty",
                            message = "Add any public Instagram profile. Peek follows them locally — public browsing needs no login and there is no algorithm.",
                            icon = Icons.Rounded.Visibility,
                            actionLabel = "Find people",
                            onAction = onOpenDiscover,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    } else if (syncState.running) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                            repeat(2) { HomeShimmerCard() }
                        }
                    } else {
                        EmptyState(
                            title = "Nothing here yet",
                            message = "You're following ${following.size} ${if (following.size == 1) "profile" else "profiles"}. Pull to refresh and Peek will cache their latest posts.",
                            icon = Icons.Rounded.Refresh,
                            actionLabel = "Refresh now",
                            onAction = { viewModel.refresh() },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            } else {
                items(feed, key = { it.media.code }) { post ->
                    FeedPostCard(
                        post = post,
                        hideLikeCounts = settings.hideLikeCounts,
                        downloadProgress = downloads[post.media.code],
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .animateItem(),
                        onOpenPost = { onOpenPost(post.media.code) },
                        onOpenProfile = { onOpenProfile(post.profile.username) },
                        onToggleLike = { viewModel.toggleLike(post.media.code) },
                        onToggleSave = { viewModel.toggleSave(post.media.code) },
                        onDownload = { viewModel.download(post.media.code) },
                        onShare = {
                            context.shareText(
                                "https://www.instagram.com/p/${post.media.code}/",
                                "Share post",
                            )
                        },
                        onOpenInstagram = {
                            context.openInstagram("p/${post.media.code}/")
                        },
                        onCopyLink = {
                            context.copyText(post.media.code.instagramPostUrl())
                            viewModel.postMessage("Link copied")
                        },
                    )
                }
                item(key = "footer") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "You're all caught up. Cached locally, never tracked.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    followingCount: Int,
    syncState: SyncState,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "refresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (reduce) 4000 else 1200)),
        label = "rotation",
    )

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(27.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Peek", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (followingCount == 0) "Your quiet Instagram" else "$followingCount in your quiet circle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add profile",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRefresh, enabled = !syncState.running) {
                if (syncState.running) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(21.dp)
                            .rotate(rotation),
                    )
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        }
        AnimatedVisibility(visible = syncState.running) {
            Column {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = syncState.label.ifBlank { "Syncing" } + if (syncState.total > 0) "  ${syncState.current}/${syncState.total}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        if (syncState.total == 0) 0f
                        else (syncState.current.toFloat() / syncState.total).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                )
            }
        }
    }
}

@Composable
private fun StoryStrip(
    profiles: List<ProfileEntity>,
    onOpenProfile: (String) -> Unit,
    onOpenStories: (String) -> Unit,
    onAdd: () -> Unit,
    avatarModifier: (String) -> Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "add") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(1.2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add profile", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Text("Add", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        items(profiles, key = { it.username }) { profile ->
            val unread = (profile.latestReelMedia ?: 0L) > ((profile.lastViewedAt ?: 0L) / 1000L)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(66.dp)
                    .clickable {
                        if ((profile.latestReelMedia ?: 0L) > 0L) {
                            onOpenStories(profile.username)
                        } else {
                            onOpenProfile(profile.username)
                        }
                    },
            ) {
                Avatar(
                    url = profile.profilePicUrl,
                    username = profile.username,
                    size = 64.dp,
                    modifier = avatarModifier(profile.username),
                    ring = true,
                    ringUnread = unread,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    profile.username,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeShimmerCard() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(38.dp), RoundedCornerShape(19.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    ShimmerBox(Modifier.width(120.dp).height(12.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(Modifier.width(80.dp).height(9.dp), RoundedCornerShape(5.dp))
                }
            }
            ShimmerBox(Modifier.fillMaxWidth().aspectRatio(4f / 5f), RoundedCornerShape(24.dp))
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { ShimmerBox(Modifier.size(22.dp), RoundedCornerShape(11.dp)) }
            }
        }
    }
}

