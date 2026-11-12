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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.SectionHeader
import com.avi.peek.util.compactCount

@Composable
fun LibraryScreen(
    settings: PeekSettings,
    onOpenFollowing: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCircles: () -> Unit,
    onOpenPrivateFeed: () -> Unit,
    onOpenRateLimits: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenDiscover: () -> Unit,
) {
    val followingCount by ServiceLocator.repository.followingCount.collectAsStateWithLifecycle(0)
    val savedCount by ServiceLocator.repository.savedCount.collectAsStateWithLifecycle(0)
    val downloadCount by ServiceLocator.repository.downloadCount.collectAsStateWithLifecycle(0)
    val saved by ServiceLocator.repository.saved.collectAsStateWithLifecycle(emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Everything that stays on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onOpenDiscover, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add")
                }
            }
        }

        item(key = "hero") {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "No Peek account. No cloud.",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Peek keeps your follows, bookmarks and downloads in a local database. Nothing is posted, synced or shared.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item(key = "stats") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryStat(
                    value = followingCount.toLong(),
                    label = "Following",
                    icon = Icons.Rounded.People,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFollowing,
                )
                LibraryStat(
                    value = savedCount.toLong(),
                    label = "Saved",
                    icon = Icons.Rounded.Bookmarks,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSaved,
                )
                LibraryStat(
                    value = downloadCount.toLong(),
                    label = "Offline",
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDownloads,
                )
            }
        }

        if (saved.isNotEmpty()) {
            item(key = "recent-saved") {
                Column {
                    SectionHeader(
                        title = "Recently saved",
                        action = "See all",
                        onAction = onOpenSaved,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(saved.take(10), key = { it.code }) { media ->
                            Box(
                                Modifier
                                    .size(132.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onOpenPost(media.code) },
                            ) {
                                PeekImage(
                                    url = media.displayUrl,
                                    cacheKey = "thumb_${media.code}",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "menu") {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            ) {
                Column {
                    LibraryRow(Icons.Rounded.People, "Manage following", "$followingCount local ${if (followingCount == 1) "profile" else "profiles"}", onOpenFollowing)
                    LibraryRow(Icons.Rounded.Bookmarks, "Saved posts", "$savedCount bookmarked", onOpenSaved)
                    LibraryRow(Icons.Rounded.Favorite, "Liked posts", "Your local liked feed", onOpenLiked)
                    LibraryRow(Icons.Rounded.CollectionsBookmark, "Collections", "Private folders of saved posts", onOpenCollections)
                    LibraryRow(Icons.Rounded.Groups, "Private circles", "Local groups and close friends", onOpenCircles)
                    LibraryRow(Icons.Rounded.Lock, "Private feed", "Posts from private accounts you follow", onOpenPrivateFeed)
                    LibraryRow(Icons.Rounded.Download, "Offline posts", "$downloadCount downloaded", onOpenDownloads)
                    LibraryRow(Icons.Rounded.Insights, "Weekly recap", "A private look at your week", onOpenRecap)
                    LibraryRow(Icons.Rounded.Speed, "Rate-limit dashboard", "Requests, pauses and recent outcomes", onOpenRateLimits)
                    LibraryRow(Icons.Rounded.DarkMode, "Appearance & feel", "Material You, palette, haptics", onOpenSettings)
                    LibraryRow(Icons.Rounded.Sync, "Sync & notifications", "Refresh, background sync", onOpenSettings)
                    LibraryRow(Icons.Rounded.Backup, "Backup & settings", "Export follows and saves", onOpenSettings)
                    LibraryRow(Icons.Rounded.Settings, "All settings", "Peek ${com.avi.peek.BuildConfig.VERSION_NAME}", onOpenSettings, showDivider = false)
                }
            }
        }

        item(key = "footer") {
            Text(
                "Made for following quietly · Peek is unofficial and not affiliated with Meta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LibraryStat(
    value: Long,
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(12.dp))
            Text(value.compactCount(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}
