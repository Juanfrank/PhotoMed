package com.photomed.app

import android.graphics.PointF
import com.photomed.app.align.FeatureMetrics
import com.photomed.app.align.InkRenderer
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ángulo interno de la línea multipunto y proporciones faciales del contorno. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProportionsAndAnglesTest {
    @Test
    fun internalAngle() {
        fun a(ax: Float, ay: Float, vx: Float, vy: Float, bx: Float, by: Float) = InkRenderer.angleAt(PointF(ax, ay), PointF(vx, vy), PointF(bx, by))!!
        assertEquals(90f, a(0f, 0f, 10f, 0f, 10f, 10f), 0.01f)
        assertEquals(180f, a(0f, 0f, 10f, 0f, 20f, 0f), 0.01f)
        assertEquals(45f, a(10f, 10f, 0f, 0f, 10f, 0f), 0.01f)
        assertEquals(120f, a(-10f, 0f, 0f, 0f, 5f, -8.660254f), 0.05f)
    }

    @Test
    fun facialProportionsOnRealMeshes() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin mallas de retratos", json != null)
        val o = JSONObject(json!!)
        var frontals = 0
        for (k in o.keys()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            // línea del pelo sintética: 0,6 interpupilares sobre las cejas
            val m = FeatureMetrics.contourM(mesh, 300f, "iris").metrics
            val f = m.features!!.toMap()
            val idx = f[FeatureMetrics.FACIAL_INDEX]!!
            assertTrue("$k índice facial $idx", idx in 60f..120f)
            val sub = f["Estomion → mentón / subnasal → estomion (ideal 2)"]
            assertTrue("$k tercio inferior $sub", sub == null || sub in 1f..3.5f)
            val fifths = f.filterKeys { it.startsWith("Quinto") }
            if (fifths.isNotEmpty()) {
                frontals++
                assertEquals(5, fifths.size)
                assertEquals("$k quintos", 100f, fifths.values.sum(), 0.5f)
                fifths.values.forEach { assertTrue("$k quinto $it", it in 8f..35f) }
                val bg = f["Bigonial / bicigomático, %"]!!
                assertTrue("$k bigonial $bg", bg in 55f..100f)
            }
            println("PROPORCIONES $k: índice %.1f (%s), inferior %.2f, quintos %s".format(idx, FeatureMetrics.facialType(idx), sub ?: 0f,
                fifths.values.joinToString(" / ") { "%.0f".format(it) }))
        }
        assertTrue("ninguna cara de frente", frontals > 0)
        // con la línea del pelo: tres tercios que suman 100 %
        val k0 = o.keys().next(); val arr = o.getJSONArray(k0)
        val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val hair = (0..10).map { Pt(mesh[234].x + (mesh[454].x - mesh[234].x) * it / 10f, mesh[10].y - 0.02f) }
        val f = FeatureMetrics.contourM(mesh, 300f, "iris", hair).metrics.features!!.toMap()
        val thirds = listOf("Tercio superior (ideal 33 %), %", "Tercio medio (ideal 33 %), %", "Tercio inferior (ideal 33 %), %").map { f[it]!! }
        assertEquals(100f, thirds.sum(), 0.5f)
    }
}
