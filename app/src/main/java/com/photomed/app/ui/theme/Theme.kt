package com.photomed.app.ui.theme

import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Roles de color adicionales de M3 (fixed, shadow) según Juanfrank/MD. */
val LocalExtendedColors = staticCompositionLocalOf { Md3LightExtendedColors }

/**
 * Tema Material 3 construido 1:1 con los tokens de Juanfrank/MD (color, tipografía, forma).
 * El modo oscuro no es una inversión: cada rol toma otro tono de la misma paleta tonal.
 */
@Composable
fun PhotoMedTheme(darkTheme: Boolean = AppTheme.isDark(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) Md3DarkExtendedColors else Md3LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) Md3DarkColorScheme else Md3LightColorScheme,
            typography = Md3Typography,
            shapes = Md3Shapes,
            content = content,
        )
    }
}

/** Tema elegido en Ajustes: el del sistema, claro u oscuro. */
enum class ThemeMode(private val labelEs: String) {
    SYSTEM(trn("Según el sistema")), LIGHT(trn("Claro")), DARK(trn("Oscuro"));

    /** Etiqueta en el idioma de la interfaz. */
    val label: String get() = tr(labelEs)
}

/** Preferencia de tema (se aplica al instante en toda la app). */
object AppTheme {
    const val PREF = "theme_mode"
    var mode by androidx.compose.runtime.mutableStateOf(ThemeMode.SYSTEM)

    fun load(context: android.content.Context) {
        mode = runCatching { ThemeMode.valueOf(context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE)
            .getString(PREF, ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
        applyToSystem(context)
    }

    fun set(context: android.content.Context, m: ThemeMode) {
        mode = m
        context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE).edit().putString(PREF, m.name).apply()
        applyToSystem(context)
    }

    /** Android 12+: también el fondo de arranque y los diálogos del sistema siguen el tema elegido. */
    private fun applyToSystem(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        val ui = context.getSystemService(android.app.UiModeManager::class.java) ?: return
        runCatching { ui.setApplicationNightMode(when (mode) {
            ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
        }) }
    }

    @Composable
    fun isDark(): Boolean = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
}

/** Superficie inmersiva oscura para la cámara y los visores de imagen. */
@Composable
fun ImmersiveTheme(content: @Composable () -> Unit) = PhotoMedTheme(darkTheme = true, content = content)
