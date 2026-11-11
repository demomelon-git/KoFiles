package com.example.darkfiles.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра в духе Google Files: синий акцент, тёмно-серые поверхности
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF174EA6),
    onPrimaryContainer = Color(0xFFD3E3FD),
    background = Color(0xFF1F2023),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1F2023),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2B2D31),
    onSurfaceVariant = Color(0xFFBDC1C6),
    surfaceContainer = Color(0xFF28292D),
    surfaceContainerHigh = Color(0xFF303134),
    error = Color(0xFFF28B82)
)

private val Light = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFE8EAED)
)

@Composable
fun DarkFilesTheme(theme: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        1 -> isSystemInDarkTheme()
        2 -> false
        else -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
