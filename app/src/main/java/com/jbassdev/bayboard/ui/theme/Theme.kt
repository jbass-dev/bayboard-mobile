package com.jbassdev.bayboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1B3A57)
private val Orange = Color(0xFFF28C28)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0B1F33),
    secondary = Color(0xFF8A4B00),
    secondaryContainer = Color(0xFFFFDCBF),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Orange,
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFF6F7F9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC4EA),
    onPrimary = Color(0xFF0B1F33),
    primaryContainer = Color(0xFF24476A),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFFFB877),
    secondaryContainer = Color(0xFF6A3A00),
    onSecondaryContainer = Color(0xFFFFDCBF),
    tertiary = Orange,
)

/** Badge colours for "running long" — amber reads as caution in both themes. */
data class WarnColors(val container: Color, val content: Color)

@Composable
fun warnColors(): WarnColors =
    if (isSystemInDarkTheme()) WarnColors(Color(0xFF5C4400), Color(0xFFFFE08A))
    else WarnColors(Color(0xFFFFE08A), Color(0xFF3D2E00))

@Composable
fun BayBoardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
