package com.photomed.app.align

import com.photomed.app.i18n.trn
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Primera foto (sin referencia) en zonas donde no hay puntos que comprobar: cuero cabelludo, ojo y
 * modo libre (sin detector), las zonas del cuerpo (el detector de cuerpo localiza a la persona por
 * la cara: de espaldas o en primer plano no la ve, y la pose no dice nada del encuadre) y los
 * primeros planos del rostro cuando no se encuentra la cara. Se dispara cuando el encuadre queda quieto
 * [holdMs] y hay imagen con detalle (no una pared lisa o la cámara tapada): quien encuadra con la
 * guía decide el encuadre y la app elige el instante sin movimiento.
 *
 * Movimiento entre cuadros: diferencia media de luminancia relativa al gradiente medio (≈ píxeles
 * de desplazamiento a [width] px de ancho), independiente del contraste de la escena.
 */
class SteadyFraming(private val holdMs: Long = 700) {
    private var prev: GrayImage? = null
    private var since = 0L

    fun reset() { prev = null; since = 0L }

    /** [g]: luminancia 0..1 del cuadro (ver [ImageOps.luma]); [now] en ms. */
    fun update(g: GrayImage, now: Long): MatchEval {
        val p = prev; prev = g
        val tex = texture(g)
        if (tex.first < MIN_STD || tex.second < MIN_GRAD) {
            since = 0L
            return MatchEval(MatchStatus.SEARCHING, 0f, listOf(HINT_FRAME), threshold = 1f)
        }
        val shiftPx = if (p == null || p.width != g.width || p.height != g.height) Float.MAX_VALUE else meanAbsDiff(p, g) / tex.second
        if (shiftPx > MAX_SHIFT_PX) {
            since = 0L
            return MatchEval(MatchStatus.CLOSE, 0.3f, listOf(HINT_STILL), threshold = 1f)
        }
        if (since == 0L) since = now
        val held = now - since
        // por debajo de 0,8 hasta cumplir el tiempo (el «casi alineada» no debe adelantarse)
        return if (held >= holdMs) MatchEval(MatchStatus.MATCH, 1f, emptyList(), threshold = 1f)
            else MatchEval(MatchStatus.CLOSE, 0.4f + 0.35f * held / holdMs, listOf(HINT_STILL), threshold = 1f)
    }

    companion object {
        val HINT_STILL = trn("Mantenga el teléfono quieto")
        val HINT_FRAME = trn("Encuadre la zona dentro de la guía")
        /** Desviación típica mínima de la luminancia y gradiente medio mínimo (imagen con detalle). */
        const val MIN_STD = 0.04f
        const val MIN_GRAD = 0.008f
        /** Desplazamiento máximo entre cuadros para considerarlo quieto (px a 96 px de ancho ≈ 0,7 %). */
        const val MAX_SHIFT_PX = 0.7f

        /** Desviación típica de la luminancia y gradiente medio (diferencias entre vecinos). */
        fun texture(g: GrayImage): Pair<Float, Float> {
            val d = g.data; val n = d.size
            if (n == 0) return 0f to 0f
            var s = 0.0; var s2 = 0.0
            for (v in d) { s += v; s2 += v * v }
            val mean = s / n
            var grad = 0.0; var m = 0
            for (y in 0 until g.height - 1) for (x in 0 until g.width - 1) {
                val i = y * g.width + x
                grad += abs(d[i + 1] - d[i]) + abs(d[i + g.width] - d[i]); m += 2
            }
            return sqrt((s2 / n - mean * mean).coerceAtLeast(0.0)).toFloat() to (if (m > 0) (grad / m).toFloat() else 0f)
        }

        fun meanAbsDiff(a: GrayImage, b: GrayImage): Float {
            var s = 0.0
            for (i in a.data.indices) s += abs(a.data[i] - b.data[i])
            return (s / a.data.size).toFloat()
        }

        /** Primeros planos del rostro: sin cara detectada se encuadran con la guía. */
        private val CLOSE_UPS = setOf(com.photomed.app.data.GuideType.FOREHEAD, com.photomed.app.data.GuideType.PERIOCULAR,
            com.photomed.app.data.GuideType.LIPS)

        /** ¿La primera foto de [zone] se decide por la quietud del encuadre (ver la clase)? */
        fun applies(zone: com.photomed.app.data.Zone, det: Detection): Boolean {
            val spec = ZoneAlignment.of(zone)
            return when (spec.detector) {
                // sin detector y en el cuerpo, siempre: la pose no dice nada del encuadre (y el detector
                // de cuerpo a veces «ve» una persona diminuta en una mano o un pie)
                Detector.NONE, Detector.POSE -> true
                Detector.FACE -> det.points.size < LiveDecision.MIN_POINTS && zone.guide in CLOSE_UPS
            }
        }
    }
}
