package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avi.peek.data.repo.ProfileStorage
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.fileSize
import com.avi.peek.util.relativeTime
import kotlinx.coroutines.launch

@Composable
fun ProfileStorageScreen(
    username: String,
    onBack: () -> Unit,
) {
    // Per-profile storage names private accounts; keep it off captures.
    SecureScreen()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var storage by remember { mutableStateOf<ProfileStorage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<StorageAction?>(null) }

    suspend fun refresh() {
        loading = true
        storage = runCatching { ServiceLocator.repository.profileStorage(username) }.getOrNull()
        loading = false
    }

    LaunchedEffect(username) { refresh() }

    fun run(action: StorageAction) {
        scope.launch {
            loading = true
            val result = runCatching {
                when (action) {
                    StorageAction.CLEAR_CACHE -> ServiceLocator.repository.clearCachedPosts(username)
                    StorageAction.DELETE_DOWNLOADS -> ServiceLocator.repository.deleteDownloadsForProfile(username)
                    StorageAction.TRIM_60 -> ServiceLocator.repository.trimProfileCache(username, 60)
                    StorageAction.TRIM_120 -> ServiceLocator.repository.trimProfileCache(username, 120)
                }
            }
            loading = false
            refresh()
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { action.doneMessage },
                    onFailure = { it.message ?: "Couldn't update storage" },
                ),
            )
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Profile storage",
            subtitle = "@$username · on this device",
            onBack = onBack,
        )
        if (loading && storage == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(60.dp))
                CircularProgressIndicator()
            }
            return@Column
        }
        val value = storage ?: return@Column
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Local storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(value.downloadBytes.fileSize(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("downloaded media", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${value.cachedCount} cached posts · ${value.downloadedCount} offline · ${value.savedCount} saved · ${value.likedCount} liked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (value.newestAt != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Newest ${value.newestAt.relativeTime()} · oldest ${value.oldestAt?.relativeTime() ?: "unknown"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            StorageActionCard(
                icon = Icons.Rounded.CleaningServices,
                title = "Clear cached posts",
                subtitle = "Keeps saved, liked and downloaded posts. Removes ${(value.cachedCount - value.keptCount).coerceAtLeast(0)} cached grid entries.",
                onClick = { confirm = StorageAction.CLEAR_CACHE },
            )
            StorageActionCard(
                icon = Icons.Rounded.DeleteOutline,
                title = "Delete downloads",
                subtitle = "Frees ${value.downloadBytes.fileSize()} · keeps the cached grid and bookmarks.",
                destructive = true,
                onClick = { confirm = StorageAction.DELETE_DOWNLOADS },
            )
            StorageActionCard(
                icon = Icons.Rounded.Download,
                title = "Trim cache to newest 60",
                subtitle = "Keeps downloads, saves and likes. Useful after loading a very large profile.",
                onClick = { confirm = StorageAction.TRIM_60 },
            )
            StorageActionCard(
                icon = Icons.Rounded.Download,
                title = "Trim cache to newest 120",
                subtitle = "A gentler trim for profiles you browse often.",
                onClick = { confirm = StorageAction.TRIM_120 },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Peek never uploads these files. Storage actions only touch this device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    run(action)
                }) { Text(action.confirmLabel, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private enum class StorageAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val doneMessage: String,
) {
    CLEAR_CACHE(
        "Clear cached posts?",
        "This removes cached grid posts for this profile. Saved, liked and downloaded posts stay.",
        "Clear cache",
        "Cached posts cleared",
    ),
    DELETE_DOWNLOADS(
        "Delete downloads?",
        "This deletes the offline files for this profile. You can download them again later.",
        "Delete downloads",
        "Downloads deleted",
    ),
    TRIM_60(
        "Trim to 60 cached posts?",
        "Only the newest 60 cached posts are kept, plus anything saved, liked or downloaded.",
        "Trim",
        "Cache trimmed to 60 posts",
    ),
    TRIM_120(
        "Trim to 120 cached posts?",
        "Only the newest 120 cached posts are kept, plus anything saved, liked or downloaded.",
        "Trim",
        "Cache trimmed to 120 posts",
    ),
}

@Composable
private fun StorageActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
