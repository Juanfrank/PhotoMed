package com.photomed.app

import com.photomed.app.align.ZoomController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Planta simulada: el paciente está a una distancia que requiere zoom `needed`; la cámara aplica el
 * zoom con 2 cuadros de retraso y la medida tiene ruido (detector). Se comprueba convergencia sin
 * oscilación.
 */
class ZoomControllerTest {
    private fun simulate(needed: Float, noise: Float, seed: Int): Pair<Float, Int> {
        val rnd = Random(seed)
        val zc = ZoomController(1f, 4f)
        var zoom = 1f
        val pending = ArrayDeque<Float>()
        var applied = 1f
        var reversals = 0; var lastDir = 0
        var t = 0L
        repeat(150) {
            t += 200 // ~5 cuadros/s
            if (pending.isNotEmpty()) applied = pending.removeFirst()
            val measured = needed / applied * (1f + rnd.nextFloat() * 2 * noise - noise)
            zc.update(measured, zoom, t)?.let { z ->
                val dir = if (z > zoom) 1 else -1
                if (lastDir != 0 && dir != lastDir) reversals++
                lastDir = dir; zoom = z
                pending.addLast(z); pending.addLast(z) // retraso de aplicación
            }
        }
        return zoom to reversals
    }

    @Test
    fun convergesWithoutOscillation() {
        for ((needed, seed) in listOf(1.6f to 1, 2.3f to 2, 1.15f to 3, 3.2f to 4)) {
            val (zoom, reversals) = simulate(needed, noise = 0.015f, seed = seed)
            assertEquals("zoom final para $needed", needed, zoom, needed * 0.04f)
            assertTrue("inversiones de sentido: $reversals", reversals <= 1)
        }
    }

    @Test
    fun ignoresNoiseInsideDeadband() {
        val zc = ZoomController()
        var changes = 0
        var t = 0L
        repeat(100) { i -> t += 200; if (zc.update(if (i % 2 == 0) 1.015f else 0.985f, 1.5f, t) != null) changes++ }
        assertEquals(0, changes)
    }

    @Test
    fun waitsForFocusAndSettling() {
        val zc = ZoomController()
        zc.focusing = true
        repeat(10) { assertEquals(null, zc.update(1.5f, 1f, it * 200L)) }
        zc.focusing = false
        var t = 2000L; var z: Float? = null
        while (z == null) { t += 200; z = zc.update(1.5f, 1f, t) }
        // durante el asentamiento no vuelve a cambiar
        for (k in 1..4) assertEquals(null, zc.update(1.5f, z, t + k * 200L))
    }
}
