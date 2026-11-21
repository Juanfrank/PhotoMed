package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.GrayImage
import com.photomed.app.align.LineMeasure
import com.photomed.app.align.Periorbital
import com.photomed.app.align.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.random.Random

/** Ojeras (zona más oscura que la mejilla de debajo) y bolsas (claro sobre un surco paralelo al párpado). */
class PeriorbitalTest {
    private val w = 600
    private val faceW = 400f / w            // cara de 400 px
    private val lidY = 200
    private val sc = 140f to "iris"         // mm por unidad del ancho (cara ≈ 93 mm)

    private fun mesh(): List<Pt> {
        val m = MutableList(478) { Pt(0.5f, 0.5f) }
        Periorbital.LID_R.forEachIndexed { k, i -> m[i] = Pt((150f + k * 15f) / w, lidY / w.toFloat()) }
        Periorbital.LID_L.forEachIndexed { k, i -> m[i] = Pt((450f - k * 15f) / w, lidY / w.toFloat()) }
        return m
    }

    /** Piel uniforme (L* 65) con ruido; [dark]: ojera de ΔL* bajo el párpado; [bag]: bolsa con su surco. */
    private fun image(dark: Float = 0f, bag: Boolean = false): GrayImage {
        val r = Random(3)
        val l = FloatArray(w * w) { i ->
            val t = (i / w - lidY).toFloat()   // distancia bajo el párpado (px)
            var v = 65f + (r.nextFloat() - 0.5f) * 1.5f
            if (t in 5f..40f) v -= dark
            if (bag && t > 0) v += 6f * exp(-((t - 18f) * (t - 18f)) / 60f) - 9f * exp(-((t - 36f) * (t - 36f)) / 30f)
            v
        }
        // luminancia 0..1 a partir de L*
        return GrayImage(w, w, FloatArray(l.size) { i -> val f = (l[i] + 16f) / 116f; val y = f * f * f
            if (y <= 0.0031308f) 12.92f * y else 1.055f * Math.pow(y.toDouble(), 1 / 2.4).toFloat() - 0.055f })
    }

    private fun run(img: GrayImage, labels: FaceLabels? = null) =
        listOf(true, false).flatMap { Periorbital.analyze(mesh(), it, Periorbital.maps(img, null, 400f), labels, faceW, sc) }

    @Test fun plainSkinHasNothing() { assertTrue(run(image()).isEmpty()) }

    @Test fun darkCircleFollowsTheDarkZone() {
        val out = run(image(dark = 8f))
        val dc = out.filter { it.layer == AnnotLayer.DARK_CIRCLES }
        assertEquals(2, dc.size)
        assertTrue(out.none { it.layer == AnnotLayer.EYE_BAGS })
        // borde inferior ≈ 40 px bajo el párpado (no un polígono fijo)
        val bottom = dc[0].points.maxOf { it.y } * w - lidY
        assertTrue("borde a $bottom px", bottom in 32f..46f)
        val m = dc[0].metrics!!
        assertTrue("ΔL* ${m.deltaL}", m.deltaL!! in 6f..10f)
        assertTrue(m.type!!.contains("intensidad")); assertTrue(m.grade!! >= 2)
        // la montura de unas gafas (segmentación: accesorio) corta la zona
        val glasses = FaceLabels(w, w, ByteArray(w * w) { i -> if (i / w in lidY + 20..lidY + 26) FaceLabels.OTHER.toByte() else FaceLabels.FACE_SKIN.toByte() })
        val cut = run(image(dark = 8f), glasses).filter { it.layer == AnnotLayer.DARK_CIRCLES }
        assertTrue(cut.all { it.points.maxOf { p -> p.y } * w - lidY < 24f })
    }

    @Test fun eyeBagWithItsLowerCrease() {
        val bags = run(image(bag = true)).filter { it.layer == AnnotLayer.EYE_BAGS }
        assertEquals(2, bags.size)
        val f = bags[0].metrics!!.features.toMap()
        // surco a 36 px del párpado: 36 / 600 × 140 mm ≈ 8,4 mm
        assertEquals(36f / w * sc.first, f.getValue("Altura (párpado → surco inferior), mm"), 1.2f)
        assertTrue(f.getValue("Relieve (claro sobre la bolsa → surco), ΔL*") >= 6f)
        assertTrue(f.getValue("Grado orientativo (0–3)") >= 2f)
        assertTrue(LineMeasure.area(bags[0].points) > 0f)
    }
}
