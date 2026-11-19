package com.photomed.app

import com.photomed.app.align.Detection
import com.photomed.app.align.LandmarkSmoother
import com.photomed.app.align.Pt
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Suavizado temporal One-Euro de los puntos en vivo: menos temblor sin retrasar el movimiento. */
class OneEuroTest {
    private val rnd = java.util.Random(7)
    private fun noisy(x: Float) = x + (rnd.nextGaussian() * 0.003).toFloat()   // temblor del detector ≈ 0,3 % del ancho

    @Test
    fun removesJitterAndFollowsMotion() {
        val s = LandmarkSmoother()
        // 1) quieto 2 s a 15 cuadros/s
        val raw = ArrayList<Float>(); val out = ArrayList<Float>()
        var t = 0L
        repeat(30) {
            val x = noisy(0.5f); raw += x
            out += s.smooth(Detection(mapOf("p" to Pt(x, 0.5f))), t).points["p"]!!.x; t += 66
        }
        fun sd(v: List<Float>) = v.drop(5).let { l -> val m = l.average(); sqrt(l.sumOf { (it - m) * (it - m) } / l.size) }
        val sdRaw = sd(raw); val sdOut = sd(out)
        // 2) movimiento real: 10 % del ancho en 0,5 s
        var x = 0.5f; var lag = 0f
        repeat(8) { x += 0.0125f; lag = abs(s.smooth(Detection(mapOf("p" to Pt(noisy(x), 0.5f))), t).points["p"]!!.x - x); t += 66 }
        println("ONE-EURO temblor %.4f → %.4f · retraso al moverse %.4f (de 0,1)".format(sdRaw, sdOut, lag))
        assertTrue(sdOut < sdRaw * 0.6)
        assertTrue(lag < 0.02f)
    }
}
