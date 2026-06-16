package com.widgetforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C9EFF),
    onPrimary = Color(0xFF001C6B),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFB8C8FF),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E2433),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE2E8F0),
    outline = Color(0xFF334155),
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001C6B),
    secondary = Color(0xFF3B5BDB),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFFEEF2FF),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0D1117),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626)
)

@Composable
fun WidgetForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
