package com.photomed.app

import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.ui.compare.Timeline
import com.photomed.app.ui.compare.gridColumns
import com.photomed.app.ui.compare.toggleSelection
import com.photomed.app.ui.gallery.anonymizedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompareMathTest {
    @Test
    fun timelineSegmentsAcrossNPhotos() {
        val t = Timeline.of(4, 1.25f)
        assertEquals(1, t.from); assertEquals(2, t.to); assertEquals(0.25f, t.fraction, 1e-6f)
        assertEquals(0.75f, t.dividerPosition, 1e-6f)
        // El final cae en la última transición (no fuera de rango).
        val end = Timeline.of(4, 3f)
        assertEquals(2, end.from); assertEquals(3, end.to); assertEquals(1f, end.fraction, 1e-6f)
        // Valores fuera de rango se acotan.
        assertEquals(0f, Timeline.of(4, -2f).value, 1e-6f)
        assertEquals(3f, Timeline.of(4, 99f).value, 1e-6f)
        // Dos fotos: equivale al deslizador clásico.
        val two = Timeline.of(2, 0.5f)
        assertEquals(0, two.from); assertEquals(1, two.to); assertEquals(0.5f, two.dividerPosition, 1e-6f)
        // Casos degenerados no fallan.
        assertEquals(0, Timeline.of(1, 0.5f).segment)
        assertEquals(0, Timeline.of(0, 0.5f).count)
    }

    @Test
    fun dividerDragStaysInActiveTransition() {
        var t = Timeline.of(5, 2.5f)
        for (x in listOf(0.9f, 0.5f, 0.0f, -0.3f, 1.4f, 0.2f)) {
            t = t.withDivider(x)
            assertEquals("el gesto no salta de transición", 2, t.segment)
            assertTrue(t.fraction in 0f..1f)
        }
        assertEquals(0.8f, t.fraction, 1e-6f)
    }

    @Test
    fun gridMaximizesCellSize() {
        // Área vertical de teléfono (3:4 por celda).
        assertEquals(1, gridColumns(1, 360f, 560f))
        assertEquals(2, gridColumns(2, 360f, 300f))   // poco alto: lado a lado
        assertEquals(1, gridColumns(2, 360f, 1000f))  // muy alto: apiladas
        assertEquals(2, gridColumns(4, 360f, 560f))
        val c = gridColumns(9, 360f, 560f)
        assertTrue(c in 2..3)
        for (n in 1..12) {
            val cols = gridColumns(n, 360f, 520f)
            assertTrue(cols in 1..n)
        }
    }

    @Test
    fun multiSelectionKeepsOrderAndCap() {
        var s = emptyList<String>()
        for (k in listOf("a", "b", "c")) s = toggleSelection(s, k)
        assertEquals(listOf("a", "b", "c"), s)
        s = toggleSelection(s, "b")
        assertEquals(listOf("a", "c"), s)
        s = toggleSelection(s, "d", max = 3); s = toggleSelection(s, "e", max = 3)
        assertEquals(listOf("c", "d", "e"), s)
        assertFalse("a" in s)
    }

    @Test
    fun anonymizedNamesAreUniqueAndWithoutPatientData() {
        val uri = android.net.Uri.parse("file:///x/001_Ana_Perez/labios_1.jpg")
        val p = Photo(uri, "labios_20261118_101530_123.jpg", Zone.LIPS, 1_763_400_000_000L)
        val names = (1..3).map { anonymizedName(p, it) }
        assertEquals(3, names.toSet().size)
        assertTrue(names.all { it.startsWith("PhotoMed_${Zone.LIPS.id}_") && !it.contains("Ana") })
        assertFalse(anonymizedName(p).contains("_0"))
    }
}
