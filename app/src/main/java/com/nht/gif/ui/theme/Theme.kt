package com.nht.gif.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Mirrors the key values from Theme.EasyGif in themes.xml. Only colors needed by migrated
 *  Compose screens are defined here; the full XML theme remains the source of truth for the rest. */
private val EasyGifColorScheme = darkColorScheme(
    primary = Color(0xFF40A367),
    onPrimary = Color(0xFFF0F1ED),
    surface = Color(0xFF191C19),
    onSurface = Color(0xFFF0F1ED),
    background = Color(0xFF191C19),
    onBackground = Color(0xFFF0F1ED),
    surfaceVariant = Color(0xFF757874),
    outline = Color(0xFFF0F1ED),
)

@Composable
fun EasyGifTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EasyGifColorScheme,
        content = content,
    )
}
