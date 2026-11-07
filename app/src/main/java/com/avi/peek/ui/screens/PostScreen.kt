package com.avi.peek.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.remote.MediaChild
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.DarkSystemBars
import com.avi.peek.ui.components.HeartBurst
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.PeekPlayerState
import com.avi.peek.ui.components.PeekVideoSurface
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.ZoomablePeekImage
import com.avi.peek.ui.components.rememberPeekPlayer
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.compactCount
import com.avi.peek.util.copyText
import com.avi.peek.util.fullDate
import com.avi.peek.util.instagramPostUrl
import com.avi.peek.util.openInstagram
import com.avi.peek.util.relativeTime
import com.avi.peek.util.shareText
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PostScreen(
    code: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenAudio: (String) -> Unit,
    viewModel: PostViewModel = viewModel(
        key = "post_$code",
        factory = SimpleViewModelFactory { PostViewModel(code) },
    ),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val media by viewModel.media.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var muted by remember { mutableStateOf(settings.muteVideos) }
    var burst by remember(code) { mutableIntStateOf(0) }
    var showComments by remember { mutableStateOf(false) }
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)
    DarkSystemBars()

    LaunchedEffect(settings.muteVideos) { muted = settings.muteVideos }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val value = media
    if (value == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        return
    }

    val children = remember(value.code, value.childrenJson) { value.detailChildren() }
    val pagerState = rememberPagerState(pageCount = { children.size.coerceAtLeast(1) })

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                        }
                    },
                    onDragEnd = {
                        if (dragOffset.value > 220.dp.toPx()) {
                            onBack()
                        } else {
                            scope.launch {
                                dragOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.72f, stiffness = 420f),
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f) }
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val child = children.getOrNull(page)
                        ?: MediaChild(index = 0, mediaType = value.mediaType, imageUrl = value.displayUrl)
                    val isActive = pagerState.currentPage == page
                    val localImage = value.localImages().getOrNull(page)
                    val videoUrl = if (value.localVideoPath != null && children.size == 1) {
                        File(value.localVideoPath!!).toURI().toString()
                    } else {
                        child.videoUrl ?: child.videoUrlLow ?: value.videoUrl
                    }
                    val player = rememberPeekPlayer(
                        url = videoUrl,
                        muted = muted,
                        active = isActive,
                        autoPlay = true,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(value.code, page, player) {
                                detectTapGestures(
                                    onTap = {
                                        if (videoUrl != null) {
                                            player?.toggle()
                                            haptics.tap()
                                        }
                                    },
                                    onDoubleTap = {
                                        if (!value.isLiked) viewModel.toggleLike()
                                        burst++
                                        haptics.heart()
                                    },
                                )
                            },
                    ) {
                        if (videoUrl != null && isActive) {
                            PeekVideoSurface(player, Modifier.fillMaxSize())
                            VideoControls(
                                player = player,
                                caption = value.caption,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = if (children.size > 1) 34.dp else 10.dp),
                            )
                        } else {
                            ZoomablePeekImage(
                                url = localImage?.let { File(it).toURI().toString() } ?: child.imageUrl ?: value.displayUrl,
                                cacheKey = "detail_${value.code}_$page",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        HeartBurst(trigger = burst, modifier = Modifier.fillMaxSize())
                        if (videoUrl != null && player?.isPlaying == false) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.42f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                            ),
                        ),
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.34f)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Avatar(
                        url = value.userAvatar,
                        username = value.username,
                        size = 34.dp,
                        onClick = { onOpenProfile(value.username) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenProfile(value.username) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                value.username,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            if (value.userVerified) {
                                Spacer(Modifier.width(4.dp))
                                VerifiedBadge(size = 13.dp)
                            }
                        }
                        Text(
                            value.productType?.replaceFirstChar { it.uppercase() } ?: "Post",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    if (children.any { it.videoUrl != null } || value.videoUrl != null) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.34f)) {
                            IconButton(onClick = { muted = !muted; haptics.tap() }) {
                                Icon(
                                    if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    contentDescription = if (muted) "Unmute" else "Mute",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.34f)) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh details", tint = Color.White)
                        }
                    }
                }

                if (children.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(children.size) { index ->
                            val width by animateDpAsState(
                                if (pagerState.currentPage == index) 18.dp else 6.dp,
                                label = "dot",
                            )
                            Box(
                                Modifier
                                    .size(width = width, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.4f)),
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(34.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (loading && value.detailFetchedAt == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.toggleLike(); haptics.toggle() }) {
                            Icon(
                                if (value.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (value.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IconButton(onClick = { showComments = true }) {
                            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = {
                            context.shareText(value.code.instagramPostUrl(), "Share post")
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            if (value.isDownloaded) {
                                viewModel.encouragingMessage("Already saved offline · Library → Downloads")
                            } else {
                                viewModel.download()
                            }
                        }) {
                            when {
                                downloadProgress != null -> CircularProgressIndicator(
                                    progress = { downloadProgress!!.fraction },
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp),
                                )
                                value.isDownloaded -> Icon(
                                    Icons.Rounded.DownloadDone,
                                    contentDescription = "Saved offline",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                else -> Icon(Icons.Rounded.Download, contentDescription = "Save offline")
                            }
                        }
                        IconButton(onClick = { viewModel.toggleSave(); haptics.toggle() }) {
                            Icon(
                                if (value.isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (value.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (!settings.hideLikeCounts && (value.likeCount ?: 0L) > 0L) {
                        Text(
                            "${value.likeCount!!.compactCount()} likes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val caption = value.caption?.trim().orEmpty()
                    if (caption.isNotEmpty()) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                            ) { append(value.username) }
                            append("  ")
                            append(caption)
                        }.let { annotated ->
                            Text(annotated, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = (value.takenAt ?: (value.fetchedAt / 1000)).relativeTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (value.takenAt != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(3.dp))
                                Text(value.takenAt!!.fullDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (value.locationName != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text(value.locationName!!, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (!value.audioTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenAudio(value.audioTitle!!) }
                                .padding(vertical = 3.dp),
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                listOfNotNull(value.audioTitle, value.audioArtist?.let { "@$it" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "All posts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { context.openInstagram("p/${value.code}/") },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open on Instagram")
                        }
                        TextButton(onClick = {
                            context.copyText(value.code.instagramPostUrl())
                            viewModel.encouragingMessage("Link copied")
                        }) {
                            Text("Copy link")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (showComments) {
        ModalBottomSheet(onDismissRequest = { showComments = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "Comments",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if ((value.commentCount ?: 0L) > 0L) {
                        "${value.commentCount!!.compactCount()} comments on this post. Instagram's public page only exposes the count, not the comment text, so Peek can't show a read-only thread here."
                    } else {
                        "Instagram's guest page does not expose comment text. Peek never logs in, so there is no thread to read here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        showComments = false
                        context.openInstagram("p/${value.code}/")
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open comments on Instagram")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Peek stays read-only and account-free. Comments cannot be posted from here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VideoControls(
    player: PeekPlayerState?,
    caption: String?,
    modifier: Modifier = Modifier,
) {
    if (player == null) return
    var speed by remember { mutableFloatStateOf(1f) }
    var showCaption by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1L)
    val shown = (scrub ?: player.positionMs.toFloat()).coerceIn(0f, duration.toFloat())

    LaunchedEffect(player, speed) {
        player.player.setPlaybackSpeed(speed)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (showCaption && !caption.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.68f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.56f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                Slider(
                    value = shown,
                    onValueChange = { scrub = it },
                    onValueChangeFinished = {
                        scrub?.let { player.seekTo(it.toLong()) }
                        scrub = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatClock(shown.toLong())} / ${formatClock(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showCaption = !showCaption }) {
                        Text(
                            "CC",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (showCaption) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    TextButton(
                        onClick = {
                            speed = when (speed) {
                                0.5f -> 1f
                                1f -> 1.5f
                                1.5f -> 2f
                                else -> 0.5f
                            }
                        },
                    ) {
                        Text(
                            if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun MediaEntity.detailChildren(): List<MediaChild> {
    val stored = children()
    if (stored.isNotEmpty()) return stored
    return listOfNotNull(
        displayUrl?.let {
            MediaChild(
                index = 0,
                mediaType = mediaType,
                imageUrl = it,
                videoUrl = videoUrl,
            )
        },
        videoUrl?.takeIf { displayUrl == null }?.let {
            MediaChild(index = 0, mediaType = 2, videoUrl = it)
        },
    )
}
