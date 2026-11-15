@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.avi.peek.ui.screens

import android.app.PictureInPictureParams
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.repo.DownloadProgress
import com.avi.peek.data.repo.FeedPost
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.DarkSystemBars
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.HeartBurst
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.PeekVideoSurface
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.findActivity
import com.avi.peek.ui.components.rememberPeekPlayer
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.compactCount
import com.avi.peek.util.instagramPostUrl
import com.avi.peek.util.openInstagram
import com.avi.peek.util.relativeTime
import com.avi.peek.util.shareText
import java.io.File

@Composable
fun ReelsScreen(
    onOpenProfile: (String) -> Unit,
    viewModel: ReelsViewModel = viewModel(),
) {
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val haptics = LocalPeekHaptics.current
    var muted by remember { mutableStateOf(settings.muteVideos) }

    LaunchedEffect(settings.muteVideos) { muted = settings.muteVideos }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    if (reels.isEmpty()) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            EmptyState(
                title = "No reels in your circle",
                message = "Reels from the accounts you follow show up here. Refresh your circle or add a creator.",
                icon = Icons.Rounded.Movie,
                actionLabel = "Refresh circle",
                onAction = viewModel::refresh,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    DarkSystemBars()
    val pagerState = rememberPagerState(pageCount = { reels.size })

    LaunchedEffect(pagerState.currentPage, reels.size) {
        reels.getOrNull(pagerState.currentPage)?.let { viewModel.ensureDetail(it.media.code) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val post = reels[page]
            ReelPage(
                post = post,
                active = pagerState.currentPage == page,
                muted = muted,
                autoplay = settings.autoplayReels && !settings.calmMode,
                hideLikeCounts = settings.hideLikeCounts || settings.calmMode,
                downloadProgress = downloads[post.media.code],
                onToggleLike = { viewModel.toggleLike(post.media.code) },
                onToggleSave = { viewModel.toggleSave(post.media.code) },
                onDownload = { viewModel.download(post.media.code) },
                onShare = { context.shareText(post.media.code.instagramPostUrl(), "Share reel") },
                onComment = { context.openInstagram("p/${post.media.code}/") },
                onOpenProfile = { onOpenProfile(post.profile.username) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Reels",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                Text(
                    "from your circle",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (activity != null) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.3f)) {
                    IconButton(
                        onClick = {
                            runCatching {
                                activity.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(9, 16))
                                        .build(),
                                )
                            }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.PictureInPictureAlt,
                            contentDescription = "Picture in picture",
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.3f)) {
                IconButton(onClick = { muted = !muted; haptics.tap() }) {
                    Icon(
                        if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = if (muted) "Unmute" else "Mute",
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.3f)) {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReelPage(
    post: FeedPost,
    active: Boolean,
    muted: Boolean,
    autoplay: Boolean,
    hideLikeCounts: Boolean,
    downloadProgress: DownloadProgress?,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onComment: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val media = post.media
    val haptics = LocalPeekHaptics.current
    var burst by remember(media.code) { mutableIntStateOf(0) }
    var expanded by remember(media.code) { mutableStateOf(false) }

    val children = remember(media.code, media.childrenJson) { media.children() }
    val firstVideo = children.firstOrNull { it.videoUrl != null }?.videoUrl ?: media.videoUrl
    val videoUrl = media.localVideoPath
        ?.takeIf { File(it).exists() }
        ?.let { File(it).toURI().toString() }
        ?: firstVideo
    val player = rememberPeekPlayer(
        url = videoUrl,
        muted = muted,
        active = active,
        autoPlay = autoplay,
    )

    Box(Modifier.fillMaxSize()) {
        PeekImage(
            url = media.displayUrl,
            cacheKey = "reel_cover_${media.code}",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
        )
        if (videoUrl != null) {
            PeekVideoSurface(
                state = player,
                modifier = Modifier.fillMaxSize(),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            )
        } else if (active) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                strokeWidth = 2.5.dp,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(media.code, videoUrl, player) {
                    detectTapGestures(
                        onTap = {
                            player?.toggle()
                            haptics.tap()
                        },
                        onDoubleTap = {
                            if (!media.isLiked) onToggleLike()
                            burst++
                            haptics.heart()
                        },
                    )
                },
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.35f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        HeartBurst(trigger = burst, modifier = Modifier.fillMaxSize())

        if (videoUrl != null && player?.isPlaying == false) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        if (player != null && player.durationMs > 0) {
            val fraction = (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 0.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(Color.White.copy(alpha = 0.9f)),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box {
                Avatar(
                    url = post.profile.profilePicUrl,
                    username = post.profile.username,
                    size = 46.dp,
                    ring = true,
                    onClick = onOpenProfile,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
            ReelAction(
                icon = if (media.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (media.isLiked) "Liked" else "Like",
                tint = if (media.isLiked) Color(0xFFFF6B81) else Color.White,
                onClick = { onToggleLike(); haptics.toggle() },
            )
            ReelAction(Icons.Rounded.ChatBubbleOutline, "Comments", Color.White, onComment)
            ReelAction(Icons.Rounded.Share, "Share", Color.White, onShare)
            ReelAction(
                if (media.isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                "Save",
                if (media.isSaved) MaterialTheme.colorScheme.primary else Color.White,
            ) { onToggleSave(); haptics.toggle() }
            if (downloadProgress != null) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { downloadProgress.fraction },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                ReelAction(
                    if (media.isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    "Offline",
                    if (media.isDownloaded) MaterialTheme.colorScheme.primary else Color.White,
                ) { if (!media.isDownloaded) onDownload() }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 84.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    url = post.profile.profilePicUrl,
                    username = post.profile.username,
                    size = 32.dp,
                    onClick = onOpenProfile,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    post.profile.username,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (post.profile.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "· ${(media.takenAt ?: media.fetchedAt / 1000).relativeTime()}",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val caption = media.caption?.trim().orEmpty()
            if (caption.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
            if (!media.audioTitle.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        listOfNotNull(media.audioTitle, media.audioArtist?.let { "@$it" }).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!hideLikeCounts && (media.likeCount ?: 0L) > 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${media.likeCount!!.compactCount()} likes",
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        AnimatedVisibility(
            visible = active && player?.buffering == true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ReelAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.32f),
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                    blurRadius = 4f,
                ),
            ),
        )
    }
}
