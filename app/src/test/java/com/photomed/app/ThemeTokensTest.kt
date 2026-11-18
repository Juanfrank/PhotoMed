package com.photomed.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.photomed.app.ui.theme.Md3DarkColorScheme
import com.photomed.app.ui.theme.Md3LightColorScheme
import com.photomed.app.ui.theme.Md3Palette
import com.photomed.app.ui.theme.Md3Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica el tema contra los tokens de Juanfrank/MD y los requisitos de contraste WCAG. */
class ThemeTokensTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    @Test
    fun rolesMatchRepositoryTones() {
        assertEquals(Md3Palette.primary40, Md3LightColorScheme.primary)
        assertEquals(Md3Palette.primary80, Md3DarkColorScheme.primary)
        assertEquals(Md3Palette.neutral98, Md3LightColorScheme.surface)
        assertEquals(Md3Palette.neutral6, Md3DarkColorScheme.surface)
        assertEquals(Md3Palette.primary30, Md3DarkColorScheme.primaryContainer)
        assertEquals(57f, Md3Typography.displayLarge.fontSize.value, 0.01f)
        assertEquals(14f, Md3Typography.labelLarge.fontSize.value, 0.01f)
    }

    @Test
    fun textPairsMeetWcagAA() {
        for (s in listOf(Md3LightColorScheme, Md3DarkColorScheme)) {
            val pairs = listOf(
                s.onPrimary to s.primary, s.onPrimaryContainer to s.primaryContainer,
                s.onSecondaryContainer to s.secondaryContainer, s.onTertiaryContainer to s.tertiaryContainer,
                s.onSurface to s.surface, s.onSurfaceVariant to s.surface, s.onSurface to s.surfaceContainerHighest,
                s.onError to s.error, s.onErrorContainer to s.errorContainer, s.inverseOnSurface to s.inverseSurface,
            )
            pairs.forEach { (fg, bg) -> assertTrue("contraste ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5) }
            assertTrue(contrast(s.outline, s.surface) >= 3.0)
        }
    }
}
