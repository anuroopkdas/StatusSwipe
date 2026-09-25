package com.statusswipe.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = SurfaceOled,
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = AmberPrimaryDark,
    onSecondary = SurfaceOled,
    background = SurfaceOled,
    onBackground = TextPrimary,
    surface = SurfaceElevated,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceCardBorder,
    error = CrimsonError,
    errorContainer = CrimsonContainer,
    onErrorContainer = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = LightSurface,
    primaryContainer = AmberContainer,
    onPrimaryContainer = AmberPrimaryDark,
    background = LightSurface,
    onBackground = LightTextPrimary,
    surface = LightSurfaceCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder
)

@Composable
fun StatusSwipeTheme(
    darkTheme: Boolean = true, // Default to sleek dark mode for utility tool
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
