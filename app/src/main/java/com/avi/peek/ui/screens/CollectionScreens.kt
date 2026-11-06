package com.avi.peek.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.fileSize
import com.avi.peek.util.openInstagram
import com.avi.peek.util.fullDate
import kotlinx.coroutines.launch
import java.io.File

private enum class CollectionFilter { ALL, PHOTOS, REELS, VIDEOS }

@Composable
fun SavedScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val saved by ServiceLocator.repository.saved.collectAsStateWithLifecycle(emptyList())
    val haptics = LocalPeekHaptics.current
    var filter by remember { mutableStateOf(CollectionFilter.ALL) }
    val filtered = when (filter) {
        CollectionFilter.ALL -> saved
        CollectionFilter.PHOTOS -> saved.filter { !it.isVideo && !it.isReel }
        CollectionFilter.REELS -> saved.filter { it.isReel }
        CollectionFilter.VIDEOS -> saved.filter { it.isVideo }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Saved",
            subtitle = "${saved.size} local ${if (saved.size == 1) "post" else "posts"}",
            onBack = onBack,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SavedFilter("All", filter == CollectionFilter.ALL, Modifier.weight(1f)) {
                filter = CollectionFilter.ALL; haptics.tap()
            }
            SavedFilter("Photos", filter == CollectionFilter.PHOTOS, Modifier.weight(1f)) {
                filter = CollectionFilter.PHOTOS; haptics.tap()
            }
            SavedFilter("Reels", filter == CollectionFilter.REELS, Modifier.weight(1f)) {
                filter = CollectionFilter.REELS; haptics.tap()
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = "No bookmarks yet",
                message = "Tap the bookmark icon on a post and it will wait for you here, even offline.",
                icon = Icons.Rounded.Bookmarks,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(filtered, key = { it.code }) { media ->
                    MediaGridItem(media = media, onClick = { onOpenPost(media.code) })
                }
            }
        }
    }
}

@Composable
private fun SavedFilter(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val downloads by ServiceLocator.repository.downloaded.collectAsStateWithLifecycle(emptyList())
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalPeekHaptics.current
    val context = LocalContext.current

    val totalBytes = remember(downloads) {
        downloads.sumOf { media ->
            val images = media.localImages().sumOf { File(it).length() }
            val video = media.localVideoPath?.let { File(it).length() } ?: 0L
            images + video
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Offline",
            subtitle = "${downloads.size} ${if (downloads.size == 1) "post" else "posts"} · ${totalBytes.fileSize()}",
            onBack = onBack,
        )
        if (downloads.isEmpty()) {
            EmptyState(
                title = "Nothing downloaded",
                message = "Save a post offline and its photos or video will live here, ready without a connection.",
                icon = Icons.Rounded.Download,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(downloads, key = { it.code }) { media ->
                    DownloadRow(
                        media = media,
                        onOpen = { onOpenPost(media.code) },
                        onDelete = {
                            scope.launch {
                                ServiceLocator.repository.deleteDownload(media.code)
                                haptics.confirm()
                                snackbar.showSnackbar("Removed from offline")
                            }
                        },
                        onOpenInstagram = {
                            context.openInstagram("p/${media.code}/")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    media: MediaEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onOpenInstagram: () -> Unit,
) {
    val bytes = remember(media.code, media.localImagePathsJson, media.localVideoPath) {
        media.localImages().sumOf { File(it).length() } + (media.localVideoPath?.let { File(it).length() } ?: 0L)
    }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(16.dp))) {
                PeekImage(
                    url = media.displayUrl,
                    cacheKey = "thumb_${media.code}",
                    modifier = Modifier.fillMaxSize(),
                )
                if (media.isVideo) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "@${media.username}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    media.caption?.replace('\n', ' ')?.take(70).orEmpty().ifBlank { "Saved post" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${if (media.isVideo) "Video" else "Photo"} · ${bytes.fileSize()} · ${(media.downloadedAt ?: System.currentTimeMillis()).fullDate()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = onOpenInstagram) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = "Open on Instagram", modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}
