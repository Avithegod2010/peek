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
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.avi.peek.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.AccountState
import com.avi.peek.data.remote.MediaChild
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.PeekImage
import com.avi.peek.ui.components.PeekVideoSurface
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.components.rememberPeekPlayer
import com.avi.peek.util.openInstagram
import com.avi.peek.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoriesViewModel(private val username: String) : ViewModel() {
    private val repo = ServiceLocator.repository
    private val _items = MutableStateFlow<List<MediaChild>>(emptyList())
    val items: StateFlow<List<MediaChild>> = _items.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    val account: StateFlow<AccountState> = ServiceLocator.accountManager.state
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), AccountState())

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { repo.fetchStories(username) }
                .onSuccess { _items.value = it }
                .onFailure { _error.value = it.userMessage() }
            _loading.value = false
        }
    }
}

@Composable
fun StoriesScreen(
    username: String,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: StoriesViewModel = viewModel(
        key = "stories_$username",
        factory = SimpleViewModelFactory { StoriesViewModel(username) },
    ),
) {
    SecureScreen()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val seen by ServiceLocator.libraryStore.seenHighlights.collectAsStateWithLifecycle(emptySet())
    val seenKey = "story_$username"

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "@$username",
            subtitle = if (seenKey in seen) "Stories · seen locally" else "Stories · new",
            onBack = onBack,
            actions = {
                IconButton(onClick = { context.openInstagram("stories/$username/") }) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = "Open on Instagram")
                }
            },
        )
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                items.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.stories_no_stories),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            error ?: if (account.signedIn) {
                                "This account has no active stories, or Instagram did not expose them to this session."
                            } else {
                                "Stories need your optional Instagram session."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        if (!account.signedIn) {
                            Button(onClick = onOpenAccount, shape = RoundedCornerShape(16.dp)) {
                                Text("Sign in to Instagram")
                            }
                        } else {
                            Button(onClick = viewModel::load, shape = RoundedCornerShape(16.dp)) {
                                Text("Try again")
                            }
                        }
                    }
                }
                else -> {
                    val pager = rememberPagerState(pageCount = { items.size })
                    var progress by remember(items.size) { mutableFloatStateOf(0f) }
                    LaunchedEffect(pager.currentPage, items.size) {
                        progress = 0f
                        val duration = items.getOrNull(pager.currentPage)?.durationMs
                            ?.takeIf { it > 1_000L } ?: 5_000L
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
                            ServiceLocator.libraryStore.markHighlightSeen(seenKey)
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
                                    cacheKey = "story_${username}_$page",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Box(
                                Modifier.fillMaxWidth().height(90.dp).align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                        ),
                                    ),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items.forEachIndexed { index, _ ->
                            val fraction = when {
                                index < pager.currentPage -> 1f
                                index == pager.currentPage -> progress
                                else -> 0f
                            }
                            Box(
                                Modifier.weight(1f).height(3.dp)
                                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
                            ) {
                                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White, CircleShape))
                            }
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                            IconButton(
                                enabled = pager.currentPage > 0,
                                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                            ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous", tint = Color.White) }
                        }
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                            Text(
                                "${pager.currentPage + 1} / ${items.size}",
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
                                        pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(items.lastIndex))
                                    }
                                },
                            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", tint = Color.White) }
                        }
                    }
                }
            }
        }
    }
}
