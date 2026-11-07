package com.avi.peek.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope
import com.avi.peek.data.directory.CreatorCategory
import com.avi.peek.data.directory.PopularCreator
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.util.compactCount

fun categoryIcon(category: CreatorCategory): ImageVector = when (category) {
    CreatorCategory.ALL -> Icons.Rounded.Public
    CreatorCategory.SPORT -> Icons.Rounded.SportsSoccer
    CreatorCategory.MUSIC -> Icons.Rounded.MusicNote
    CreatorCategory.FILM -> Icons.Rounded.Movie
    CreatorCategory.TECH -> Icons.Rounded.Memory
    CreatorCategory.NATURE -> Icons.Rounded.Public
    CreatorCategory.ART -> Icons.Rounded.Palette
    CreatorCategory.PHOTOGRAPHY -> Icons.Rounded.PhotoCamera
    CreatorCategory.FASHION -> Icons.Rounded.Checkroom
    CreatorCategory.FOOD -> Icons.Rounded.Restaurant
    CreatorCategory.GAMING -> Icons.Rounded.SportsEsports
    CreatorCategory.TRAVEL -> Icons.Rounded.Flight
    CreatorCategory.BUSINESS -> Icons.Rounded.BusinessCenter
    CreatorCategory.COMEDY -> Icons.Rounded.EmojiEmotions
}

@Composable
fun categoryTint(category: CreatorCategory): Color {
    val base = categoryBaseTint(category)
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (darkSurface) lerp(base, Color.White, 0.32f) else base
}

fun CreatorCategory.shortLabel(): String = when (this) {
    CreatorCategory.NATURE -> "Nature"
    CreatorCategory.FASHION -> "Fashion"
    CreatorCategory.FILM -> "Film"
    CreatorCategory.COMEDY -> "Comedy"
    else -> label
}

private fun categoryBaseTint(category: CreatorCategory): Color = when (category) {
    CreatorCategory.ALL -> Color(0xFF5B4BC4)
    CreatorCategory.SPORT -> Color(0xFF2E7D6B)
    CreatorCategory.MUSIC -> Color(0xFF8A4C6B)
    CreatorCategory.FILM -> Color(0xFFB4542D)
    CreatorCategory.TECH -> Color(0xFF3F6EA6)
    CreatorCategory.NATURE -> Color(0xFF3E6837)
    CreatorCategory.ART -> Color(0xFF7A6134)
    CreatorCategory.PHOTOGRAPHY -> Color(0xFF5A6B7D)
    CreatorCategory.FASHION -> Color(0xFF964061)
    CreatorCategory.FOOD -> Color(0xFF8B4A2F)
    CreatorCategory.GAMING -> Color(0xFF5B4BC4)
    CreatorCategory.TRAVEL -> Color(0xFF2E7D6B)
    CreatorCategory.BUSINESS -> Color(0xFF4C5A7A)
    CreatorCategory.COMEDY -> Color(0xFF7B4BA8)
}

@Composable
fun SearchEntry(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Search people by name",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(7.dp).size(14.dp),
                )
            }
        }
    }
}

@Composable
fun StarterCreatorCard(
    creator: PopularCreator,
    profile: ProfileEntity?,
    following: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = categoryTint(creator.category)
    Surface(
        modifier = modifier.width(176.dp).height(264.dp),
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                tint.copy(alpha = 0.5f),
                                tint.copy(alpha = 0.08f),
                            ),
                        ),
                    ),
            ) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        categoryIcon(creator.category),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        creator.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 13.dp)
                    .padding(bottom = 12.dp),
            ) {
                Avatar(
                    url = profile?.profilePicUrl,
                    username = creator.username,
                    size = 62.dp,
                    ring = true,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile?.fullName?.takeIf { it.isNotBlank() } ?: creator.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (profile?.isVerified == true) {
                        Spacer(Modifier.width(3.dp))
                        VerifiedBadge(size = 13.dp)
                    }
                }
                Text(
                    "@${creator.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                AnimatedContent(
                    targetState = profile != null,
                    transitionSpec = { fadeIn(spring()) togetherWith fadeOut() },
                    label = "starterDetail",
                ) { loaded ->
                    if (loaded && profile != null) {
                        Text(
                            "${profile.followerCount.compactCount()} followers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        ShimmerBox(Modifier.width(92.dp).height(9.dp), RoundedCornerShape(5.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                FollowPill(
                    following = following,
                    busy = busy,
                    tint = tint,
                    onClick = onFollow,
                )
            }
        }
    }
}

@Composable
fun CreatorCard(
    creator: PopularCreator,
    profile: ProfileEntity?,
    following: Boolean,
    busy: Boolean,
    failed: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = categoryTint(creator.category)
    Surface(
        modifier = modifier.height(246.dp),
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(13.dp)
                .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box {
                    Avatar(
                        url = profile?.profilePicUrl,
                        username = creator.username,
                        size = 54.dp,
                        ring = true,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(19.dp)
                            .clip(CircleShape)
                            .background(tint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            categoryIcon(creator.category),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(strokeWidth = 1.8.dp, modifier = Modifier.size(16.dp), color = tint)
                } else if (failed) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile?.fullName?.takeIf { it.isNotBlank() } ?: creator.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (profile?.isVerified == true) {
                    Spacer(Modifier.width(3.dp))
                    VerifiedBadge(size = 13.dp)
                }
            }
            Text(
                "@${creator.username}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    categoryIcon(creator.category),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    creator.category.shortLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            if ((profile?.followerCount ?: 0) > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${profile!!.followerCount.compactCount()} followers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                profile?.biography?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
                    ?: creator.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            )
            Spacer(Modifier.weight(1f))
            FollowPill(
                following = following,
                busy = busy,
                tint = tint,
                onClick = onFollow,
                compact = true,
            )
        }
    }
}

@Composable
private fun FollowPill(
    following: Boolean,
    busy: Boolean,
    tint: Color,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = !busy && !following,
        shape = RoundedCornerShape(14.dp),
        color = if (following) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
        contentColor = if (following) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth().height(if (compact) 30.dp else 34.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 1.8.dp,
                    color = tint,
                    modifier = Modifier.size(13.dp),
                )
            } else {
                Icon(
                    if (following) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                if (following) "Following" else "Follow",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun MiniCreatorCard(
    creator: PopularCreator,
    profile: ProfileEntity?,
    following: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = categoryTint(creator.category)
    Surface(
        onClick = onClick,
        modifier = modifier.width(142.dp).height(184.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Avatar(
                        url = profile?.profilePicUrl,
                        username = creator.username,
                        size = 46.dp,
                        ring = true,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(tint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(categoryIcon(creator.category), contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (profile?.isVerified == true) VerifiedBadge(size = 12.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                profile?.fullName?.takeIf { it.isNotBlank() } ?: creator.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${creator.username}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (profile != null) "${profile.followerCount.compactCount()} followers" else creator.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = if (profile != null) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            FollowPill(following = following, busy = busy, tint = tint, onClick = onFollow, compact = true)
        }
    }
}

@Composable
fun PackCard(
    pack: com.avi.peek.data.directory.CreatorPack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(196.dp).height(118.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (pack.hiddenGem) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        if (pack.hiddenGem) "HIDDEN GEM" else "PACK",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (pack.hiddenGem) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("${pack.usernames.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(pack.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(pack.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CreatorSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(246.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(13.dp)) {
            ShimmerBox(Modifier.size(54.dp), CircleShape)
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.width(110.dp).height(12.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.height(7.dp))
            ShimmerBox(Modifier.width(82.dp).height(9.dp), RoundedCornerShape(5.dp))
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.width(130.dp).height(9.dp), RoundedCornerShape(5.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(9.dp), RoundedCornerShape(5.dp))
            Spacer(Modifier.height(5.dp))
            ShimmerBox(Modifier.width(140.dp).height(9.dp), RoundedCornerShape(5.dp))
        }
    }
}
