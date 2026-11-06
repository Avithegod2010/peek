package com.avi.peek.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.local.ProfileWithMedia
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.AnimatedNumber
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.ShimmerBox
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.pressScale
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.copyText
import com.avi.peek.util.instagramWebUrl
import com.avi.peek.util.openInstagram
import com.avi.peek.util.openUrl
import com.avi.peek.util.relativeTime
import com.avi.peek.util.shareText

@Composable
fun ProfileScreen(
    username: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: ProfileViewModel = viewModel(
        key = "profile_$username",
        factory = SimpleViewModelFactory { ProfileViewModel(username) },
    ),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val visibleMedia by viewModel.visibleMedia.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val haptics = LocalPeekHaptics.current
    val gridState = rememberLazyGridState()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val profile = data?.profile
    val showTitle = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 260
    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "topBar",
    )

    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        if (data == null) {
            ProfileLoading()
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { viewModel.refresh(); haptics.tap() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                        ProfileHeader(
                            value = data!!,
                            tab = tab,
                            onTab = {
                                viewModel.setTab(it)
                                haptics.tap()
                            },
                            onFollow = {
                                if (data!!.profile.isFollowed) viewModel.unfollow() else viewModel.follow()
                                haptics.confirm()
                            },
                            onShare = {
                                context.shareText(data!!.profile.username.instagramWebUrl(), "Share profile")
                            },
                            onMenu = { menuOpen = true },
                            onRefresh = viewModel::refresh,
                        )
                    }
                    if (visibleMedia.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            val private = data!!.profile.isPrivate
                            EmptyState(
                                title = when {
                                    private -> "This account is private"
                                    tab == ProfileTab.SAVED -> "Nothing saved yet"
                                    tab == ProfileTab.REELS -> "No reels cached"
                                    else -> "No posts cached"
                                },
                                message = when {
                                    private -> "Peek can follow it locally, but Instagram doesn't show private posts to guests."
                                    tab == ProfileTab.SAVED -> "Tap the bookmark on any post to keep it here."
                                    else -> "Pull to refresh and Peek will cache the latest public posts."
                                },
                                icon = if (private) Icons.Rounded.Lock else Icons.Rounded.Refresh,
                                actionLabel = if (private) null else "Refresh",
                                onAction = if (private) null else viewModel::refresh,
                                modifier = Modifier.padding(vertical = 30.dp),
                            )
                        }
                    } else {
                        items(visibleMedia, key = { it.code }) { media ->
                            MediaGridItem(
                                media = media,
                                modifier = Modifier,
                                onClick = { onOpenPost(media.code) },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.32f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = showTitle, enter = fadeIn(), exit = fadeOut()) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                    Text(
                        text = username,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.32f)) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }
                ProfileMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    profile = profile,
                    onCopy = {
                        context.copyText(username)
                        viewModel.postMessage("Username copied")
                    },
                    onShare = {
                        context.shareText(username.instagramWebUrl(), "Share profile")
                    },
                    onInstagram = { context.openInstagram(username) },
                    onRefresh = viewModel::refresh,
                    onPin = viewModel::togglePinned,
                    onUnfollow = viewModel::unfollow,
                    onRemove = viewModel::removeProfile,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    value: ProfileWithMedia,
    tab: ProfileTab,
    onTab: (ProfileTab) -> Unit,
    onFollow: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
) {
    val profile = value.profile
    val media = value.media
    val context = LocalContext.current
    val haptics = LocalPeekHaptics.current

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(218.dp),
        ) {
            if (profile.profilePicUrl != null) {
                PeekImage(
                    url = profile.profilePicUrl,
                    cacheKey = "cover_bg_${profile.username}",
                    modifier = Modifier.fillMaxSize().blur(34.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-56).dp)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(
                url = profile.profilePicUrl,
                username = profile.username,
                size = 112.dp,
                ring = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.fullName.ifBlank { profile.username },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (profile.isVerified) {
                    Spacer(Modifier.width(5.dp))
                    VerifiedBadge(size = 18.dp)
                }
            }
            Text(
                "@${profile.username}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderStat(media.size.toLong(), "posts")
                HeaderStat(profile.followerCount, "followers")
                HeaderStat(profile.followingCount, "following")
            }
            Spacer(Modifier.height(16.dp))
            if (profile.biography.isNotBlank()) {
                Text(
                    text = profile.biography,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            val links = profile.bioLinks()
            if (links.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    links.take(3).forEach { link ->
                        AssistChip(
                            onClick = { context.openUrl(link.url) },
                            label = {
                                Text(
                                    link.title.ifBlank { link.url.substringAfter("://").take(24) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            shape = CircleShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile.isFollowed) {
                    OutlinedButton(onClick = onFollow, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Following")
                    }
                } else {
                    Button(onClick = onFollow, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Follow locally")
                    }
                }
                OutlinedButton(onClick = onShare, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(onClick = onMenu, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
                }
            }
            if (profile.isPrivate) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Private account · content isn't public", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (profile.lastFetchedAt > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Updated ${(profile.lastFetchedAt / 1000).relativeTime()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        val highlights = profile.highlights()
        if (highlights.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(highlights, key = { it.id }) { highlight ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(70.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .pressScale(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                            .clickable {
                                haptics.tap()
                                context.openInstagram("stories/highlights/${highlight.id}/")
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Avatar(
                            url = highlight.coverUrl,
                            username = highlight.title,
                            size = 62.dp,
                            ring = true,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            highlight.title.ifBlank { "Highlight" },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        ProfileTabs(tab, onTab)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeaderStat(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value, MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileTabs(tab: ProfileTab, onTab: (ProfileTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ProfileTabButton("Posts", tab == ProfileTab.POSTS, Modifier.weight(1f)) { onTab(ProfileTab.POSTS) }
        ProfileTabButton("Reels", tab == ProfileTab.REELS, Modifier.weight(1f)) { onTab(ProfileTab.REELS) }
        ProfileTabButton("Saved", tab == ProfileTab.SAVED, Modifier.weight(1f)) { onTab(ProfileTab.SAVED) }
    }
}

@Composable
private fun ProfileTabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun ProfileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    profile: ProfileEntity?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onInstagram: () -> Unit,
    onRefresh: () -> Unit,
    onPin: () -> Unit,
    onUnfollow: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Refresh profile") },
            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            onClick = { onDismiss(); onRefresh() },
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            onClick = { onDismiss(); onShare() },
        )
        DropdownMenuItem(
            text = { Text("Copy username") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopy() },
        )
        DropdownMenuItem(
            text = { Text("Open on Instagram") },
            leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
            onClick = { onDismiss(); onInstagram() },
        )
        DropdownMenuItem(
            text = { Text(if (profile?.isPinned == true) "Unpin profile" else "Pin to top") },
            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
            onClick = { onDismiss(); onPin() },
        )
        HorizontalDivider()
        if (profile?.isFollowed == true) {
            DropdownMenuItem(
                text = { Text("Unfollow locally") },
                leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null) },
                onClick = { onDismiss(); onUnfollow() },
            )
        }
        DropdownMenuItem(
            text = { Text("Remove from Peek") },
            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            onClick = { onDismiss(); onRemove() },
        )
    }
}

@Composable
private fun ProfileLoading() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(218.dp)) {
            ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        }
        Column(
            Modifier
                .offset(y = (-50).dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShimmerBox(Modifier.size(104.dp), CircleShape)
            Spacer(Modifier.height(14.dp))
            ShimmerBox(Modifier.width(190.dp).height(20.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.width(120.dp).height(12.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.height(24.dp))
            ShimmerBox(Modifier.width(260.dp).height(38.dp), RoundedCornerShape(19.dp))
        }
    }
}
