package com.avi.peek.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("Snackbar host not provided")
}

val LocalReduceMotion = compositionLocalOf { false }

val AvatarPalette = listOf(
    Color(0xFF6D5BD0), Color(0xFF2E7D6B), Color(0xFFB4542D), Color(0xFF8A4C6B),
    Color(0xFF3F6EA6), Color(0xFF7A6134), Color(0xFF4C7A3A), Color(0xFF7B4BA8),
)

fun colorForUsername(username: String): Color {
    if (username.isEmpty()) return AvatarPalette.first()
    return AvatarPalette[(username.hashCode() and 0x7fffffff) % AvatarPalette.size]
}

@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(16.dp)) {
    val reduce = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduce) 2400 else 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            colors.surfaceContainerHigh,
            colors.surfaceContainerHighest,
            colors.surfaceContainerHigh,
        ),
        start = Offset(offset * 600f, 0f),
        end = Offset(offset * 600f + 400f, 400f),
    )
    Box(modifier = modifier.background(brush, shape))
}

@Composable
fun PeekImage(
    url: String?,
    cacheKey: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    contentDescription: String? = null,
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .diskCacheKey(cacheKey)
            .crossfade(180)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        loading = {
            Box(Modifier.fillMaxSize()) {
                ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
            }
        },
        error = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        success = { SubcomposeAsyncImageContent() },
    )
}

@Composable
fun Avatar(
    url: String?,
    username: String,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
    ring: Boolean = false,
    ringUnread: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val gradient = if (ringUnread) {
        Brush.sweepGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
            ),
        )
    } else {
        Brush.sweepGradient(
            listOf(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(size)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (ring || ringUnread) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(gradient),
            )
        }
        Box(
            modifier = Modifier
                .size(if (ring || ringUnread) size - 5.dp else size)
                .clip(CircleShape)
                .background(colorForUsername(username)),
            contentAlignment = Alignment.Center,
        ) {
            PeekImage(
                url = url,
                cacheKey = "avatar_$username",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (url.isNullOrBlank()) {
                Text(
                    text = username.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (ring || ringUnread) {
            Box(
                Modifier
                    .size(if (ring || ringUnread) size - 5.dp else size)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .padding(1.dp),
            )
        }
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = "Verified",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size),
    )
}

@Composable
fun AnimatedNumber(value: Long, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "number",
    )
    Text(
        text = animated.toLong().let { n ->
            when {
                n < 1000 -> n.toString()
                n < 1_000_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000.0)
                else -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
            }
        },
        style = style,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(18.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "You're offline · showing your saved circle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
fun HeartBurst(trigger: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    var localTrigger by remember { mutableIntStateOf(trigger) }
    androidx.compose.runtime.LaunchedEffect(trigger) {
        if (trigger > 0 && !reduce) {
            localTrigger = trigger
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    if (localTrigger == 0 || reduce) return
    val p = progress.value
    if (p <= 0f || p >= 1f) return
    val primary = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val random = Random(42)
        repeat(12) { index ->
            val angle = (index / 12f) * Math.PI * 2 + random.nextFloat() * 0.3
            val distance = size.minDimension * (0.08f + 0.42f * p)
            val radius = (5f + random.nextFloat() * 5f) * (1f - p * 0.6f)
            drawCircle(
                color = if (index % 3 == 0) primary.copy(alpha = (1f - p) * 0.8f) else Color.White.copy(alpha = (1f - p) * 0.85f),
                radius = radius,
                center = Offset(
                    center.x + cos(angle).toFloat() * distance,
                    center.y + sin(angle).toFloat() * distance,
                ),
            )
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            androidx.compose.material.icons.Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(92.dp)
                .graphicsLayer {
                    val s = 0.4f + (1f - (2 * p - 1).let { it * it }) * 0.9f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - p * 0.85f)
                },
        )
    }
}

@Composable
fun ProvideSnackbar(host: SnackbarHostState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSnackbarHostState provides host, content = content)
}
