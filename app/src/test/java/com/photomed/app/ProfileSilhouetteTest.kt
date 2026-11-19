package com.photomed.app

import com.photomed.app.align.Mask
import com.photomed.app.align.Pt
import com.photomed.app.align.Silhouette
import com.photomed.app.align.Similarity
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Perfil cerca de 90°: puntos desde la silueta y coincidencia entre dos tomas. */
class ProfileSilhouetteTest {
    /** Cabeza de perfil mirando a la derecha: cráneo (círculo), nariz (triángulo), mentón y cuello. */
    private fun profile(dx: Float, dy: Float, s: Float, w: Int = 300, h: Int = 400): Mask {
        val m = Mask(w, h)
        fun inside(x: Float, y: Float): Boolean {
            val u = (x - dx) / s; val v = (y - dy) / s
            val skull = hypot(u - 0.45f, v - 0.42f) < 0.24f
            val face = u in 0.45f..0.66f && v in 0.40f..0.86f && u < 0.66f - 0.15f * ((v - 0.40f).coerceAtLeast(0f))
            val nose = v in 0.52f..0.66f && u < 0.66f + 0.08f * (1f - kotlin.math.abs(v - 0.62f) / 0.10f).coerceAtLeast(0f) && u > 0.5f
            val chin = hypot(u - 0.56f, v - 0.84f) < 0.06f
            val neck = u in 0.36f..0.55f && v in 0.80f..1.10f
            return skull || face || nose || chin || neck
        }
        for (y in 0 until h) for (x in 0 until w) m.data[y * w + x] = inside(x / w.toFloat(), y / w.toFloat() * 0.75f * 4f / 3f)
        return m
    }

    @Test
    fun silhouettePointsTrackTheHead() {
        val a = Silhouette.fromMask(profile(0f, 0f, 1f))!!.profilePoints(frontRight = true)
        val b = Silhouette.fromMask(profile(0.04f, 0.03f, 0.92f))!!.profilePoints(frontRight = true)
        println("PERFIL puntos: ${a.keys}")
        assertTrue(a.keys.containsAll(listOf("vértice", "punta nasal", "occipucio")))
        val keys = a.keys.intersect(b.keys).toList()
        val t = Similarity.estimate(keys.map { b[it]!! }, keys.map { a[it]!! })!!
        // la semejanza recupera la escala (1/0,92) y lleva cada punto a su sitio
        val err = keys.maxOf { k -> val q = t.apply(b[k]!!); hypot(q.x - a[k]!!.x, q.y - a[k]!!.y) }
        println("PERFIL escala %.3f · error máx. %.3f".format(t.scale, err))
        assertTrue(t.scale in 1.03f..1.15f)
        assertTrue(err < 0.03f)
    }
}
