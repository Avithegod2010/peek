package com.avi.peek.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.directory.CreatorCategory
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.data.directory.PackTransfer
import com.avi.peek.data.directory.PopularCreator
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.CreatorCard
import com.avi.peek.ui.components.CreatorSkeleton
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.MiniCreatorCard
import com.avi.peek.ui.components.PackCard
import com.avi.peek.ui.components.SearchEntry
import com.avi.peek.ui.components.SectionHeader
import com.avi.peek.ui.components.StarterCreatorCard
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.categoryIcon
import com.avi.peek.ui.components.categoryTint
import com.avi.peek.util.LocalPeekHaptics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ExploreScreen(
    onOpenSearch: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenPack: (String) -> Unit,
    viewModel: ExploreViewModel = viewModel(),
) {
    val haptics = LocalPeekHaptics.current
    val snackbar = LocalSnackbarHostState.current
    val following by viewModel.following.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val busyFollow by viewModel.busyFollow.collectAsStateWithLifecycle()
    val suggested by viewModel.suggested.collectAsStateWithLifecycle()
    val recentlyViewed by viewModel.recentlyViewed.collectAsStateWithLifecycle()
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Explore", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Real people, beyond the algorithm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = onOpenSearch,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search people",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        item(key = "search") {
            SearchEntry(
                modifier = Modifier.padding(horizontal = 18.dp),
                onClick = { haptics.tap(); onOpenSearch() },
            )
        }

        item(key = "packs") {
            Column {
                SectionHeader(
                    title = "Packs",
                    action = "Import",
                    onAction = { showImport = true },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.packs, key = { it.id }) { pack ->
                        PackCard(pack = pack, onClick = { onOpenPack(pack.id) })
                    }
                }
            }
        }

        item(key = "starter-header") {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Starter pack",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "A first handful of good follows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                    ) {
                        Text(
                            "LIVE",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.starter, key = { it.username }) { creator ->
                        StarterCreatorCard(
                            creator = creator,
                            profile = profiles[creator.username],
                            following = following.any { it.username == creator.username },
                            busy = busyFollow.contains(creator.username),
                            onClick = { onOpenProfile(creator.username) },
                            onFollow = { haptics.confirm(); viewModel.follow(creator) },
                        )
                    }
                }
            }
        }

        if (suggested.isNotEmpty()) {
            item(key = "suggested") {
                Column {
                    SectionHeader(
                        title = if (following.isEmpty()) "A few more to explore" else "Because you follow your circle",
                        action = null,
                        onAction = null,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(suggested, key = { "suggested-${it.username}" }) { creator ->
                            MiniCreatorCard(
                                creator = creator,
                                profile = profiles[creator.username],
                                following = following.any { it.username == creator.username },
                                busy = busyFollow.contains(creator.username),
                                onClick = { onOpenProfile(creator.username) },
                                onFollow = { haptics.confirm(); viewModel.follow(creator) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "popular-header") {
            Column {
                SectionHeader(
                    title = "Popular creators",
                    action = "Refresh",
                    onAction = viewModel::refresh,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryPill(
                        category = CreatorCategory.ALL,
                        selected = category == CreatorCategory.ALL,
                        onClick = {
                            haptics.tap()
                            viewModel.selectCategory(CreatorCategory.ALL)
                        },
                    )
                    viewModel.categories.forEach { item ->
                        CategoryPill(
                            category = item,
                            selected = category == item,
                            onClick = {
                                haptics.tap()
                                viewModel.selectCategory(item)
                            },
                        )
                    }
                }
            }
        }

        item(key = "popular-grid") {
            PopularGrid(
                creators = viewModel.creatorsFor(category),
                profiles = profiles,
                following = following.map { it.username }.toSet(),
                loading = loading,
                failed = failed,
                busy = busyFollow,
                onOpen = onOpenProfile,
                onFollow = { haptics.confirm(); viewModel.follow(it) },
                onRetry = { viewModel.refresh() },
            )
        }

        if (recentlyViewed.isNotEmpty()) {
            item(key = "recently-viewed") {
                Column {
                    SectionHeader(
                        title = "Recently viewed",
                        action = null,
                        onAction = null,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recentlyViewed.take(10), key = { "viewed-${it.username}" }) { profile ->
                            Column(
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable { onOpenProfile(profile.username) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(
                                    url = profile.profilePicUrl,
                                    username = profile.username,
                                    size = 58.dp,
                                    ring = true,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    profile.username,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "circle") {
            Column(Modifier.padding(horizontal = 18.dp)) {
                SectionHeader(
                    title = "Your circle",
                    action = if (following.isNotEmpty()) "Manage" else null,
                    onAction = onOpenFollowing,
                )
                Spacer(Modifier.height(10.dp))
                if (following.isEmpty()) {
                    Surface(
                        onClick = onOpenSearch,
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Build your quiet circle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Search a name or add a starter account", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        following.take(4).forEach { profile ->
                            CircleRow(profile = profile, onClick = { onOpenProfile(profile.username) })
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Every profile here is a public account. Peek reads only public pages and keeps follows, views and downloads on this device. Name search uses public web results.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import a Peek pack") },
            text = {
                Column {
                    Text(
                        "Paste shared Peek pack text or a JSON list of usernames.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("@natgeo\n@nasa\n…") },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importText.isNotBlank(),
                    onClick = {
                        val names = parsePackUsernames(importText)
                        if (names.isNotEmpty()) {
                            PackTransfer.title = "Shared pack"
                            PackTransfer.usernames = names
                            showImport = false
                            importText = ""
                            onOpenPack("imported")
                        }
                    },
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("Cancel") }
            },
        )
    }
}

private fun parsePackUsernames(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.startsWith("{")) {
        runCatching {
            val obj = Json.parseToJsonElement(trimmed).jsonObject
            val array = obj["usernames"]?.jsonArray
                ?: obj["peekPack"]?.jsonObject?.get("usernames")?.jsonArray
            return array?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.map { it.removePrefix("@").lowercase() }
                ?.filter { it.matches(Regex("[a-z0-9._]{2,30}")) }
                ?.distinct()
                ?.take(50)
                .orEmpty()
        }
    }
    val blocked = setOf("peek", "pack", "usernames", "username", "instagram", "https", "http", "www", "com", "json", "shared")
    return Regex("@?([A-Za-z0-9._]{2,30})").findAll(trimmed)
        .map { it.groupValues[1].lowercase() }
        .filter { it !in blocked }
        .distinct()
        .take(50)
        .toList()
}

@Composable
private fun CategoryPill(
    category: CreatorCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = categoryTint(category)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) tint else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(categoryIcon(category), contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                category.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PopularGrid(
    creators: List<PopularCreator>,
    profiles: Map<String, ProfileEntity>,
    following: Set<String>,
    loading: Set<String>,
    failed: Set<String>,
    busy: Set<String>,
    onOpen: (String) -> Unit,
    onFollow: (PopularCreator) -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedContent(
        targetState = creators,
        transitionSpec = {
            fadeIn(tween(240)) togetherWith fadeOut(tween(120))
        },
        label = "popularCreators",
    ) { visible ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            visible.chunked(2).forEach { rowCreators ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowCreators.forEach { creator ->
                        val profile = profiles[creator.username]
                        if (profile == null && creator.username in loading) {
                            CreatorSkeleton(Modifier.weight(1f))
                        } else {
                            CreatorCard(
                                creator = creator,
                                profile = profile,
                                following = creator.username in following,
                                busy = creator.username in busy,
                                failed = creator.username in failed,
                                loading = creator.username in loading,
                                onClick = {
                                    if (profile == null && creator.username in failed) onRetry()
                                    else onOpen(creator.username)
                                },
                                onFollow = { onFollow(creator) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (rowCreators.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleRow(profile: ProfileEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.profilePicUrl, profile.username, 44.dp, ring = true)
            Spacer(Modifier.width(11.dp))
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
                }
                Text(
                    profile.fullName.ifBlank { "In your circle" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
