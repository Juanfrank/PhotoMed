package com.photomed.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF8D6E63),
    secondaryContainer = Color(0xFFF3E5DF),
    tertiary = Color(0xFFC2185B),
    background = Color(0xFFF8FAF9),
    surface = Color(0xFFF8FAF9),
)

@Composable
fun PhotoMedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
