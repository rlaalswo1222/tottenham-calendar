package dev.coys.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.coys.calendar.data.Outcome

val SpursNavy = Color(0xFF132257)
val SpursNavyLight = Color(0xFF2A3A7A)
val PitchGreen = Color(0xFF2E7D4F)

val WinColor = Color(0xFF2E9E4F)
val DrawColor = Color(0xFF8A8F98)
val LossColor = Color(0xFFD64545)

fun Outcome?.color(): Color = when (this) {
    Outcome.WIN -> WinColor
    Outcome.DRAW -> DrawColor
    Outcome.LOSS -> LossColor
    null -> DrawColor
}

fun Outcome?.label(): String = when (this) {
    Outcome.WIN -> "승"
    Outcome.DRAW -> "무"
    Outcome.LOSS -> "패"
    null -> "-"
}

private val Light = lightColorScheme(
    primary = SpursNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2F3),
    onPrimaryContainer = SpursNavy,
    secondary = SpursNavyLight,
    background = Color(0xFFF5F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EBF2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB8C4F0),
    onPrimary = SpursNavy,
    primaryContainer = SpursNavyLight,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9FB0E8),
    background = Color(0xFF0B1020),
    surface = Color(0xFF141A2E),
    surfaceVariant = Color(0xFF1E2640),
)

@Composable
fun SpursTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
