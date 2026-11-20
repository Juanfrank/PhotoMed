package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.GrayImage
import com.photomed.app.align.LineMeasure
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.ui.compare.hitLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow

class LineMetricsTest {
    private fun toGamma(y: Float) = if (y <= 0.0031308f) 12.92f * y else 1.055f * y.pow(1f / 2.4f) - 0.055f

    /** Surco en V vertical (en x = w/2), iluminado de frente sobre piel lambertiana: Y = Y0 · cos θ. */
    private fun vGroove(w: Int, h: Int, widthPx: Float, depthPx: Float): GrayImage {
        val y0 = 0.35f
        val slope = 2 * depthPx / widthPx
        return GrayImage(w, h, FloatArray(w * h) { i ->
            val x = i % w; val inGroove = abs(x - w / 2f) < widthPx / 2
            toGamma(if (inGroove) y0 * cos(atan(slope)) else y0)
        })
    }

    @Test fun irisScale() {
        // malla mínima: pupilas a 0.3 de distancia y diámetro de iris 0.055 → 11,7 mm / 0.055
        val mesh = MutableList(478) { Pt(0.5f, 0.5f) }
        mesh[468] = Pt(0.35f, 0.5f); mesh[473] = Pt(0.65f, 0.5f)
        mesh[469] = Pt(0.3775f, 0.5f); mesh[471] = Pt(0.3225f, 0.5f)
        mesh[474] = Pt(0.6775f, 0.5f); mesh[476] = Pt(0.6225f, 0.5f)
        val (mm, src) = LineMeasure.scale(mesh)!!
        assertEquals("iris", src)
        assertEquals(11.7f / 0.055f, mm, 0.5f)
        // iris implausible (demasiado grande frente a la interpupilar) → interpupilar
        mesh[469] = Pt(0.45f, 0.5f); mesh[471] = Pt(0.25f, 0.5f); mesh[474] = Pt(0.75f, 0.5f); mesh[476] = Pt(0.55f, 0.5f)
        val (mm2, src2) = LineMeasure.scale(mesh)!!
        assertEquals("interpupilar", src2)
        assertEquals(63f / 0.3f, mm2, 0.5f)
    }

    @Test fun shapeFromShadingRecoversGrooveDepth() {
        // 400 px = 100 mm (4 px/mm); surco de 3 mm de ancho y 1 mm de profundidad
        val img = vGroove(400, 200, widthPx = 12f, depthPx = 4f)
        val mmPerUnit = 100f
        val pts = (0..10).map { Pt(0.5f, (40 + it * 10) / 400f) }
        val p = LineMeasure.profile(img, pts, mmPerUnit, halfMm = 4f)!!
        val depth = p.depthMm / LineMeasure.SHADING_GAIN
        assertEquals(1f, depth, 0.3f)
        assertEquals(3f, p.widthMm, 0.8f)
        assertTrue(p.contrastL > 3f)
        // piel lisa: sin valle
        val flat = GrayImage(400, 200, FloatArray(400 * 200) { 0.6f })
        val pf = LineMeasure.profile(flat, pts, mmPerUnit)
        assertTrue(pf == null || pf.depthMm == 0f)
    }

    @Test fun lengthAndGrade() {
        val m = LineMeasure.line("x", listOf(Pt(0.1f, 0.1f), Pt(0.2f, 0.1f), Pt(0.3f, 0.1f)), vGroove(400, 400, 8f, 2f), 100f to "iris")
        assertEquals(20f, m.lengthMm!!, 0.01f)
        assertEquals(0, LineMeasure.grade(0.05f)); assertEquals(3, LineMeasure.grade(0.6f)); assertEquals(5, LineMeasure.grade(3f))
    }

    @Test fun darkCircleColorimetry() {
        val cheek = floatArrayOf(65f, 12f, 18f)
        val vasc = LineMeasure.darkCircle("o", floatArrayOf(55f, 15f, 12f), cheek, 0.001f, 100f to "iris")
        assertEquals(10f, vasc.deltaL!!, 0.01f)
        assertTrue(vasc.type!!.startsWith("vascular"))
        assertEquals(10f, vasc.areaMm2!!, 0.01f)
        val pig = LineMeasure.darkCircle("o", floatArrayOf(55f, 13f, 21f), cheek, 0.001f, 100f to "iris")
        assertTrue(pig.type!!.startsWith("pigmentaria"))
        assertTrue(pig.ita!! < pig.itaRef!!)
        val shadow = LineMeasure.darkCircle("o", floatArrayOf(58f, 12.3f, 18.2f), cheek, 0.001f, 100f to "iris")
        assertTrue(shadow.type!!.startsWith("estructural"))
        // ITA de una piel clara (L* 70, b* 15) ≈ 53°
        assertEquals(53.1f, LineMeasure.ita(floatArrayOf(70f, 10f, 15f)), 0.2f)
    }

    @Test fun tapFindsNearestMeasuredLine() {
        val a = AnnotLine(AnnotLayer.FOREHEAD, listOf(Pt(0.2f, 0.2f), Pt(0.8f, 0.2f)), false, metrics = LineMetrics("Frente 1"))
        val b = AnnotLine(AnnotLayer.FOREHEAD, listOf(Pt(0.2f, 0.3f), Pt(0.8f, 0.3f)), false, metrics = LineMetrics("Frente 2"))
        val dark = AnnotLine(AnnotLayer.DARK_CIRCLES, listOf(Pt(0.3f, 0.6f), Pt(0.5f, 0.6f), Pt(0.5f, 0.7f), Pt(0.3f, 0.7f)), true, filled = true, metrics = LineMetrics("Ojera"))
        val noMetrics = AnnotLine(AnnotLayer.EYES, listOf(Pt(0.2f, 0.21f), Pt(0.8f, 0.21f)), true)
        val all = listOf(a, b, dark, noMetrics)
        val layers = setOf(AnnotLayer.FOREHEAD, AnnotLayer.DARK_CIRCLES, AnnotLayer.EYES)
        assertEquals("Frente 1", hitLine(all, layers, Pt(0.5f, 0.21f), 0.03f)?.metrics?.name)
        assertEquals("Frente 2", hitLine(all, layers, Pt(0.5f, 0.28f), 0.03f)?.metrics?.name)
        assertEquals("Ojera", hitLine(all, layers, Pt(0.4f, 0.65f), 0.03f)?.metrics?.name)
        assertNull(hitLine(all, layers, Pt(0.5f, 0.45f), 0.03f))
        // capa oculta: no se selecciona
        assertNull(hitLine(all, setOf(AnnotLayer.DARK_CIRCLES), Pt(0.5f, 0.21f), 0.03f))
        assertNotNull(hitLine(all, setOf(AnnotLayer.DARK_CIRCLES), Pt(0.4f, 0.65f), 0.03f))
    }
}
