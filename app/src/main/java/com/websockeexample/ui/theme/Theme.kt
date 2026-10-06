package com.websockeexample.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = LiveBlue,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelRaised,
    onSurfaceVariant = TextMuted,
    outline = Line,
    error = Sell,
    onError = Ink,
)

@Composable
fun WebSockeExampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
