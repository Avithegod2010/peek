package com.avi.peek.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avi.peek.data.remote.MediaChild
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.PeekVideoSurface
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.components.rememberPeekPlayer
import com.avi.peek.util.openInstagram
import com.avi.peek.util.userMessage

@Composable
fun HighlightScreen(
    highlightId: String,
    title: String,
    coverUrl: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaChild>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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
            subtitle = "Read-only public highlight",
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(
                    onClick = { context.openInstagram("stories/highlights/$highlightId/") },
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = "Open on Instagram")
                }
            },
        )
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                loading -> {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
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
                            "Highlight items aren't public",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error ?: "Instagram doesn't expose this highlight's stories to logged-out guests. The cover and title are still readable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
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
                                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
                            )
                            Row(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                repeat(items.size) { index ->
                                    Box(
                                        Modifier
                                            .size(if (pager.currentPage == index) 18.dp else 6.dp, 6.dp)
                                            .background(
                                                if (pager.currentPage == index) Color.White else Color.White.copy(alpha = 0.4f),
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
