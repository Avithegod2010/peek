package com.avi.peek.ui.screens

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avi.peek.data.directory.CreatorDirectory
import com.avi.peek.ui.components.LocalReduceMotion
import com.avi.peek.ui.components.colorForUsername
import com.avi.peek.util.LocalPeekHaptics
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnboardingScreen(
    onFinish: (destination: String) -> Unit,
) {
    val haptics = LocalPeekHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    var languageLabel by remember { mutableStateOf(applicationLanguageLabel(context)) }
    val transition = rememberInfiniteTransition(label = "onboarding")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(if (reduceMotion) 5000 else 1900), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (reduceMotion) 12000 else 6000)),
        label = "orbit",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(7.dp).size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "PEEK",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }

            Spacer(Modifier.weight(0.7f))
            Box(
                Modifier
                    .size(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension * 0.5f,
                        ),
                        radius = size.minDimension * 0.5f,
                    )
                    repeat(3) { ring ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f - ring * 0.04f),
                            radius = size.minDimension * (0.28f + ring * 0.1f),
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                        )
                    }
                    repeat(3) { dot ->
                        val angle = Math.toRadians(orbit.toDouble() + dot * 120)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f),
                            radius = 4.5f,
                            center = Offset(
                                center.x + cos(angle).toFloat() * size.minDimension * 0.4f,
                                center.y + sin(angle).toFloat() * size.minDimension * 0.4f,
                            ),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(118.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        )
                        .then(Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size((50 * pulse).dp),
                    )
                }
            }
            Spacer(Modifier.weight(0.7f))

            Text(
                "Instagram,\nwithout Instagram.",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = androidx.compose.ui.unit.TextUnit(38f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Follow public accounts quietly. No feed algorithm, no reels rabbit hole, no Peek account. Optional Instagram sign-in exists only if you want private accounts you follow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureChip(Icons.Rounded.Lock, "Optional login")
                FeatureChip(Icons.Rounded.CloudOff, "Offline")
                FeatureChip(Icons.Rounded.AutoAwesome, "Local")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "No Peek account · no analytics · local-only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = {
                    haptics.tap()
                    languageLabel = cycleApplicationLanguage(context)
                },
            ) {
                Text(
                    "Language · $languageLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CreatorDirectory.starter.take(5).forEach { creator ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colorForUsername(creator.username).copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            creator.name.take(1),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorForUsername(creator.username),
                        )
                    }
                }
                Text(
                    "and more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(0.8f))
            Button(
                onClick = { haptics.confirm(); onFinish("discover") },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Find a profile", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { haptics.tap(); onFinish("discover") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Explore the starter pack")
            }
            TextButton(onClick = { haptics.tap(); onFinish("home") }) {
                Text(
                    "Just have a look around",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val onboardingLanguages = listOf(
    "" to "System",
    "en" to "English",
    "hi" to "हिन्दी",
    "es" to "Español",
    "pt" to "Português",
    "ar" to "العربية",
    "ja" to "日本語",
)

private fun applicationLanguageLabel(context: android.content.Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "System"
    val manager = context.getSystemService(LocaleManager::class.java) ?: return "System"
    val tag = manager.applicationLocales.get(0)?.language.orEmpty()
    return onboardingLanguages.firstOrNull { it.first == tag }?.second ?: "System"
}

private fun cycleApplicationLanguage(context: android.content.Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "System"
    val manager = context.getSystemService(LocaleManager::class.java) ?: return "System"
    val current = manager.applicationLocales.get(0)?.language.orEmpty()
    val index = onboardingLanguages.indexOfFirst { it.first == current }
        .let { if (it < 0) 0 else it }
    val next = onboardingLanguages[(index + 1) % onboardingLanguages.size]
    manager.applicationLocales = if (next.first.isBlank()) {
        LocaleList.getEmptyLocaleList()
    } else {
        LocaleList.forLanguageTags(next.first)
    }
    (context as? Activity)?.recreate()
    return next.second
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}
