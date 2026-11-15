package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FlashoraDarkColorScheme = darkColorScheme(
    primary = FlashoraOrange,
    onPrimary = FlashoraTextPrimary,
    secondary = FlashoraCyan,
    onSecondary = FlashoraBg,
    tertiary = FlashoraPink,
    background = FlashoraBg,
    onBackground = FlashoraTextPrimary,
    surface = FlashoraSurface,
    onSurface = FlashoraTextPrimary,
    surfaceVariant = FlashoraSurfaceElevated,
    onSurfaceVariant = FlashoraTextSecondary,
)

@Composable
fun FlashoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlashoraDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
