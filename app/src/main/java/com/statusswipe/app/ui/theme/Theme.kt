package com.statusswipe.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingBlack,
    primaryContainer = NothingCharcoal,
    onPrimaryContainer = NothingRed,
    secondary = NothingLavender,
    onSecondary = NothingBlack,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingCharcoal,
    onSurface = NothingWhite,
    surfaceVariant = NothingButtonSurface,
    onSurfaceVariant = NothingGrey,
    outline = NothingBorder,
    error = NothingError,
    errorContainer = NothingCharcoal,
    onErrorContainer = NothingRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightWarmAmber,
    onPrimary = NothingWhite,
    primaryContainer = LightSurface,
    onPrimaryContainer = LightWarmAmber,
    secondary = NothingRed,
    onSecondary = NothingWhite,
    background = LightStoneCream,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightStoneCream,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = NothingError,
    errorContainer = LightSurface,
    onErrorContainer = NothingRed
)

object NothingTheme {
    @Composable
    fun switchColors(): SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = NothingRed,
        checkedTrackColor = Color.Transparent,
        checkedBorderColor = NothingBorder,
        uncheckedThumbColor = NothingGrey,
        uncheckedTrackColor = Color.Transparent,
        uncheckedBorderColor = NothingBorder
    )

    @Composable
    fun sliderColors(): SliderColors = SliderDefaults.colors(
        thumbColor = NothingRed,
        activeTrackColor = NothingRed,
        inactiveTrackColor = NothingCharcoal,
        activeTickColor = NothingBlack,
        inactiveTickColor = NothingGrey
    )
}

@Composable
fun StatusSwipeTheme(
    darkTheme: Boolean = true, // Force dark theme by default based on Nothing OS vibe
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = (if (darkTheme) NothingBlack else LightStoneCream).toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = (if (darkTheme) NothingBlack else LightStoneCream).toArgb()
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
