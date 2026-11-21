package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.FeatureMetrics
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.MeasureGuide
import com.photomed.app.align.Pt
import com.photomed.app.align.StoredTrace
import com.photomed.app.align.TraceJson
import com.photomed.app.align.TraceSet
import com.photomed.app.ui.compare.hitLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Segmentos de lo que se mide en cada línea (se dibujan punteados al seleccionarla). */
class MeasureGuidesTest {
    /** Malla mínima, cara algo girada en el plano (10°): pupilas, cejas, nariz, ojos. */
    private fun mesh(): List<Pt> {
        val a = Math.toRadians(10.0); val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
        fun p(x: Float, y: Float) = Pt(0.5f + x * c - y * s, 0.5f + x * s + y * c)   // y hacia abajo
        val m = MutableList(478) { p(0f, 0f) }
        m[468] = p(-0.1f, 0f); m[473] = p(0.1f, 0f)
        intArrayOf(70, 63, 105, 66, 107).forEachIndexed { k, i -> m[i] = p(-0.16f + k * 0.03f, -0.06f) }
        intArrayOf(300, 293, 334, 296, 336).forEachIndexed { k, i -> m[i] = p(0.16f - k * 0.03f, -0.06f) }
        m[33] = p(-0.14f, 0f); m[133] = p(-0.06f, 0f); m[263] = p(0.14f, 0f); m[362] = p(0.06f, 0f)
        m[159] = p(-0.1f, -0.015f); m[145] = p(-0.1f, 0.015f); m[386] = p(0.1f, -0.015f); m[374] = p(0.1f, 0.015f)
        m[98] = p(-0.035f, 0.15f); m[327] = p(0.035f, 0.15f); m[122] = p(-0.012f, 0.02f); m[351] = p(0.012f, 0.02f)
        m[196] = p(-0.011f, 0.07f); m[419] = p(0.011f, 0.07f); m[6] = p(0f, 0.01f); m[1] = p(0f, 0.13f); m[168] = p(0f, -0.02f); m[2] = p(0f, 0.16f)
        return m
    }

    @Test fun hairlineMeasuredOnStraightLinesThroughThePupils() {
        val m = mesh()
        val a = Math.toRadians(10.0); val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
        val hair = (-10..10).map { k -> val x = k * 0.02f; Pt(0.5f + x * c - (-0.2f) * s, 0.5f + x * s + (-0.2f) * c) }
        val r = FeatureMetrics.hairlineM(m, hair, 100f, "iris")!!
        val f = r.metrics.features.toMap()
        // ceja a 0,06 y pelo a 0,20 por encima de las pupilas: 0,14 × 100 mm
        assertEquals(14f, f.getValue("Frente central (cejas → pelo), mm"), 0.05f)
        assertEquals(14f, f.getValue("Sobre la ceja derecha (vertical de la pupila), mm"), 0.05f)
        // cada segmento es perpendicular a la línea de los ojos (recto, desde la pupila)
        val ex = Pt(m[473].x - m[468].x, m[473].y - m[468].y)
        r.guides.forEach { g ->
            val dx = g.b.x - g.a.x; val dy = g.b.y - g.a.y
            assertTrue("no es perpendicular", abs(dx * ex.x + dy * ex.y) < 1e-4f * hypot(dx, dy).coerceAtLeast(1e-3f) + 1e-5f)
        }
        // el de la pupila derecha empieza en la pupila
        assertTrue(r.guides.any { hypot(it.a.x - m[468].x, it.a.y - m[468].y) < 1e-5f })
    }

    @Test fun noseHasBridgeAndWidthWithTheirSegments() {
        val r = FeatureMetrics.noseM(mesh(), 100f, "iris")
        val f = r.metrics.features.toMap()
        assertEquals(7f, f.getValue("Ancho alar, mm"), 0.01f)
        assertEquals(2.4f, f.getValue("Ancho del puente (raíz), mm"), 0.01f)
        assertTrue("Ancho del dorso (mitad), mm" in f && "Longitud del dorso (raíz → punta), mm" in f)
        assertEquals(5, r.guides.size)
        assertTrue(r.guides.first().label.startsWith("ancho alar 7"))
    }

    /** Tocar el dorso o las alas de la nariz (sin medidas propias) selecciona la medida de la nariz. */
    @Test fun touchingAnyNoseLineSelectsTheNose() {
        val nose = AnnotLine(AnnotLayer.NOSE, listOf(Pt(0.45f, 0.6f), Pt(0.5f, 0.62f), Pt(0.55f, 0.6f)), false, metrics = LineMetrics("Nariz"))
        val dorsum = AnnotLine(AnnotLayer.NOSE, listOf(Pt(0.48f, 0.4f), Pt(0.48f, 0.55f)), false)
        assertEquals("Nariz", hitLine(listOf(nose, dorsum), setOf(AnnotLayer.NOSE), Pt(0.481f, 0.47f), 0.01f)?.metrics?.name)
    }

    @Test fun guidesFollowTransformsAndAreSaved() {
        val l = AnnotLine(AnnotLayer.FOLDS, listOf(Pt(0.1f, 0.1f), Pt(0.2f, 0.2f)), false, metrics = LineMetrics("Surco"),
            guides = listOf(MeasureGuide("ancho 1,2 mm", Pt(0.1f, 0.2f), Pt(0.3f, 0.4f))))
        val g = l.mapped { Pt(it.x * 2, it.y + 1) }.guides.single()
        assertEquals(Pt(0.2f, 1.2f), g.a); assertEquals(Pt(0.6f, 1.4f), g.b)
        val set = TraceSet(4f / 3f, listOf(StoredTrace(l.layer, l.points, false, false, false, l.metrics, guides = l.guides)))
        val back = TraceJson.read(TraceJson.write(set))!!
        assertEquals("ancho 1,2 mm", back.traces.single().guides.single().label)
        assertNotNull(back.lines(com.photomed.app.align.ImageEdit(rot = 1), com.photomed.app.align.TraceFrame.FULL).single().guides.singleOrNull())
    }
}
