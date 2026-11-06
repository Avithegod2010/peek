package com.avi.peek.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.avi.peek.util.compactCount
import androidx.compose.ui.unit.dp
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.repo.DownloadProgress
import com.avi.peek.data.repo.FeedPost
import com.avi.peek.ui.theme.PeekShapes
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.relativeTime

@Composable
fun FeedPostCard(
    post: FeedPost,
    hideLikeCounts: Boolean,
    downloadProgress: DownloadProgress?,
    modifier: Modifier = Modifier,
    onOpenPost: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onOpenInstagram: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val media = post.media
    val profile = post.profile
    val haptics = LocalPeekHaptics.current
    var menuOpen by remember { mutableStateOf(false) }
    var burst by remember(media.code) { mutableIntStateOf(0) }
    var captionExpanded by remember(media.code) { mutableStateOf(false) }
    val likeScale by animateFloatAsState(
        targetValue = if (media.isLiked) 1.13f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 650f),
        label = "likeScale",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PeekShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 13.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    url = profile.profilePicUrl,
                    username = profile.username,
                    size = 38.dp,
                    ring = true,
                    ringUnread = (profile.latestReelMedia ?: 0L) > (profile.lastViewedAt ?: 0L) / 1000L,
                    onClick = onOpenProfile,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.username,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profile.isVerified) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge(size = 13.dp)
                        }
                    }
                    Text(
                        text = buildString {
                            if (profile.fullName.isNotBlank() && !profile.fullName.equals(profile.username, true)) {
                                append(profile.fullName)
                                append(" · ")
                            }
                            append((media.takenAt ?: (media.fetchedAt / 1000)).relativeTime())
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = {
                        haptics.tap()
                        menuOpen = true
                    }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Open on Instagram") },
                            onClick = { menuOpen = false; onOpenInstagram() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            onClick = { menuOpen = false; onCopyLink() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { menuOpen = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (media.isDownloaded) "Saved offline" else "Save offline") },
                            onClick = { menuOpen = false; if (!media.isDownloaded) onDownload() },
                            enabled = !media.isDownloaded,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(24.dp))
                    .pointerInput(media.code, media.isLiked) {
                        detectTapGestures(
                            onTap = { onOpenPost() },
                            onDoubleTap = {
                                if (!media.isLiked) {
                                    onToggleLike()
                                }
                                burst++
                                haptics.heart()
                            },
                        )
                    },
            ) {
                PeekImage(
                    url = media.displayUrl,
                    cacheKey = "cover_${media.code}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (media.isVideo) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.38f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                if (media.isReel) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.46f),
                    ) {
                        Text(
                            text = "REEL",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (media.isCarousel || media.carouselCount > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.46f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Rounded.Collections,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${media.carouselCount.coerceAtLeast(2)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (downloadProgress != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadProgress.fraction },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
                HeartBurst(trigger = burst, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.18f),
                            ),
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onToggleLike(); haptics.toggle() }) {
                    Icon(
                        imageVector = if (media.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (media.isLiked) "Unlike" else "Like",
                        tint = if (media.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(25.dp)
                            .graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                    )
                }
                IconButton(onClick = onOpenPost) {
                    Icon(
                        Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp),
                    )
                }
                IconButton(onClick = { onShare(); haptics.tap() }) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDownload(); haptics.tap() }) {
                    if (downloadProgress != null) {
                        CircularProgressIndicator(
                            progress = { downloadProgress.fraction },
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(21.dp),
                        )
                    } else {
                        Icon(
                            if (media.isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            contentDescription = "Save offline",
                            tint = if (media.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                IconButton(onClick = { onToggleSave(); haptics.toggle() }) {
                    Icon(
                        imageVector = if (media.isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (media.isSaved) "Remove bookmark" else "Bookmark",
                        tint = if (media.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 1.dp, bottom = 16.dp),
            ) {
                if (!hideLikeCounts && (media.likeCount ?: 0L) > 0L) {
                    Text(
                        text = "${media.likeCount!!.compactCount()} likes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                val caption = media.caption?.trim().orEmpty()
                if (caption.isNotEmpty()) {
                    val annotated = androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(profile.username)
                        }
                        append("  ")
                        append(caption)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (captionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (caption.length > 90) {
                        Text(
                            text = if (captionExpanded) "less" else "more",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures { captionExpanded = !captionExpanded }
                                }
                                .padding(vertical = 2.dp),
                        )
                    }
                }
                if (!hideLikeCounts && (media.commentCount ?: 0L) > 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "View all ${media.commentCount!!.compactCount()} comments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .pointerInput(Unit) { detectTapGestures { onOpenPost() } },
                    )
                }
            }
        }
    }
}


@Composable
fun MediaGridItem(
    media: MediaEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalPeekHaptics.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .pressScale(interaction, 0.94f)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.tap()
                onClick()
            },
    ) {
        PeekImage(
            url = media.displayUrl,
            cacheKey = "thumb_${media.code}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (media.isVideo) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        if (media.isCarousel || media.carouselCount > 1) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Collections,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (media.isLiked) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(15.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(2.dp),
                )
            }
            if (media.isSaved) {
                Icon(
                    Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(15.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(2.dp),
                )
            }
        }
    }
}
