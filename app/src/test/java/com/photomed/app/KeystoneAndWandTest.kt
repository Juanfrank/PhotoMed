package com.photomed.app

import com.photomed.app.align.ContourMetrics
import com.photomed.app.align.KeystoneFit
import com.photomed.app.align.MagicWand
import com.photomed.app.align.MaskOps
import com.photomed.app.align.Perspective
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class KeystoneAndWandTest {
    private fun unmapPt(p: Pt, deg: Float): Pt {
        val q = Perspective.unmap(p.x.toDouble(), p.y.toDouble(), 1.0, 4.0 / 3.0, deg.toDouble(), Perspective.DEFAULT_FOCAL.toDouble())
        return Pt(q.x.toFloat(), q.y.toFloat())
    }

    /** Óvalo facial + rasgos internos (coordenadas normalizadas, alto 4/3). */
    private fun face(): List<Pt> {
        val oval = (0 until 36).map { i ->
            val a = i * 2 * Math.PI / 36
            val narrow = if (cos(a) < 0) 0.82 else 1.0 // mentón más estrecho que la frente
            Pt((0.5 + 0.27 * sin(a) * narrow).toFloat(), (0.66 - 0.36 * cos(a)).toFloat())
        }
        val inner = listOf(Pt(0.4f, 0.58f), Pt(0.6f, 0.58f), Pt(0.5f, 0.72f), Pt(0.43f, 0.84f), Pt(0.57f, 0.84f), Pt(0.5f, 0.5f))
        return oval + inner
    }

    @Test
    fun mapAndUnmapAreInverse() {
        val p = Pt(0.31f, 0.92f)
        val q = Perspective.mapPt(unmapPt(p, 9f), 9f)
        assertEquals(p.x, q.x, 1e-4f); assertEquals(p.y, q.y, 1e-4f)
    }

    @Test
    fun keystoneFromFacialContourPoints() {
        val before = face()
        for (truth in listOf(-8f, 5f)) {
            val s = Similarity(0.93f, 0.04f, 0.04f, 0.03f)
            val after = before.map { unmapPt(s.apply(it), truth) } // otra distancia/encuadre + otra inclinación
            val r = KeystoneFit.fromLandmarks(before, after)!!
            assertEquals(truth, r.keystoneDeg, 0.3f)
            assertTrue(r.rms < r.rmsWithout * 0.2f)
        }
        // sin inclinación no inventa corrección
        assertEquals(0f, KeystoneFit.fromLandmarks(before, before.map { Similarity(1.1f, 0f, -0.02f, 0f).apply(it) })!!.keystoneDeg, 0.01f)
    }

    @Test
    fun keystoneFromSilhouetteWidthProfiles() {
        val w = 300; val h = 400
        val before = face().take(36)
        val mb = ContourMetrics.of(MaskOps.polygon(before, w, h))!!
        for (truth in listOf(-8f, 6f)) {
            // la silueta "después" se toma a otra distancia (escala) y con otra inclinación
            val ma = ContourMetrics.of(MaskOps.polygon(before.map { unmapPt(Similarity(0.9f, 0f, 0.05f, 0.06f).apply(it), truth) }, w, h))!!
            val est = KeystoneFit.fromOutlines(mb.outline, ma.outline)
            assertNotNull(est)
            assertEquals(truth, est!!, 1.0f)
        }
    }

    @Test
    fun magicWandSelectsSkinRegionAndMeasuresIt() {
        val w = 240; val h = 320
        val rnd = Random(1)
        val rgb = IntArray(w * h)
        fun c(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        val cx = 120f; val cy = 170f; val rx = 70f; val ry = 100f
        for (y in 0 until h) for (x in 0 until w) {
            val inFace = ((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it } <= 1f
            val inEye = ((x - 95f) / 12f).let { it * it } + ((y - 140f) / 6f).let { it * it } <= 1f
            val n = rnd.nextInt(-6, 7)
            rgb[y * w + x] = when {
                inEye -> c(40 + n, 30 + n, 30 + n)
                inFace -> c(222 + n + (y - 70) / 10, 180 + n, 160 + n) // degradado suave de piel
                else -> c(70 + x / 6 + n, 90 + n, 120 + n)
            }
        }
        val mask = MagicWand.select(rgb, w, h, 150, 210, tolerance = 14f)
        val m = ContourMetrics.of(mask)!!
        val expectedArea = Math.PI * rx * ry
        assertEquals(1.0, mask.area / expectedArea, 0.06) // incluye el ojo (relleno de huecos)
        assertEquals(2 * rx / w, m.maxWidth, 0.03f)
        assertEquals(2 * ry / w, m.height, 0.03f)
        assertTrue(m.outline.size > 50)
        assertTrue(abs(m.centroid.x - cx / w) < 0.02f)
    }
}
