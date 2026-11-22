package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.CreatorCard
import com.avi.peek.ui.components.CreatorSkeleton
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.shareText

@Composable
fun PackScreen(
    packId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: PackViewModel = viewModel(
        key = "pack_$packId",
        factory = SimpleViewModelFactory { PackViewModel(packId) },
    ),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val following by viewModel.following.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = viewModel.pack.title,
            subtitle = "${viewModel.pack.usernames.size} public creators",
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    val json = buildString {
                        append("{\"peekPack\":\"")
                        append(viewModel.pack.title.replace("\"", "\\\""))
                        append("\",\"usernames\":[")
                        append(viewModel.pack.usernames.joinToString(",") { "\"$it\"" })
                        append("]}")
                    }
                    val link = "peek://pack?usernames=${viewModel.pack.usernames.joinToString(",")}"
                    context.shareText(
                        "Peek pack: ${viewModel.pack.title}\n\n${viewModel.pack.usernames.joinToString("\n") { "@$it" }}\n\n$json\n\nOpen in Peek: $link",
                        "Share Peek pack",
                    )
                }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share pack")
                }
            },
        )

        if (viewModel.creators.isEmpty()) {
            EmptyState(
                title = "This pack is empty",
                message = "Import a shared pack or add creators one by one.",
                icon = Icons.Rounded.People,
                modifier = Modifier.fillMaxSize().weight(1f),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            if (viewModel.pack.hiddenGem) "HIDDEN GEM PACK" else "PACK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            viewModel.pack.subtitle.ifBlank { "A hand-picked corner of Instagram" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { haptics.confirm(); viewModel.followAll() },
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Follow the pack")
                        }
                    }
                }
            }

            items(viewModel.creators.chunked(2).size) { rowIndex ->
                val rowCreators = viewModel.creators.chunked(2)[rowIndex]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowCreators.forEach { creator ->
                        val profile = profiles[creator.username]
                        if (profile == null && loading.contains(creator.username)) {
                            CreatorSkeleton(Modifier.weight(1f))
                        } else {
                            CreatorCard(
                                creator = creator,
                                profile = profile,
                                following = following.any { it.username == creator.username },
                                busy = busy.contains(creator.username),
                                failed = profile == null && !loading.contains(creator.username),
                                loading = loading.contains(creator.username),
                                onClick = { onOpenProfile(creator.username) },
                                onFollow = { viewModel.follow(creator) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (rowCreators.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item(key = "footer") {
                Text(
                    "Share this pack as text. Peek's import field accepts the JSON at the end, or just the @usernames.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            }
        }
    }
}
