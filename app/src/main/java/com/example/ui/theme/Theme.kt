package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoboticsDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = CyberCyan,
    secondary = CyberOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A1F00),
    onSecondaryContainer = CyberOrangeGlow,
    tertiary = AiPurple,
    onTertiary = Color.White,
    background = SpaceBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = EmergencyRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use our custom futuristic robotics dark palette for authentic feel
    MaterialTheme(
        colorScheme = RoboticsDarkColorScheme,
        typography = Typography,
        content = content
    )
}
