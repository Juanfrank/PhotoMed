package com.photomed.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Roles de color adicionales de M3 (fixed, shadow) según Juanfrank/MD. */
val LocalExtendedColors = staticCompositionLocalOf { Md3LightExtendedColors }

/**
 * Tema Material 3 construido 1:1 con los tokens de Juanfrank/MD (color, tipografía, forma).
 * El modo oscuro no es una inversión: cada rol toma otro tono de la misma paleta tonal.
 */
@Composable
fun PhotoMedTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) Md3DarkExtendedColors else Md3LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) Md3DarkColorScheme else Md3LightColorScheme,
            typography = Md3Typography,
            shapes = Md3Shapes,
            content = content,
        )
    }
}

/** Superficie inmersiva oscura para la cámara y los visores de imagen. */
@Composable
fun ImmersiveTheme(content: @Composable () -> Unit) = PhotoMedTheme(darkTheme = true, content = content)
