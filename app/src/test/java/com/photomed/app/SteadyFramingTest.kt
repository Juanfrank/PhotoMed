package com.photomed.app

import com.photomed.app.align.GrayImage
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.SteadyFraming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Quietud del encuadre para la primera foto sin puntos (zonas del cuerpo, cuero cabelludo, ojo): con el
 * análisis lento de las zonas del cuerpo (≈ 300 ms entre cuadros) el temblor normal de la mano no debe
 * impedir la foto; un cuadro suelto con temblor no reinicia la cuenta; moverse de verdad sí la impide.
 */
class SteadyFramingTest {
    /** Textura desplazada [dx] px (a 96 px de ancho). */
    private fun frame(dx: Float) = GrayImage(96, 128, FloatArray(96 * 128) { i ->
        val x = i % 96 + dx; val y = i / 96
        0.5f + 0.25f * sin(x * 0.35f) * sin(y * 0.27f) + 0.1f * sin((x + y) * 0.9f)
    })

    /** Tiempo hasta disparar (ms) o null; cuadros cada [dt] ms con desplazamiento [shift](k). */
    private fun firesAfter(dt: Long, maxMs: Long = 4000, shift: (Int) -> Float): Long? {
        val sf = SteadyFraming()
        var k = 0
        while (k * dt <= maxMs) {
            if (sf.update(frame(shift(k)), k * dt).status == MatchStatus.MATCH) return k * dt
            k++
        }
        return null
    }

    @Test
    fun slowFramesWithHandTremorStillFire() {
        // temblor: ±0,5 px entre cuadros a 300 ms (≈ 0,17 px cada 100 ms); antes se exigía < 0,7 px entre
        // cuadros sin contar el tiempo, y a 300 ms de separación el temblor acumulado lo superaba
        val t = firesAfter(300) { k -> if (k % 2 == 0) 0.5f else 0f }
        assertTrue("dispara con temblor y cuadros lentos: $t", t != null && t <= 1200)
        val t2 = firesAfter(300) { k -> if (k % 2 == 0) 1.6f else 0f }
        assertTrue("1,6 px cada 300 ms (≈ 0,5 px/100 ms) es quieto: $t2", t2 != null && t2 <= 1200)
    }

    @Test
    fun oneShakyFrameDoesNotRestart() {
        // quieto, un cuadro con un golpe de temblor a los 300 ms, y quieto otra vez: dispara igual a los ~0,5 s
        val t = firesAfter(100) { k -> if (k == 3) 4f else 0f }
        assertTrue("un temblor suelto no reinicia: $t", t != null && t <= 700)
    }

    @Test
    fun movingPhoneNeverFires() {
        // moviéndose: 3 px cada 100 ms, sin parar
        assertEquals(null, firesAfter(100) { k -> k * 3f })
        assertEquals(null, firesAfter(300) { k -> k * 6f })
        assertNotEquals(null, firesAfter(100) { 0f })
    }
}
