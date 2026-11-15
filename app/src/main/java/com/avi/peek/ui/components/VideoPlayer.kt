package com.avi.peek.ui.components

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class PeekPlayerState(val player: ExoPlayer) {
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var buffering by mutableStateOf(true)

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0L))
}

@Composable
fun rememberPeekPlayer(
    url: String?,
    muted: Boolean,
    active: Boolean,
    autoPlay: Boolean = true,
    loop: Boolean = true,
): PeekPlayerState? {
    val safeUrl = url?.takeIf {
        it.isNotBlank() && com.avi.peek.data.remote.NetworkAllowlist.isAllowedUrl(it)
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestActive by rememberUpdatedState(active)
    val latestAutoPlay by rememberUpdatedState(autoPlay)
    val state = remember(safeUrl) {
        if (safeUrl == null) {
            null
        } else {
            val player = ExoPlayer.Builder(context).build().apply {
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                setHandleAudioBecomingNoisy(true)
                volume = if (muted) 0f else 1f
                setMediaItem(MediaItem.fromUri(safeUrl))
                prepare()
            }
            PeekPlayerState(player)
        }
    }

    DisposableEffect(state) {
        if (state == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.isPlaying = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    state.buffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        state.durationMs = state.player.duration.coerceAtLeast(0L)
                    }
                }
            }
            state.player.addListener(listener)
            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> state.player.pause()
                    Lifecycle.Event.ON_START -> if (latestActive && latestAutoPlay) {
                        state.player.play()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                state.player.removeListener(listener)
                state.player.release()
            }
        }
    }

    LaunchedEffect(state, muted) {
        state?.player?.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(state, active, autoPlay) {
        if (state != null) {
            if (active && autoPlay) state.player.play() else state.player.pause()
        }
    }

    LaunchedEffect(state) {
        if (state != null) {
            while (isActive) {
                state.positionMs = state.player.currentPosition
                state.durationMs = state.player.duration.coerceAtLeast(0L)
                state.isPlaying = state.player.isPlaying
                delay(350)
            }
        }
    }

    return state
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PeekVideoSurface(
    state: PeekPlayerState?,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    Box(modifier = modifier.background(Color.Black)) {
        if (state != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        player = state.player
                        setResizeMode(resizeMode)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = state.player
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = state.buffering,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
    }
}
