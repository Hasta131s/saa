package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DeepDarkColors = darkColorScheme(
    primary = WhiteAccent,
    onPrimary = BlackBackground,
    secondary = GreyAccent,
    onSecondary = WhiteAccent,
    background = BlackBackground,
    onBackground = WhiteAccent,
    surface = CharcoalSurface,
    onSurface = WhiteAccent,
    outline = AccentBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DeepDarkColors,
        typography = Typography,
        content = content
    )
}
