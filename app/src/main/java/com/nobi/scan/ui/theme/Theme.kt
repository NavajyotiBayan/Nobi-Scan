package com.nobi.scan.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class NobiPalette(
    val isDark: Boolean,
    val background: Color,
    val backdropTint: Color,
    val surface: Color,
    val ink: Color,
    val mutedInk: Color,
    val accent: Color,
    val onAccent: Color,
    val rim: Color,
    val glass: Color,
    val lens: Color,
)

private val PearlPalette = NobiPalette(
    isDark = false,
    background = Color(0xFFF8F7FB),
    backdropTint = Color(0xFFE8E4F3),
    surface = Color.White,
    ink = Color(0xFF211A2E),
    mutedInk = Color(0xFF5D536C),
    accent = Color(0xFF6035BF),
    onAccent = Color.White,
    rim = Color(0xCCFFFFFF),
    glass = Color(0x66FFFFFF),
    lens = Color(0xFFDCD5E5),
)

private val GraphitePalette = NobiPalette(
    isDark = true,
    background = Color(0xFF121116),
    backdropTint = Color(0xFF211E29),
    surface = Color(0xFF211E29),
    ink = Color(0xFFF5F1FB),
    mutedInk = Color(0xFFBFB6CD),
    accent = Color(0xFFC6ADFF),
    onAccent = Color(0xFF211833),
    rim = Color(0x3DDFD6EC),
    glass = Color(0x99312D39),
    lens = Color(0xFF413A4D),
)

val LocalNobiPalette = staticCompositionLocalOf { PearlPalette }

private val PearlScheme = lightColorScheme(
    primary = PearlPalette.accent,
    onPrimary = PearlPalette.onAccent,
    background = PearlPalette.background,
    onBackground = PearlPalette.ink,
    surface = PearlPalette.surface,
    onSurface = PearlPalette.ink,
    onSurfaceVariant = PearlPalette.mutedInk,
    surfaceContainer = PearlPalette.backdropTint
)

private val GraphiteScheme = darkColorScheme(
    primary = GraphitePalette.accent,
    onPrimary = GraphitePalette.onAccent,
    background = GraphitePalette.background,
    onBackground = GraphitePalette.ink,
    surface = GraphitePalette.surface,
    onSurface = GraphitePalette.ink,
    onSurfaceVariant = GraphitePalette.mutedInk,
    surfaceContainer = GraphitePalette.backdropTint
)

@Composable
fun NobiScanTheme(isDark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNobiPalette provides if (isDark) GraphitePalette else PearlPalette) {
        MaterialTheme(
            colorScheme = if (isDark) GraphiteScheme else PearlScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun SystemBarContrast(lightBackground: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context as? Activity ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
