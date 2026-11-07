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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.avi.peek.data.repo.ProfileDownloadState
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.AnimatedNumber
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.MiniCreatorCard
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.SectionHeader
import com.avi.peek.ui.components.ShimmerBox
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.pressScale
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.compactCount
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
    onOpenProfile: (String) -> Unit,
    headerAvatarModifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(
        key = "profile_$username",
        factory = SimpleViewModelFactory { ProfileViewModel(username) },
    ),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val visibleMedia by viewModel.visibleMedia.collectAsStateWithLifecycle()
    val relatedProfiles by viewModel.relatedProfiles.collectAsStateWithLifecycle()
    val relatedBusy by viewModel.relatedBusy.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val bulkDownload by viewModel.bulkDownload.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val haptics = LocalPeekHaptics.current
    val gridState = rememberLazyGridState()
    var menuOpen by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var alsoGallery by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val profile = data?.profile
    val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    LaunchedEffect(lastVisibleIndex, profile?.hasMorePosts, loadingMore) {
        val total = gridState.layoutInfo.totalItemsCount
        if (profile?.hasMorePosts == true && !loadingMore && total > 0 && lastVisibleIndex >= total - 4) {
            viewModel.loadMore()
        }
    }
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
                            relatedCreators = viewModel.relatedCreators,
                            relatedProfiles = relatedProfiles,
                            following = following.map { it.username }.toSet(),
                            relatedBusy = relatedBusy,
                            onOpenRelated = onOpenProfile,
                            onFollowRelated = viewModel::followRelated,
                            headerAvatarModifier = headerAvatarModifier,
                            bulkDownload = bulkDownload,
                            onDownloadProfile = { showDownloadDialog = true },
                            onClearBulk = viewModel::clearBulkDownload,
                        )
                    }
                    if (visibleMedia.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            val private = data!!.profile.isPrivate && data!!.media.isEmpty()
                            EmptyState(
                                title = when {
                                    private -> "This account is private"
                                    tab == ProfileTab.SAVED -> "Nothing saved yet"
                                    tab == ProfileTab.REELS -> "No reels cached"
                                    else -> "No posts cached"
                                },
                                message = when {
                                    private -> "Peek can follow it locally, but Instagram doesn't show private posts to guests. If this account is actually public, refresh to re-check."
                                    tab == ProfileTab.SAVED -> "Tap the bookmark on any post to keep it here."
                                    else -> "Pull to refresh and Peek will cache the latest public posts."
                                },
                                icon = if (private) Icons.Rounded.Lock else Icons.Rounded.Refresh,
                                actionLabel = "Refresh",
                                onAction = viewModel::refresh,
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
                    if (profile?.hasMorePosts == true && tab != ProfileTab.SAVED) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load-more") {
                            LoadMoreFooter(
                                loading = loadingMore,
                                loaded = data?.media?.size ?: 0,
                                total = profile.postCount,
                                onLoadMore = viewModel::loadMore,
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

    if (showDownloadDialog) {
        val loadedCount = data?.media?.size ?: 0
        val totalPosts = data?.profile?.postCount?.takeIf { it > 0 } ?: loadedCount.toLong()
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download @$username") },
            text = {
                Column {
                    Text(
                        "Peek will walk every public timeline page it can reach, starting with the " +
                            "${loadedCount.coerceAtLeast(1)} loaded here, then save every photo and video for offline use.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { alsoGallery = !alsoGallery }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(checked = alsoGallery, onCheckedChange = { alsoGallery = it })
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text("Also copy to gallery", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Pictures/Peek and Movies/Peek",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Peek loads pages one at a time and can still be stopped by Instagram rate limits. A large profile can take several minutes and use a lot of storage and data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.downloadProfile(alsoGallery)
                        showDownloadDialog = false
                    },
                    enabled = totalPosts > 0 && bulkDownload?.running != true,
                ) { Text("Download all") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") }
            },
        )
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
    relatedCreators: List<com.avi.peek.data.directory.PopularCreator>,
    relatedProfiles: Map<String, ProfileEntity>,
    following: Set<String>,
    relatedBusy: Set<String>,
    onOpenRelated: (String) -> Unit,
    onFollowRelated: (com.avi.peek.data.directory.PopularCreator) -> Unit,
    headerAvatarModifier: Modifier,
    bulkDownload: ProfileDownloadState?,
    onDownloadProfile: () -> Unit,
    onClearBulk: () -> Unit,
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
                modifier = headerAvatarModifier,
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
                HeaderStat(if (profile.postCount > 0) profile.postCount else media.size.toLong(), "posts")
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
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = onDownloadProfile,
                enabled = media.isNotEmpty() && !profile.isPrivate && bulkDownload?.running != true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (bulkDownload?.running == true && bulkDownload.preparing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Loading public posts · ${bulkDownload.prepared}")
                } else if (bulkDownload?.running == true) {
                    CircularProgressIndicator(
                        progress = {
                            if (bulkDownload.total == 0) 0f
                            else (bulkDownload.downloaded + bulkDownload.failed).toFloat() / bulkDownload.total
                        },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Downloading ${bulkDownload.downloaded + bulkDownload.failed}/${bulkDownload.total}")
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (media.isEmpty()) {
                            "No public posts to save"
                        } else {
                            "Download profile · ${media.size} " + when {
                                profile.hasMorePosts -> "loaded"
                                media.size == 1 -> "post"
                                else -> "posts"
                            }
                        },
                    )
                }
            }
            if (bulkDownload != null && (bulkDownload.running || bulkDownload.finished || bulkDownload.error != null)) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (bulkDownload.running && bulkDownload.preparing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Loading every public page · ${bulkDownload.prepared} posts found",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (bulkDownload.running) {
                            LinearProgressIndicator(
                                progress = {
                                    if (bulkDownload.total == 0) 0f
                                    else (bulkDownload.downloaded + bulkDownload.failed).toFloat() / bulkDownload.total
                                },
                                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Downloading profile · ${bulkDownload.downloaded + bulkDownload.failed}/${bulkDownload.total}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (bulkDownload.saveToGallery) {
                                Text(
                                    "Gallery copies: ${bulkDownload.galleryFiles} files so far",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (bulkDownload.error != null) {
                            Text(
                                "Profile download failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                bulkDownload.error!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onClearBulk) { Text("Dismiss") }
                        } else {
                            Text(
                                "Profile download complete",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${bulkDownload.downloaded} posts saved offline" +
                                    if (bulkDownload.failed > 0) " · ${bulkDownload.failed} failed" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bulkDownload.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (bulkDownload.saveToGallery) {
                                Text(
                                    "${bulkDownload.galleryFiles} files copied to your gallery" +
                                        if (bulkDownload.galleryFailures > 0) " · ${bulkDownload.galleryFailures} gallery errors" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = onClearBulk) { Text("Dismiss") }
                        }
                    }
                }
            }
            if (profile.isPrivate && media.isEmpty()) {
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

        if (relatedCreators.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader(
                title = "More like this",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(9.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(relatedCreators, key = { "related-${it.username}" }) { creator ->
                    MiniCreatorCard(
                        creator = creator,
                        profile = relatedProfiles[creator.username],
                        following = creator.username in following,
                        busy = creator.username in relatedBusy,
                        onClick = { onOpenRelated(creator.username) },
                        onFollow = {
                            haptics.confirm()
                            onFollowRelated(creator)
                        },
                    )
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
private fun LoadMoreFooter(
    loading: Boolean,
    loaded: Int,
    total: Long,
    onLoadMore: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("Loading older posts…", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Load more posts")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (total > 0) "Showing $loaded of ${total.compactCount()}" else "Showing $loaded posts",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
