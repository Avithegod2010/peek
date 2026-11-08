package com.avi.peek.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.avi.peek.ui.components.findActivity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avi.peek.data.local.PaletteStyle
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.ThemeMode
import androidx.compose.material3.Shapes

private data class PaletteColors(
    val light: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
)

private fun PaletteStyle.colors(): PaletteColors = when (this) {
    PaletteStyle.LAVENDER -> PaletteColors(
        light = Color(0xFF5B4BC4),
        lightContainer = Color(0xFFE7DEFF),
        onLightContainer = Color(0xFF1B0061),
        dark = Color(0xFFC9BFFF),
        darkContainer = Color(0xFF443393),
        onDarkContainer = Color(0xFFE7DEFF),
    )
    PaletteStyle.OCEAN -> PaletteColors(
        light = Color(0xFF006A6A),
        lightContainer = Color(0xFF9CF1EF),
        onLightContainer = Color(0xFF00201F),
        dark = Color(0xFF4DDADA),
        darkContainer = Color(0xFF00504F),
        onDarkContainer = Color(0xFF9CF1EF),
    )
    PaletteStyle.FOREST -> PaletteColors(
        light = Color(0xFF3E6837),
        lightContainer = Color(0xFFBFEFB0),
        onLightContainer = Color(0xFF012100),
        dark = Color(0xFFA3D398),
        darkContainer = Color(0xFF285021),
        onDarkContainer = Color(0xFFBFEFB0),
    )
    PaletteStyle.SUNSET -> PaletteColors(
        light = Color(0xFF8B4A2F),
        lightContainer = Color(0xFFFFDBCB),
        onLightContainer = Color(0xFF360F00),
        dark = Color(0xFFFFB693),
        darkContainer = Color(0xFF6D341A),
        onDarkContainer = Color(0xFFFFDBCB),
    )
    PaletteStyle.ROSE -> PaletteColors(
        light = Color(0xFF8B4657),
        lightContainer = Color(0xFFFFD9E0),
        onLightContainer = Color(0xFF3D071A),
        dark = Color(0xFFFFB1C1),
        darkContainer = Color(0xFF6D3444),
        onDarkContainer = Color(0xFFFFD9E0),
    )
    PaletteStyle.MINT -> PaletteColors(
        light = Color(0xFF006B5B),
        lightContainer = Color(0xFFA7F2E3),
        onLightContainer = Color(0xFF00201B),
        dark = Color(0xFF89D6C8),
        darkContainer = Color(0xFF005045),
        onDarkContainer = Color(0xFFA7F2E3),
    )
    PaletteStyle.COFFEE -> PaletteColors(
        light = Color(0xFF6F4E37),
        lightContainer = Color(0xFFFFDCC2),
        onLightContainer = Color(0xFF2A170A),
        dark = Color(0xFFE0C0A6),
        darkContainer = Color(0xFF573E2D),
        onDarkContainer = Color(0xFFFFDCC2),
    )
    PaletteStyle.SAKURA -> PaletteColors(
        light = Color(0xFF9C4059),
        lightContainer = Color(0xFFFFD9E2),
        onLightContainer = Color(0xFF3E0016),
        dark = Color(0xFFFFB1C6),
        darkContainer = Color(0xFF7B2942),
        onDarkContainer = Color(0xFFFFD9E2),
    )
    PaletteStyle.CITRUS -> PaletteColors(
        light = Color(0xFF7A5900),
        lightContainer = Color(0xFFFFE08A),
        onLightContainer = Color(0xFF261A00),
        dark = Color(0xFFE9C349),
        darkContainer = Color(0xFF5C4300),
        onDarkContainer = Color(0xFFFFE08A),
    )
    PaletteStyle.INDIGO -> PaletteColors(
        light = Color(0xFF3F51B5),
        lightContainer = Color(0xFFDFE0FF),
        onLightContainer = Color(0xFF00105C),
        dark = Color(0xFFBBC3FF),
        darkContainer = Color(0xFF283593),
        onDarkContainer = Color(0xFFDFE0FF),
    )
    PaletteStyle.SLATE -> PaletteColors(
        light = Color(0xFF4A5C6A),
        lightContainer = Color(0xFFD6E6F5),
        onLightContainer = Color(0xFF071E2B),
        dark = Color(0xFFB4C9D9),
        darkContainer = Color(0xFF334552),
        onDarkContainer = Color(0xFFD6E6F5),
    )
    PaletteStyle.SAND -> PaletteColors(
        light = Color(0xFF7A5B2E),
        lightContainer = Color(0xFFF6E0B8),
        onLightContainer = Color(0xFF2B1D00),
        dark = Color(0xFFE3C38F),
        darkContainer = Color(0xFF5E4419),
        onDarkContainer = Color(0xFFF6E0B8),
    )
    PaletteStyle.AURORA -> PaletteColors(
        light = Color(0xFF7B3FA0),
        lightContainer = Color(0xFFF2D9FF),
        onLightContainer = Color(0xFF2E004B),
        dark = Color(0xFFDDB8FF),
        darkContainer = Color(0xFF5A2A78),
        onDarkContainer = Color(0xFFF2D9FF),
    )
}

private val LightBackground = Color(0xFFFBF8FF)
private val DarkBackground = Color(0xFF121016)

fun peekColorScheme(palette: PaletteStyle, dark: Boolean, amoled: Boolean): ColorScheme {
    val c = palette.colors()
    if (dark) {
        val background = if (amoled) Color.Black else DarkBackground
        val surface = if (amoled) Color.Black else DarkBackground
        val low = if (amoled) Color(0xFF08080A) else Color(0xFF1B191F)
        val container = if (amoled) Color(0xFF0D0D0F) else Color(0xFF1F1D24)
        val high = if (amoled) Color(0xFF151517) else Color(0xFF2A272F)
        val highest = if (amoled) Color(0xFF1C1C1E) else Color(0xFF353138)
        return darkColorScheme(
            primary = c.dark,
            onPrimary = Color(0xFF1A1A1A),
            primaryContainer = c.darkContainer,
            onPrimaryContainer = c.onDarkContainer,
            inversePrimary = c.light,
            secondary = Color(0xFFCBC4D6),
            onSecondary = Color(0xFF322F37),
            secondaryContainer = Color(0xFF48454E),
            onSecondaryContainer = Color(0xFFE7E0F0),
            tertiary = Color(0xFFEFB8C8),
            onTertiary = Color(0xFF4B2531),
            tertiaryContainer = Color(0xFF653B47),
            onTertiaryContainer = Color(0xFFFFD9E0),
            background = background,
            onBackground = Color(0xFFE7E1E9),
            surface = surface,
            onSurface = Color(0xFFE7E1E9),
            surfaceVariant = Color(0xFF48454E),
            onSurfaceVariant = Color(0xFFC9C4D0),
            surfaceTint = c.dark,
            inverseSurface = Color(0xFFE7E1E9),
            inverseOnSurface = Color(0xFF1B1B1F),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF3A373F),
            surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF0E0D11),
            surfaceContainerLow = low,
            surfaceContainer = container,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
        )
    }
    val tinted = c.light.copy(alpha = 0.035f).compositeOver(LightBackground)
    return lightColorScheme(
        primary = c.light,
        onPrimary = Color.White,
        primaryContainer = c.lightContainer,
        onPrimaryContainer = c.onLightContainer,
        inversePrimary = c.dark,
        secondary = Color(0xFF605A71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6DFF2),
        onSecondaryContainer = Color(0xFF1C1A24),
        tertiary = Color(0xFF7C5262),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E0),
        onTertiaryContainer = Color(0xFF30121F),
        background = tinted,
        onBackground = Color(0xFF1B1B21),
        surface = tinted,
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE7E0F0),
        onSurfaceVariant = Color(0xFF48454E),
        surfaceTint = c.light,
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF3EFF7),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFD8D2DE),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F2FA),
        surfaceContainer = Color(0xFFEFEBF5),
        surfaceContainerHigh = Color(0xFFE9E5F0),
        surfaceContainerHighest = Color(0xFFE3DFEA),
    )
}

val PeekTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)

val PeekShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

@Composable
fun PeekTheme(
    settings: PeekSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseScheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        else -> peekColorScheme(settings.palette, dark, settings.amoled)
    }
    val scheme = if (dark && settings.amoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0C),
            surfaceContainer = Color(0xFF101012),
            surfaceContainerHigh = Color(0xFF18181A),
            surfaceContainerHighest = Color(0xFF202022),
        )
    } else {
        baseScheme
    }
    val view = LocalView.current
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val light = scheme.surface.luminance() > 0.5f
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = PeekTypography,
        shapes = PeekShapes,
        content = content,
    )
}
