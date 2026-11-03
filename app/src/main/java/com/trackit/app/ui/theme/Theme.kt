package com.trackit.app.ui.theme

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF5A2C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2D8),
    onPrimaryContainer = Color(0xFF5C1600),
    secondary = Color(0xFFFF8A3D),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFA),
    onBackground = Color(0xFF241917),
    surface = Color(0xFFFFFBFA),
    onSurface = Color(0xFF241917),
    surfaceVariant = Color(0xFFF6ECE8),
    onSurfaceVariant = Color(0xFF6F5F5A),
    outline = Color(0xFFD9C8C2),
    outlineVariant = Color(0xFFEBDDD8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A4D),
    onPrimary = Color(0xFF3A0D00),
    primaryContainer = Color(0xFF5A2616),
    onPrimaryContainer = Color(0xFFFFDCCF),
    secondary = Color(0xFFFFA05C),
    onSecondary = Color(0xFF3A1A00),
    background = Color(0xFF211D1C),
    onBackground = Color(0xFFF1E7E4),
    surface = Color(0xFF211D1C),
    onSurface = Color(0xFFF1E7E4),
    surfaceVariant = Color(0xFF332D2B),
    onSurfaceVariant = Color(0xFFC7B8B3),
    outline = Color(0xFF5A4F4B),
    outlineVariant = Color(0xFF433B38),
)

private val isDark: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Green used for positive values. */
val MaterialTheme.plusColor: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF4CD694) else Color(0xFF1E9E5E)

/** Red used for negative values. */
val MaterialTheme.minusColor: Color
    @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFFF6B73) else Color(0xFFD62839)

@Composable
fun TrackitTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
