package com.trackit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFD5B25),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2218),
    onPrimaryContainer = Color(0xFFFFDACB),
    secondary = Color(0xFFFF8A5C),
    onSecondary = Color(0xFF3A1200),
    background = Color(0xFF13161B),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF1C2127),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF252A31),
    onSurfaceVariant = Color(0xFF9AA3AD),
    surfaceContainerLowest = Color(0xFF101318),
    surfaceContainerLow = Color(0xFF181C21),
    surfaceContainer = Color(0xFF1C2127),
    surfaceContainerHigh = Color(0xFF232A32),
    surfaceContainerHighest = Color(0xFF2A313A),
    outline = Color(0xFF3A424B),
    outlineVariant = Color(0xFF2B3139),
    error = Color(0xFFF2605C),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF461E1F),
    onErrorContainer = Color(0xFFFFD9D6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFFE4715),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4DA),
    onPrimaryContainer = Color(0xFF5C1600),
    secondary = Color(0xFFFF8A5C),
    onSecondary = Color.White,
    background = Color(0xFFFCF7EC),
    onBackground = Color(0xFF14181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFF1EADB),
    onSurfaceVariant = Color(0xFF6A727B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF8F1),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF6F0E3),
    outline = Color(0xFFD9CFBA),
    outlineVariant = Color(0xFFE8E0CE),
    error = Color(0xFFD62839),
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E0),
    onErrorContainer = Color(0xFF8A1218),
)

private val isDark: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Green text/amounts for Plus. */
val MaterialTheme.plusColor: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF48CD89) else Color(0xFF1E9E5E)

/** Red text/amounts for Minus. */
val MaterialTheme.minusColor: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFF2605C) else Color(0xFFD62839)

/** Solid circle colors (white icon on top). */
val MaterialTheme.plusSolid: Color
    @Composable @ReadOnlyComposable get() = Color(0xFF1FA463)

val MaterialTheme.minusSolid: Color
    @Composable @ReadOnlyComposable get() = Color(0xFFD64545)

/** Big Plus / Minus card gradients. */
val MaterialTheme.plusCardTop: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF145A38) else Color(0xFF22B26C)

val MaterialTheme.plusCardBottom: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF173B2F) else Color(0xFF168A52)

val MaterialTheme.minusCardTop: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF5F2323) else Color(0xFFE8484B)

val MaterialTheme.minusCardBottom: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF4A1E1F) else Color(0xFFC82C30)

@Composable
fun TrackitTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
