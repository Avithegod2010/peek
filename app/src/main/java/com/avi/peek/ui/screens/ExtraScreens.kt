package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.repo.VisibilityPolicy
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.PrivateAccess
import com.avi.peek.util.compactCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@Composable
fun LikedScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val liked by ServiceLocator.repository.liked.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Liked",
            subtitle = "${liked.size} local ${if (liked.size == 1) "post" else "posts"}",
            onBack = onBack,
        )
        if (liked.isEmpty()) {
            EmptyState(
                title = "Nothing liked yet",
                message = "Double-tap a post or tap the heart. Likes are local and never sent to Instagram.",
                icon = Icons.Rounded.Favorite,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(liked, key = { it.code }) { media ->
                    MediaGridItem(media = media, onClick = { onOpenPost(media.code) })
                }
            }
        }
    }
}

@Composable
fun AudioScreen(
    title: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val posts by remember(title) {
        ServiceLocator.repository.observeAudio(title)
    }.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = title,
            subtitle = "${posts.size} cached ${if (posts.size == 1) "post" else "posts"} with this audio",
            onBack = onBack,
        )
        if (posts.isEmpty()) {
            EmptyState(
                title = "No cached posts yet",
                message = "Posts using this audio will appear here as your circle caches reels and videos.",
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(posts, key = { it.code }) { media ->
                    MediaGridItem(media = media, onClick = { onOpenPost(media.code) })
                }
            }
        }
    }
}

class RecapViewModel : ViewModel() {
    private val repo = ServiceLocator.repository
    private val weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

    private val hidden = kotlinx.coroutines.flow.combine(
        repo.settingsFlow,
        PrivateAccess.revealed,
    ) { settings, revealed -> VisibilityPolicy.panicLocksPrivate(settings.privateContentHidden, revealed) }

    val following = kotlinx.coroutines.flow.combine(repo.following, hidden) { profiles, hide ->
        if (hide) profiles.count { !it.isPrivate } else profiles.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val liked = repo.liked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedThisWeek = repo.savedSince(weekStart).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val downloadedThisWeek = repo.downloadedSince(weekStart).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val viewedThisWeek = kotlinx.coroutines.flow.combine(
        repo.viewedSince(weekStart),
        hidden,
    ) { profiles, hide -> if (hide) profiles.filterNot { it.isPrivate } else profiles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentlyViewed = kotlinx.coroutines.flow.combine(
        repo.recentlyViewed,
        hidden,
    ) { profiles, hide -> if (hide) profiles.filterNot { it.isPrivate } else profiles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cached = repo.mediaCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@Composable
fun RecapScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: RecapViewModel = viewModel(),
) {
    val following by viewModel.following.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val saved by viewModel.savedThisWeek.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedThisWeek.collectAsStateWithLifecycle()
    val viewed by viewModel.viewedThisWeek.collectAsStateWithLifecycle()
    val recent by viewModel.recentlyViewed.collectAsStateWithLifecycle()
    val cached by viewModel.cached.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Weekly recap",
            subtitle = "The last seven quiet days",
            onBack = onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("A week in your circle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "No rankings. No streak pressure. Just a private look back at what you chose to keep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RecapStat("Following", following, Icons.Rounded.People, Modifier.weight(1f))
                    RecapStat("Profiles viewed", viewed.size, Icons.Rounded.Insights, Modifier.weight(1f))
                }
            }
            item(key = "stats2") {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RecapStat("Saved", saved, Icons.Rounded.Favorite, Modifier.weight(1f))
                    RecapStat("Downloaded", downloaded, Icons.Rounded.GridView, Modifier.weight(1f))
                }
            }
            item(key = "likes") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local likes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${liked.size} posts · ${cached} cached posts in total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent-title") {
                    Text("Recently viewed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(recent.take(8), key = { "recap-${it.username}" }) { profile ->
                    Surface(
                        onClick = { onOpenProfile(profile.username) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(profile.profilePicUrl, profile.username, 42.dp, ring = true)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(profile.fullName.ifBlank { "Public profile" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item(key = "footer") {
                Text(
                    "This recap is computed on device from cached data. Peek does not send viewing history anywhere.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RecapStat(label: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(11.dp))
            Text(value.toLong().compactCount(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
