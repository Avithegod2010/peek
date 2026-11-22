package com.avi.peek.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.local.LocalCircle
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.LocalPeekHaptics
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun CirclesScreen(
    onBack: () -> Unit,
    onOpenCircle: (String) -> Unit,
    onOpenDiscover: () -> Unit,
) {
    val circles by ServiceLocator.libraryStore.circles.collectAsStateWithLifecycle(emptyList())
    val following by ServiceLocator.repository.following.collectAsStateWithLifecycle(emptyList())
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val scope = rememberCoroutineScope()
    var editorCircle by remember { mutableStateOf<LocalCircle?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Private circles",
            subtitle = "${circles.size} local ${if (circles.size == 1) "group" else "groups"}",
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    editorCircle = null
                    showEditor = true
                }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "New circle")
                }
            },
        )
        if (circles.isEmpty()) {
            EmptyState(
                title = "No private circles yet",
                message = "Make a local group of people you follow, like F1, Museums or close friends. Nothing is shared or synced.",
                icon = Icons.Rounded.Groups,
                actionLabel = if (following.isEmpty()) "Find profiles" else "Create a circle",
                onAction = {
                    if (following.isEmpty()) onOpenDiscover()
                    else {
                        editorCircle = null
                        showEditor = true
                    }
                },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(circles, key = { it.id }) { circle ->
                    CircleRow(
                        circle = circle,
                        following = following,
                        onClick = { onOpenCircle(circle.id) },
                        onEdit = {
                            editorCircle = circle
                            showEditor = true
                        },
                        onDelete = {
                            scope.launch {
                                ServiceLocator.libraryStore.deleteCircle(circle.id)
                                snackbar.showSnackbar("Circle removed")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showEditor) {
        CircleEditorDialog(
            circle = editorCircle,
            following = following,
            onDismiss = { showEditor = false },
            onSave = { name, usernames, closeFriends ->
                showEditor = false
                scope.launch {
                    ServiceLocator.libraryStore.saveCircle(
                        name = name,
                        usernames = usernames,
                        closeFriends = closeFriends,
                        id = editorCircle?.id,
                    )
                    haptics.confirm()
                    snackbar.showSnackbar("Circle saved")
                }
            },
        )
    }
}

@Composable
private fun CircleRow(
    circle: LocalCircle,
    following: List<ProfileEntity>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val members = remember(circle.usernames, following) {
        circle.usernames.mapNotNull { name -> following.firstOrNull { it.username == name } }
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp)) {
                members.take(2).forEachIndexed { index, profile ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .align(if (index == 0) Alignment.TopStart else Alignment.BottomEnd),
                    ) {
                        Avatar(profile.profilePicUrl, profile.username, 40.dp, ring = true)
                    }
                }
                if (members.isEmpty()) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(circle.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (circle.closeFriends) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Rounded.Favorite, contentDescription = "Close friends", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    }
                }
                Text(
                    "${circle.usernames.size} ${if (circle.usernames.size == 1) "person" else "people"} · local only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Edit, contentDescription = "Edit circle", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete circle", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp)) }
        }
    }
}

@Composable
private fun CircleEditorDialog(
    circle: LocalCircle?,
    following: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, Boolean) -> Unit,
) {
    var name by remember(circle) { mutableStateOf(circle?.name.orEmpty()) }
    var closeFriends by remember(circle) { mutableStateOf(circle?.closeFriends ?: false) }
    var selected by remember(circle) { mutableStateOf(circle?.usernames.orEmpty().toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (circle == null) "New private circle" else "Edit circle") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("F1, Museums, Close friends…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Close friends", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("A local label only — nothing is sent to Instagram", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = closeFriends, onCheckedChange = { closeFriends = it })
                }
                Spacer(Modifier.height(8.dp))
                Text("People you follow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.heightIn(max = 270.dp)) {
                    items(following, key = { it.username }) { profile ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(profile.username)) {
                                        selected - profile.username
                                    } else {
                                        selected + profile.username
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(profile.profilePicUrl, profile.username, 34.dp)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.fullName.ifBlank { profile.username }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${profile.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(
                                checked = selected.contains(profile.username),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + profile.username else selected - profile.username
                                },
                            )
                        }
                    }
                }
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onSave(name, selected.toList(), closeFriends) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CircleFeedScreen(
    circleId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val circles by ServiceLocator.libraryStore.circles.collectAsStateWithLifecycle(emptyList())
    val circle = circles.firstOrNull { it.id == circleId }
    val mediaFlow = remember(circle?.usernames) {
        val names = circle?.usernames.orEmpty()
        if (names.isEmpty()) flowOf<List<com.avi.peek.data.local.MediaEntity>>(emptyList())
        else ServiceLocator.repository.mediaForUsernames(names)
    }
    val media by mediaFlow.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = circle?.name ?: "Circle",
            subtitle = "${circle?.usernames?.size ?: 0} people · ${media.size} cached posts",
            onBack = onBack,
        )
        if (circle?.closeFriends == true) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Close friends · local label only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (media.isEmpty()) {
            EmptyState(
                title = "Nothing cached yet",
                message = "Follow the people in this circle and refresh Peek to fill the combined timeline.",
                icon = Icons.Rounded.Groups,
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
                items(media, key = { it.code }) { item ->
                    MediaGridItem(media = item, onClick = { onOpenPost(item.code) })
                }
            }
        }
    }
}
