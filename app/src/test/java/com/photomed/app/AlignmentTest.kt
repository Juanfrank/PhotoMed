package com.photomed.app

import com.photomed.app.align.ContourRegion
import com.photomed.app.align.EdgeAligner
import com.photomed.app.align.GrayImage
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import com.photomed.app.align.landmarkTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class AlignmentTest {

    private fun assertPt(expected: Pt, actual: Pt, eps: Float) {
        assertTrue("esperado $expected, obtenido $actual", hypot(expected.x - actual.x, expected.y - actual.y) <= eps)
    }

    @Test
    fun twoPointSimilarityIsExact() {
        val truth = Similarity(1.2f, Math.toRadians(8.0).toFloat(), 0.05f, -0.03f)
        val src = listOf(Pt(0.38f, 0.56f), Pt(0.62f, 0.56f)) // pupilas
        val dst = src.map(truth::apply)
        val est = Similarity.estimate(src, dst)!!
        assertEquals(truth.scale, est.scale, 1e-4f)
        assertEquals(truth.rotation, est.rotation, 1e-4f)
        assertPt(dst[0], est.apply(src[0]), 1e-5f)
        assertPt(src[1], est.inverse().apply(dst[1]), 1e-5f)
    }

    @Test
    fun composeAndInverseAreConsistent() {
        val a = Similarity(0.9f, 0.1f, 0.02f, 0.04f)
        val b = Similarity(1.1f, -0.3f, -0.05f, 0.01f)
        val p = Pt(0.3f, 0.7f)
        assertPt(a.apply(b.apply(p)), a.compose(b).apply(p), 1e-5f)
        assertPt(p, a.compose(a.inverse()).apply(p), 1e-5f)
    }

    @Test
    fun landmarkTransformRejectsOutlier() {
        val truth = Similarity(0.95f, 0.05f, 0.03f, 0.02f)
        val after = mapOf(
            Landmarks.EYE_L to Pt(0.62f, 0.55f), Landmarks.EYE_R to Pt(0.38f, 0.55f),
            Landmarks.NOSE_BASE to Pt(0.5f, 0.72f), Landmarks.MOUTH_L to Pt(0.58f, 0.85f),
            Landmarks.MOUTH_R to Pt(0.42f, 0.85f),
        )
        val before = after.mapValues { truth.apply(it.value) }.toMutableMap()
        before[Landmarks.NOSE_BASE] = Pt(0.7f, 0.9f) // detección errónea
        val (t, keys) = landmarkTransform(after.keys, before, after)!!
        assertTrue(Landmarks.NOSE_BASE !in keys)
        assertEquals(truth.scale, t.scale, 1e-3f)
        assertEquals(truth.tx, t.tx, 1e-3f)
    }

    /** Imagen sintética: torso/rostro elíptico con rasgos internos, sobre fondo con gradiente. */
    private fun synthetic(w: Int, h: Int, t: Similarity): GrayImage {
        val img = GrayImage(w, h)
        val inv = t.inverse()
        for (y in 0 until h) for (x in 0 until w) {
            val p = inv.apply(Pt(x.toFloat() / w, y.toFloat() / w)) // coordenadas de la "escena"
            var v = 0.2f + 0.1f * p.y
            val ex = (p.x - 0.5f) / 0.27f
            val ey = (p.y - 0.62f) / 0.4f
            if (ex * ex + ey * ey < 1f) v = 0.75f
            for (cx in floatArrayOf(0.4f, 0.6f)) if (hypot(p.x - cx, p.y - 0.52f) < 0.035f) v = 0.1f
            if (abs(p.x - 0.5f) < 0.08f && abs(p.y - 0.82f) < 0.015f) v = 0.3f
            if (abs(p.x - 0.5f) < 0.012f && p.y in 0.55f..0.72f) v = 0.55f
            img.data[y * w + x] = v
        }
        return img.standardized()
    }

    @Test
    fun edgeMatchingRecoversTransformWithoutLandmarks() {
        val truth = Similarity(1.08f, Math.toRadians(4.0).toFloat(), -0.07f, 0.02f)
        val before = synthetic(192, 256, Similarity.IDENTITY)
        val after = synthetic(192, 256, truth.inverse()) // "después" desplazado/rotado/escalado
        val result = EdgeAligner().align(before, after, init = null, window = EdgeAligner.Window.WIDE)
        // T(después → antes) debe aproximar `truth`.
        for (p in listOf(Pt(0.4f, 0.52f), Pt(0.6f, 0.52f), Pt(0.5f, 0.82f), Pt(0.3f, 0.7f))) {
            assertPt(truth.apply(p), result.transform.apply(p), 0.012f)
        }
        assertTrue("score ${result.score}", EdgeAligner.isConfident(result.score))
    }

    @Test
    fun edgeMatchingRefinesNoisyLandmarkEstimate() {
        val truth = Similarity(0.93f, Math.toRadians(-6.0).toFloat(), 0.06f, 0.05f)
        val before = synthetic(192, 256, Similarity.IDENTITY)
        val after = synthetic(192, 256, truth.inverse())
        val noisy = Similarity(truth.scale * 1.04f, truth.rotation + 0.04f, truth.tx + 0.02f, truth.ty - 0.015f)
        val result = EdgeAligner().align(before, after, init = noisy, window = EdgeAligner.Window.NARROW)
        val probe = listOf(Pt(0.4f, 0.52f), Pt(0.6f, 0.52f), Pt(0.5f, 0.82f))
        val errBefore = probe.maxOf { val d = noisy.apply(it) - truth.apply(it); hypot(d.x, d.y) }
        val errAfter = probe.maxOf { val d = result.transform.apply(it) - truth.apply(it); hypot(d.x, d.y) }
        assertNotNull(result)
        assertTrue("error inicial $errBefore, final $errAfter", errAfter < errBefore / 3 && errAfter < 0.01f)
    }
}

class ContourRegionAlignmentTest {
    @Test
    fun anatomicalRegionsGuideMatchingAndReportScores() {
        val t = AlignmentTest()
        val truth = Similarity(0.95f, Math.toRadians(-3.0).toFloat(), 0.04f, -0.03f)
        val m = AlignmentTest::class.java.getDeclaredMethod("synthetic", Int::class.java, Int::class.java, Similarity::class.java)
        m.isAccessible = true
        val before = m.invoke(t, 192, 256, Similarity.IDENTITY) as GrayImage
        val after = m.invoke(t, 192, 256, truth.inverse()) as GrayImage
        val regions = listOf(
            ContourRegion("mandíbula", Pt(0.5f, 1.0f), Pt(0.3f, 0.1f)),
            ContourRegion("ojos", Pt(0.5f, 0.52f), Pt(0.2f, 0.06f)),
        )
        val r = EdgeAligner(regions).align(before, after, null, EdgeAligner.Window.WIDE)
        for (p in listOf(Pt(0.4f, 0.52f), Pt(0.6f, 0.52f), Pt(0.5f, 0.82f))) {
            val d = r.transform.apply(p) - truth.apply(p)
            assertTrue("error ${hypot(d.x, d.y)}", hypot(d.x, d.y) < 0.012f)
        }
        assertEquals(listOf("mandíbula", "ojos"), r.regionScores.map { it.first })
        assertTrue(r.regionScores.all { it.second > 0.5f })
    }
}
