package com.avi.peek.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.local.AccountState
import com.avi.peek.data.remote.MediaChild
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.PeekVideoSurface
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.components.rememberPeekPlayer
import com.avi.peek.util.openInstagram
import com.avi.peek.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HighlightScreen(
    highlightId: String,
    title: String,
    coverUrl: String?,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MediaChild>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val seen by ServiceLocator.libraryStore.seenHighlights.collectAsStateWithLifecycle(emptySet())
    val account by ServiceLocator.accountManager.state.collectAsStateWithLifecycle(
        initialValue = AccountState(),
    )

    LaunchedEffect(highlightId) {
        loading = true
        runCatching { ServiceLocator.repository.fetchHighlightMedia(highlightId) }
            .onSuccess { items = it }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = title.ifBlank { "Highlight" },
            subtitle = if (highlightId in seen) "Read-only · seen locally" else "Read-only · new",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = { context.openInstagram("stories/highlights/$highlightId/") },
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = "Open on Instagram")
                }
            },
        )
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                items.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (coverUrl != null) {
                            PeekImage(
                                url = coverUrl,
                                cacheKey = "highlight_cover_$highlightId",
                                modifier = Modifier.size(150.dp).background(Color.DarkGray, CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        Text(
                            "Highlight items aren't available",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error ?: if (account.signedIn) {
                                "Instagram didn't return this highlight to the current session."
                            } else {
                                "Instagram doesn't expose this highlight without an account. The cover and title are still readable."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (!account.signedIn) {
                            Button(onClick = onOpenAccount, shape = RoundedCornerShape(16.dp)) {
                                Text("Sign in to Instagram")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { context.openInstagram("stories/highlights/$highlightId/") },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Open on Instagram")
                        }
                    }
                }
                else -> {
                    val pager = rememberPagerState(pageCount = { items.size })
                    var progress by remember(items.size) { mutableFloatStateOf(0f) }

                    LaunchedEffect(pager.currentPage, items.size) {
                        if (pager.currentPage > items.lastIndex) return@LaunchedEffect
                        progress = 0f
                        val duration = items.getOrNull(pager.currentPage)
                            ?.durationMs
                            ?.takeIf { it > 1_000L }
                            ?: 5_000L
                        var elapsed = 0L
                        while (elapsed < duration) {
                            delay(50)
                            if (!pager.isScrollInProgress) {
                                elapsed += 50
                                progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                            }
                        }
                        if (pager.currentPage < items.lastIndex) {
                            pager.animateScrollToPage(pager.currentPage + 1)
                        } else {
                            progress = 1f
                            ServiceLocator.libraryStore.markHighlightSeen(highlightId)
                        }
                    }

                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val item = items[page]
                        val player = rememberPeekPlayer(
                            url = item.videoUrl ?: item.videoUrlLow,
                            muted = true,
                            active = pager.currentPage == page,
                        )
                        Box(Modifier.fillMaxSize()) {
                            if (item.videoUrl != null || item.videoUrlLow != null) {
                                PeekVideoSurface(player, Modifier.fillMaxSize())
                            } else {
                                PeekImage(
                                    url = item.imageUrl ?: item.imageUrlLow,
                                    cacheKey = "highlight_${highlightId}_$page",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                                        ),
                                    ),
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items.forEachIndexed { index, _ ->
                            val fraction = when {
                                index < pager.currentPage -> 1f
                                index == pager.currentPage -> progress
                                else -> 0f
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.26f), CircleShape),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(Color.White, CircleShape),
                                )
                            }
                        }
                    }

                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                            IconButton(
                                enabled = pager.currentPage > 0,
                                onClick = {
                                    scope.launch {
                                        pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous", tint = Color.White)
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                        ) {
                            Text(
                                "${pager.currentPage + 1} / ${items.size}" +
                                    if (highlightId in seen) " · seen" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            )
                        }
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                            IconButton(
                                enabled = pager.currentPage < items.lastIndex,
                                onClick = {
                                    scope.launch {
                                        pager.animateScrollToPage(
                                            (pager.currentPage + 1).coerceAtMost(items.lastIndex),
                                        )
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
