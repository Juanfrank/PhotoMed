package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.ColorAdjust
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.MeasureNumbering
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneCriteria
import com.photomed.app.ui.compare.correctedWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tanda 3 de la revisión integral: rendimiento (mismos resultados, menos trabajo). */
class PerformanceBatchTest {
    private fun px(seed: Int) = IntArray(4000) { i ->
        val v = (i * 2654435761L + seed).toInt()
        (0xFF shl 24) or (v and 0xFFFFFF)
    }

    private fun maxDiff(a: IntArray, b: IntArray) = a.indices.maxOf { i ->
        maxOf(abs(((a[i] shr 16) and 0xFF) - ((b[i] shr 16) and 0xFF)), abs(((a[i] shr 8) and 0xFF) - ((b[i] shr 8) and 0xFF)), abs((a[i] and 0xFF) - (b[i] and 0xFF)))
    }

    /** Color con tablas: igual que el cálculo exacto (±1 sin curva de L*, ±3 con la tabla 3D). */
    @Test fun colorTablesMatchTheExactAdjustment() {
        val m = doubleArrayOf(1.08, 0.02, -0.01, 0.01, 0.97, 0.0, -0.02, 0.03, 0.92)
        val plain = ColorAdjust(m, null, 0.2, 1.0, -1.0, "prueba")
        val p = px(7)
        assertTrue(maxDiff(plain.apply(p), plain.applyExact(p)) <= 1)
        val curve = DoubleArray(101) { l -> (l + 6.0 * kotlin.math.sin(l / 100.0 * Math.PI)).coerceIn(0.0, 100.0) }
        val toned = ColorAdjust(m, curve, 0.2, 1.0, -1.0, "prueba")
        val d = maxDiff(toned.apply(p), toned.applyExact(p))
        assertTrue("diferencia $d", d <= 3)
        // alfa intacta
        assertEquals(p[5] ushr 24, toned.apply(p)[5] ushr 24)
    }

    /** Números de las medidas y su colocación: la misma instancia para las mismas líneas (sin recalcular). */
    @Test fun measureNumberingIsMemoized() {
        val lines = listOf(AnnotLine(AnnotLayer.FOLDS, listOf(Pt(0.1f, 0.1f), Pt(0.5f, 0.5f)), false, metrics = LineMetrics("Surco 1")),
            AnnotLine(AnnotLayer.SCALP_MAP, listOf(Pt(0f, 0f), Pt(0.1f, 0f), Pt(0.1f, 0.1f)), true, filled = true))
        val n1 = MeasureNumbering.of(listOf(lines)); val n2 = MeasureNumbering.of(listOf(lines))
        assertSame(n1, n2)
        val layers = setOf(AnnotLayer.FOLDS, AnnotLayer.SCALP_MAP)
        assertSame(MeasureNumbering.placeCached(lines, layers, n1, 0.02f), MeasureNumbering.placeCached(lines, layers, n1, 0.0201f))
        assertEquals(1, MeasureNumbering.placeCached(lines, layers, n1, 0.02f).size)
    }

    /** Pies y escena girada: una vez por máscara. */
    @Test fun feetAndRotationComputedOncePerMask() {
        val w = 40; val h = 54
        val c = ZoneCriteria.Classes(w, h, ByteArray(w * h) { i -> if ((i % w) in 10..30 && (i / w) in 10..50) 2 else 0 })
        assertSame(ZoneCriteria.feetOf(c), ZoneCriteria.feetOf(c))
        val s = ZoneCriteria.Scene(emptyMap(), null, c)
        val r1 = with(ZoneCriteria) { s.rotatedCcw(1) }; val r2 = with(ZoneCriteria) { s.rotatedCcw(1) }
        assertSame(r1.classes, r2.classes)
    }

    @Test fun compareDecodesSmallerWithMorePhotos() {
        assertEquals(1080, correctedWidth(2)); assertEquals(720, correctedWidth(4)); assertEquals(540, correctedWidth(12))
    }
}
