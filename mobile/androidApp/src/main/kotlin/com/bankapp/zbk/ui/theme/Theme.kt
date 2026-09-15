package com.bankapp.zbk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00A0C8)
private val TealDark = Color(0xFF007A9A)
private val Ink = Color(0xFF0B1F2A)
private val Mist = Color(0xFFE8F4F8)
private val Sand = Color(0xFFF5F7F6)

private val LightColors =
    lightColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        primaryContainer = Mist,
        onPrimaryContainer = Ink,
        secondary = TealDark,
        onSecondary = Color.White,
        background = Sand,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        error = Color(0xFFC0392B),
    )

private val DarkColors =
    darkColorScheme(
        primary = Teal,
        onPrimary = Ink,
        secondary = Mist,
        background = Ink,
        onBackground = Color.White,
        surface = Color(0xFF132833),
        onSurface = Color.White,
        error = Color(0xFFE57373),
    )

@Composable
fun ZbkTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
