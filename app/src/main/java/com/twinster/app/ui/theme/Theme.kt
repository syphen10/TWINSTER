package com.twinster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TwinsterGreen = Color(0xFF1ED760)
val TwinsterPink = Color(0xFFFF4FD8)
val TwinsterBlack = Color(0xFF0A0A0F)
val TwinsterSurface = Color(0x33FFFFFF)

/**
 * Extended accent family, picked to sit naturally alongside the brand green/pink on the same color
 * wheel AnimatedMoodBackground already interpolates across (green ~141°, pink ~309°). Cyan sits
 * within that arc — a cooler stop between the two brand hues — while amber sits on the opposite
 * arc (through red/orange) so it reads as a genuinely different temperature rather than a third
 * point on the same green-pink line. Both stay usable as lerp endpoints alongside the brand colors
 * without clashing.
 */
val TwinsterCyan = Color(0xFF3FE0D0)
val TwinsterAmber = Color(0xFFFFA94D)

// Semantic tokens — named so screens stop reaching for ad hoc magic hex values (e.g. the old
// Color(0xFFFFB4B4) for permission-denied text in ConnectScreen).
val TwinsterSuccess = TwinsterGreen
val TwinsterWarning = TwinsterAmber
val TwinsterError = Color(0xFFFF6B6B)
val TwinsterNeutral = Color(0xFFB8B8C4)

private val DarkColors = darkColorScheme(
    primary = TwinsterGreen,
    secondary = TwinsterPink,
    tertiary = TwinsterCyan,
    error = TwinsterError,
    background = TwinsterBlack,
    surface = TwinsterBlack,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun TwinsterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = TwinsterTypography,
        content = content
    )
}
