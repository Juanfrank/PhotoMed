package com.photomed.app

import com.photomed.app.align.Landmarks
import com.photomed.app.align.LiveMatch
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Perspective
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import com.photomed.app.align.landmarkTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PerspectiveAndLiveTest {
    private val w = 900.0
    private val h = 1200.0
    private val f = 0.75 * h

    /** Proyección de un punto 3D (cámara nivelada: x der., y abajo, z adelante) con la cámara elevada φ. */
    private fun projectTilted(X: Double, Y: Double, Z: Double, phiDeg: Double): Pair<Double, Double> {
        val p = Math.toRadians(phiDeg)
        // d_tilt = Rᵀ·d_level, con R(0,0,1) = (0, −sin φ, cos φ)
        val y = cos(p) * Y + sin(p) * Z
        val z = -sin(p) * Y + cos(p) * Z
        return Pair(w / 2 + f * X / z, h / 2 + f * y / z)
    }

    @Test
    fun levelingRemovesKeystoneExactly() {
        for (phi in listOf(-12.0, 8.0)) {
            for ((X, Y) in listOf(-0.4 to -0.6, 0.4 to -0.6, -0.4 to 0.7, 0.4 to 0.7, 0.0 to 0.0)) {
                val (u, v) = projectTilted(X, Y, 2.0, phi)
                val q = Perspective.level(u, v, w, h, phi, 0.75)
                assertEquals(w / 2 + f * X / 2.0, q.x, 1e-6)
                assertEquals(h / 2 + f * Y / 2.0, q.y, 1e-6)
            }
        }
    }

    @Test
    fun correctedFrameFillsOutputAndKeepsVerticals() {
        val dst = Perspective.cornerTargets(w, h, 10.0, 0.75)
        // El recorte 3:4 queda dentro del trapecio: ninguna esquina destino invade el encuadre.
        assertTrue(dst[0].x <= 0.5 && dst[0].y <= 0.5)
        assertTrue(dst[2].x >= w - 0.5 && dst[2].y >= h - 0.5)
        // Una vertical del mundo, inclinada en la foto, queda vertical tras corregir.
        val (u1, v1) = projectTilted(0.3, -0.5, 2.0, 10.0)
        val (u2, v2) = projectTilted(0.3, 0.6, 2.0, 10.0)
        assertTrue("antes debía converger", kotlin.math.abs(u1 - u2) > 5)
        val a = Perspective.level(u1, v1, w, h, 10.0, 0.75)
        val b = Perspective.level(u2, v2, w, h, 10.0, 0.75)
        assertEquals(a.x, b.x, 1e-6)
        assertEquals(listOf(0.0, w, w, 0.0), Perspective.cornerTargets(w, h, 0.0, 0.75).map { it.x })
    }

    @Test
    fun liveMatchStatesAndHints() {
        val pivot = Pt(0.5f, 0.66f)
        assertEquals(MatchStatus.SEARCHING, LiveMatch.evaluate(null, pivot).status)
        assertEquals(MatchStatus.MATCH, LiveMatch.evaluate(Similarity(1.01f, 0.01f, 0.005f, 0f), pivot).status)

        val right = LiveMatch.evaluate(Similarity(tx = 0.08f), pivot)
        assertEquals(MatchStatus.FAR, right.status)
        assertTrue(right.hints.any { "izquierda" in it })

        val closer = LiveMatch.evaluate(Similarity(scale = 1.05f, tx = 0.5f * (1 - 1.05f), ty = 0.66f * (1 - 1.05f)), pivot)
        assertEquals(MatchStatus.CLOSE, closer.status)
        assertEquals(listOf("Acérquese"), closer.hints)

        // histéresis: un error un poco mayor que el umbral mantiene la coincidencia si ya coincidía
        val edge = Similarity(tx = 0.023f)
        assertEquals(MatchStatus.CLOSE, LiveMatch.evaluate(edge, pivot, wasMatched = false).status)
        assertEquals(MatchStatus.MATCH, LiveMatch.evaluate(edge, pivot, wasMatched = true).status)
    }

    @Test
    fun manyLandmarksWithSeveralOutliers() {
        val truth = Similarity(1.04f, 0.03f, -0.02f, 0.01f)
        val after = (0 until 36).associate { i ->
            val a = i * 2 * Math.PI / 36
            "oval_$i" to Pt((0.5 + 0.3 * sin(a)).toFloat(), (0.7 + 0.4 * cos(a)).toFloat())
        }
        val before = after.mapValues { truth.apply(it.value) }.toMutableMap()
        before["oval_3"] = Pt(0.1f, 0.1f); before["oval_17"] = Pt(0.9f, 1.2f); before["oval_25"] = Pt(0.0f, 0.5f)
        val (t, keys) = landmarkTransform(Landmarks.FACE_ALL, before, after)!!
        assertTrue(keys.none { it in setOf("oval_3", "oval_17", "oval_25") })
        assertEquals(truth.scale, t.scale, 1e-3f)
        assertEquals(truth.rotation, t.rotation, 1e-3f)
        assertEquals("mandíbula", Landmarks.regionOf("oval_18"))
        assertEquals("frente", Landmarks.regionOf("oval_2"))
    }
}
