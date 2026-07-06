package com.amwangfan.omnireader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315D50),
    onPrimary = Color.White,
    secondary = Color(0xFF6F5A2E),
    tertiary = Color(0xFF4E6073),
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFF7F8F5),
    surfaceVariant = Color(0xFFE3EAE5),
    onSurfaceVariant = Color(0xFF414844),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91CFB9),
    secondary = Color(0xFFD9BD7A),
    tertiary = Color(0xFFAFC7DF),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF29312D),
    onSurfaceVariant = Color(0xFFC3CCC6),
)

@Composable
fun OmniReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
