package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.rememberPrivateUnlock
import com.avi.peek.util.PrivateAccess
import kotlinx.coroutines.launch

@Composable
fun PrivateFeedScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenDiscover: () -> Unit,
) {
    val media by ServiceLocator.repository.privateFeed.collectAsStateWithLifecycle(emptyList())
    val settings by ServiceLocator.repository.settingsFlow
        .collectAsStateWithLifecycle(initialValue = PeekSettings())
    val revealed by PrivateAccess.revealed.collectAsStateWithLifecycle()
    val hidden = settings.privateContentHidden && !revealed
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val unlock = rememberPrivateUnlock { message ->
        scope.launch { snackbar.showSnackbar(message) }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Private feed",
            subtitle = if (hidden) "Locked" else "${media.size} private posts cached",
            onBack = onBack,
        )
        if (hidden) {
            EmptyState(
                title = "Private content is hidden",
                message = "Your private follows and their cached posts are locked. Unlock with your device biometric or screen lock.",
                icon = Icons.Rounded.Lock,
                actionLabel = "Unlock private content",
                onAction = {
                    unlock {
                        PrivateAccess.reveal()
                        scope.launch {
                            runCatching { ServiceLocator.repository.openPrivateVault() }
                                .onSuccess { snackbar.showSnackbar("Private content revealed") }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Couldn't open the private vault") }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else if (media.isEmpty()) {
            EmptyState(
                title = "No private posts yet",
                message = "Follow a private account you can already see with your Instagram session. Its cached posts will appear here.",
                icon = Icons.Rounded.LockOpen,
                actionLabel = "Find profiles",
                onAction = onOpenDiscover,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    "Local only · ${media.size} cached private posts. Nothing here is shared or uploaded.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(media, key = { it.code }) { item ->
                    MediaGridItem(media = item, onClick = { onOpenPost(item.code) })
                }
            }
        }
    }
}
