package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.align.foldPairs
import com.photomed.app.align.landmarkTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los surcos analizados de las dos fotos ayudan a alinear (puntos de correspondencia). */
class FoldAlignTest {
    private fun fold(name: String, dx: Float, layer: AnnotLayer = AnnotLayer.FOLDS) =
        AnnotLine(layer, (0..10).map { Pt(0.3f + dx + it * 0.01f, 0.6f + it * 0.02f) }, false, metrics = LineMetrics(name))

    @Test fun matchingFoldsBecomePointPairs() {
        val (b, a) = foldPairs(listOf(fold("Nasogeniano derecho", 0f), fold("Surco añadido 1", 0f), fold("Frente, línea 1", 0f, AnnotLayer.FOREHEAD)),
            listOf(fold("Nasogeniano derecho", 0.05f), fold("Marioneta derecho", 0f)))
        assertEquals(5, b.size); assertEquals(b.keys, a.keys)
        assertTrue(b.keys.all { it.startsWith("fold:Nasogeniano derecho") })
        // con solo los surcos, la traslación sale de ellos
        val t = landmarkTransform(b.keys, b, a)!!.first
        val p = t.apply(a.values.first()); val q = b[a.keys.first()]!!
        assertEquals(q.x, p.x, 1e-3f); assertEquals(q.y, p.y, 1e-3f)
    }
}
