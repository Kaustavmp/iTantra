package dev.itantra.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0C141C)
val SurfaceCard = Color(0xFF192A30)
val SurfaceBorder = Color(0xFF22322C)
val MintPrimary = Color(0xFFBEEBD2)
val EmeraldAccent = Color(0xFF5DCAA5)
val TextMuted = Color(0xFF9FB0A8)
val TextLight = Color(0xFFE6F5EC)
val AlertRed = Color(0xFFD85A30)
val AlertRedBg = Color(0xFF4A1B0C)

private val DarkColorScheme = darkColorScheme(
    primary = MintPrimary,
    secondary = EmeraldAccent,
    background = DarkBg,
    surface = SurfaceCard,
    onPrimary = DarkBg,
    onBackground = TextLight,
    onSurface = TextLight,
    error = AlertRed
)

@Composable
fun ITantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
