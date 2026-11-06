package com.avi.peek.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.util.LocalPeekHaptics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FollowingViewModel : ViewModel() {
    private val repo = ServiceLocator.repository
    val following = repo.following.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun unfollow(username: String) {
        viewModelScope.launch {
            repo.unfollow(username)
            _messages.emit("@$username removed from your circle")
        }
    }

    fun togglePin(profile: ProfileEntity) {
        viewModelScope.launch {
            repo.setPinned(profile.username, !profile.isPinned)
            _messages.emit(if (profile.isPinned) "Unpinned @${profile.username}" else "Pinned @${profile.username}")
        }
    }
}

@Composable
fun FollowingScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenDiscover: () -> Unit,
    viewModel: FollowingViewModel = viewModel(),
) {
    val following by viewModel.following.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(following, query) {
        if (query.isBlank()) following
        else following.filter {
            it.username.contains(query, true) || it.fullName.contains(query, true)
        }
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Your circle",
            subtitle = "${following.size} local ${if (following.size == 1) "follow" else "follows"}",
            onBack = onBack,
            actions = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    IconButton(onClick = onOpenDiscover) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add profile", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            placeholder = { Text("Search your circle") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
        )
        if (filtered.isEmpty()) {
            EmptyState(
                title = if (following.isEmpty()) "No one yet" else "No matches",
                message = if (following.isEmpty()) "Add a public profile and follow it locally. There is no account to create." else "Try a different name or username.",
                icon = Icons.Rounded.People,
                actionLabel = if (following.isEmpty()) "Find a profile" else null,
                onAction = if (following.isEmpty()) onOpenDiscover else null,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.username }) { profile ->
                    Card(
                        onClick = { onOpenProfile(profile.username) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(
                                url = profile.profilePicUrl,
                                username = profile.username,
                                size = 48.dp,
                                ring = true,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        profile.username,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (profile.isVerified) {
                                        Spacer(Modifier.width(4.dp))
                                        VerifiedBadge(size = 13.dp)
                                    }
                                    if (profile.isPinned) {
                                        Spacer(Modifier.width(5.dp))
                                        Icon(
                                            Icons.Rounded.PushPin,
                                            contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                                Text(
                                    profile.fullName.ifBlank { "No bio cached" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { viewModel.togglePin(profile); haptics.tap() }) {
                                Icon(
                                    Icons.Rounded.PushPin,
                                    contentDescription = "Pin",
                                    tint = if (profile.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            IconButton(onClick = { viewModel.unfollow(profile.username); haptics.confirm() }) {
                                Icon(
                                    Icons.Rounded.PersonRemove,
                                    contentDescription = "Unfollow",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
