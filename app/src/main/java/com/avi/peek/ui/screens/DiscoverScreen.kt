package com.avi.peek.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.repo.STARTER_PACK
import com.avi.peek.data.repo.StarterAccount
import com.avi.peek.ui.components.AddPeopleHint
import com.avi.peek.ui.components.Avatar
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.SectionHeader
import com.avi.peek.ui.components.ShimmerBox
import com.avi.peek.ui.components.VerifiedBadge
import com.avi.peek.ui.components.colorForUsername
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.compactCount
import com.avi.peek.util.openInstagram

@Composable
fun DiscoverScreen(
    onOpenProfile: (String) -> Unit,
    onOpenFollowing: () -> Unit,
    viewModel: DiscoverViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val busy by viewModel.busyUsername.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

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
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Discover", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Add a public profile. Follow it locally.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "search") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Username or Instagram link") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        viewModel.lookup()
                    }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        focus.clearFocus()
                        viewModel.lookup()
                        haptics.tap()
                    },
                    enabled = query.isNotBlank() && !loading,
                    modifier = Modifier.size(52.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Rounded.ArrowForward, contentDescription = "Look up")
                    }
                }
            }
        }

        item(key = "preview") {
            AnimatedVisibility(visible = preview != null || loading || error != null) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    when {
                        loading -> SearchLoadingCard()
                        error != null -> ErrorCard(error!!) { viewModel.lookup() }
                        preview != null -> ProfilePreview(
                            preview = preview!!,
                            isFollowing = following.any { it.username.equals(preview!!.username, true) },
                            busy = busy == preview!!.username,
                            onFollow = { viewModel.followPreview(); haptics.confirm() },
                            onOpen = { onOpenProfile(preview!!.username.lowercase()) },
                        )
                    }
                }
            }
        }

        item(key = "starter") {
            Column {
                SectionHeader(
                    title = "Starter pack",
                    action = "Curated",
                    onAction = null,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(STARTER_PACK, key = { it.username }) { account ->
                        StarterCard(
                            account = account,
                            followed = following.any { it.username.equals(account.username, true) },
                            onClick = {
                                haptics.tap()
                                viewModel.addFromStarter(account.username)
                            },
                        )
                    }
                }
            }
        }

        item(key = "circle") {
            Column(Modifier.padding(horizontal = 18.dp)) {
                SectionHeader(
                    title = "Your circle",
                    action = if (following.size > 4) "Manage" else null,
                    onAction = onOpenFollowing,
                )
                Spacer(Modifier.height(10.dp))
                if (following.isEmpty()) {
                    AddPeopleHint(Modifier.fillMaxWidth())
                } else {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column {
                            following.take(6).forEachIndexed { index, profile ->
                                FollowingRow(
                                    profile = profile,
                                    onClick = { onOpenProfile(profile.username) },
                                    onRemove = { viewModel.unfollow(profile.username) },
                                )
                                if (index < minOf(following.size, 6) - 1) {
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    SectionHeader(
                        title = "Recent",
                        action = "Clear",
                        onAction = viewModel::clearHistory,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        recent.forEach { entry ->
                            AssistChip(
                                onClick = { viewModel.useRecent(entry.query) },
                                label = { Text(entry.query, maxLines = 1) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                shape = CircleShape,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }

        item(key = "disclaimer") {
            Row(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Peek only reads public pages. Your follows, likes and saves stay on this device. There is no Instagram account, no password and no tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchLoadingCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(72.dp), CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBox(Modifier.width(140.dp).height(14.dp), RoundedCornerShape(7.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(Modifier.width(100.dp).height(10.dp), RoundedCornerShape(5.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(Modifier.width(180.dp).height(10.dp), RoundedCornerShape(5.dp))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Couldn't load that one",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun ProfilePreview(
    preview: com.avi.peek.data.remote.ScrapedProfile,
    isFollowing: Boolean,
    busy: Boolean,
    onFollow: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorForUsername(preview.username).copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    ),
            )
            Column(Modifier.padding(horizontal = 18.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Avatar(
                        url = preview.profilePicUrl,
                        username = preview.username,
                        size = 78.dp,
                        ring = true,
                        modifier = Modifier.offset(y = (-34).dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        MiniStat(preview.followerCount, "followers")
                        MiniStat(preview.followingCount, "following")
                        MiniStat(preview.media.size.toLong(), "posts")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preview.fullName.ifBlank { preview.username },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (preview.isVerified) {
                        Spacer(Modifier.width(5.dp))
                        VerifiedBadge(size = 17.dp)
                    }
                }
                Text("@${preview.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preview.biography.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(preview.biography, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (preview.isPrivate) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Private account · posts aren't public", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isFollowing) {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open profile")
                        }
                    } else {
                        Button(
                            onClick = onFollow,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(17.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Follow locally")
                        }
                        OutlinedButton(
                            onClick = { context.openInstagram(preview.username) },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MiniStat(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.compactCount(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StarterCard(account: StarterAccount, followed: Boolean, onClick: () -> Unit) {
    val index = STARTER_PACK.indexOf(account).coerceAtLeast(0)
    val start = colorForUsername(account.username)
    Card(
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(containerColor = start.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(start.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    account.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = start,
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(account.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(account.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${account.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            if (followed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                    Text("Following", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun FollowingRow(
    profile: ProfileEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(profile.profilePicUrl, profile.username, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (profile.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge(size = 13.dp)
                }
            }
            Text(
                profile.fullName.ifBlank { "Following locally" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.PersonRemove, contentDescription = "Unfollow", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
