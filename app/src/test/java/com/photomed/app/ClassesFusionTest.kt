package com.photomed.app

import com.photomed.app.align.ClassesFusion
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.ZoneCriteria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fusión de máscaras: estabiliza con la escena quieta y no va por detrás cuando se mueve. */
class ClassesFusionTest {
    private val W = 60; private val H = 80

    /** Un «pie»: rectángulo de piel en [x0, x0 + 20) × [20, 70); [holeAt]: un píxel de fondo dentro (parpadeo). */
    private fun foot(x0: Int, holeAt: Int? = null) = ZoneCriteria.Classes(W, H, ByteArray(W * H) { i ->
        val x = i % W; val y = i / W
        if (x in x0 until x0 + 20 && y in 20 until 70 && i != holeAt) FaceLabels.BODY_SKIN.toByte() else FaceLabels.BG.toByte()
    })

    @Test
    fun stillSceneIsStabilized() {
        val f = ClassesFusion()
        f.add(foot(20)); f.add(foot(20))
        // un parpadeo del modelo (un hueco en el pie) no llega a la máscara fusionada
        val hole = 40 * W + 30
        val out = f.add(foot(20, holeAt = hole))
        assertEquals(FaceLabels.BODY_SKIN, out.data[hole].toInt())
    }

    @Test
    fun movingSceneIsNotDelayed() {
        val f = ClassesFusion()
        f.add(foot(10)); f.add(foot(10))
        // la cámara se movió: el pie está ahora 12 px a la derecha; la fusión devuelve la máscara nueva tal cual
        val moved = foot(22)
        val out = f.add(moved)
        assertTrue(out.data.contentEquals(moved.data))
        assertTrue(f.skinOverlap(foot(10), moved) < 0.7f)
    }
}
