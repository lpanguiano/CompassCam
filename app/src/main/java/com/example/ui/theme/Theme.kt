package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    primaryContainer = HighlightBadgeGreen,
    onPrimaryContainer = NeonGreen,
    secondary = MediumGreen,
    onSecondary = Color.Black,
    secondaryContainer = DarkGreenOverlay,
    onSecondaryContainer = LightGreen,
    tertiary = LightGreen,
    background = BackgroundDark,
    onBackground = OnSurfaceGreen,
    surface = SurfaceDark,
    onSurface = OnSurfaceGreen,
    surfaceVariant = DarkGreenOverlay,
    onSurfaceVariant = LightGreen
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
