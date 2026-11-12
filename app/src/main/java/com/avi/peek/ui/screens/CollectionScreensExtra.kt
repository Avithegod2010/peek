package com.avi.peek.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.local.LocalCollection
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MediaGridItem
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.LocalPeekHaptics
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val collections by ServiceLocator.libraryStore.collections.collectAsStateWithLifecycle(emptyList())
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Collections",
            subtitle = "${collections.size} private ${if (collections.size == 1) "collection" else "collections"}",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.LibraryAdd, contentDescription = "New collection")
                }
            },
        )
        if (collections.isEmpty()) {
            EmptyState(
                title = "No collections yet",
                message = "Group saved posts into private collections like Space, Recipes or Architecture. They stay on this device.",
                icon = Icons.Rounded.CollectionsBookmark,
                actionLabel = "Create a collection",
                onAction = { showCreate = true },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionRow(
                        collection = collection,
                        onClick = {
                            haptics.tap()
                            onOpenCollection(collection.id)
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New collection") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    placeholder = { Text("Space, Recipes, Architecture…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val name = newName
                        newName = ""
                        showCreate = false
                        scope.launch {
                            ServiceLocator.libraryStore.createCollection(name, emptyList())
                            snackbar.showSnackbar("Collection created")
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CollectionRow(
    collection: LocalCollection,
    onClick: () -> Unit,
) {
    val coverFlow = remember(collection.coverCode) {
        val code = collection.coverCode
        if (code.isNullOrBlank()) {
            flowOf<MediaEntity?>(null)
        } else {
            ServiceLocator.repository.mediaByCodes(listOf(code))
                .map { it.firstOrNull() }
        }
    }
    val cover by coverFlow.collectAsStateWithLifecycle(null)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    if (cover?.displayUrl != null) {
                        PeekImage(
                            url = cover!!.displayUrl,
                            cacheKey = "collection_${collection.id}",
                            modifier = Modifier.size(58.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${collection.codes.size} ${if (collection.codes.size == 1) "post" else "posts"} · on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val collections by ServiceLocator.libraryStore.collections.collectAsStateWithLifecycle(emptyList())
    val collection = collections.firstOrNull { it.id == collectionId }
    val mediaFlow = remember(collection?.codes) {
        val codes = collection?.codes.orEmpty()
        if (codes.isEmpty()) flowOf<List<MediaEntity>>(emptyList()) else ServiceLocator.repository.mediaByCodes(codes)
    }
    val media by mediaFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val haptics = LocalPeekHaptics.current
    var showRename by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(collection?.name.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ordered = remember(media, collection?.codes) {
        val byCode = media.associateBy { it.code }
        collection?.codes.orEmpty().mapNotNull { byCode[it] }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = collection?.name ?: "Collection",
            subtitle = "${ordered.size} private ${if (ordered.size == 1) "post" else "posts"}",
            onBack = onBack,
            actions = {
                IconButton(onClick = { rename = collection?.name.orEmpty(); showRename = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete collection", tint = MaterialTheme.colorScheme.error)
                }
            },
        )
        if (ordered.isEmpty()) {
            EmptyState(
                title = "This collection is empty",
                message = "Select posts from a profile grid and choose “Add to collection”.",
                icon = Icons.Rounded.CollectionsBookmark,
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
                items(ordered, key = { it.code }) { mediaItem ->
                    MediaGridItem(media = mediaItem, onClick = { onOpenPost(mediaItem.code) })
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename collection") },
            text = {
                OutlinedTextField(
                    value = rename,
                    onValueChange = { rename = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = rename.isNotBlank(),
                    onClick = {
                        showRename = false
                        scope.launch { ServiceLocator.libraryStore.renameCollection(collectionId, rename) }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this collection?") },
            text = { Text("The posts stay in your library; only the collection grouping is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    haptics.confirm()
                    scope.launch {
                        ServiceLocator.libraryStore.deleteCollection(collectionId)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
