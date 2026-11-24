package com.photomed.app.ui.camera

import androidx.compose.ui.geometry.Offset
import com.photomed.app.align.FaceFraming
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Toma capilar frontal con la cabeza inclinada: la guía (proyectada del modelo 3D) se encaja en la cara
 * detectada por la malla facial con una semejanza (escala, giro y traslación) que lleva los centros de
 * los ojos del modelo ([ScalpGuideArt.FRONTAL_TILT_EYES]) a los detectados. Solo los ojos: el resto
 * (frente, línea de implantación, raya) sigue la proyección del modelo a 30°, así que si el paciente no
 * inclina la cabeza lo bastante la línea de implantación de la guía no coincide con la suya.
 *
 * Coordenadas: la guía en fracciones del ancho y del alto del cuadro 3:4; los puntos detectados en x del
 * ancho e y en unidades del ancho (y × [ASPECT] → fracción del alto).
 */
object ScalpFaceFit {
    /** Ancho / alto del cuadro (3:4 vertical). */
    const val ASPECT = 0.75f

    class Similarity(private val s: Float, private val c: Float, private val sn: Float,
                     private val a: Offset, private val b: Offset) {
        /** Punto de la guía → punto encajado (las dos en fracciones del ancho y del alto). */
        fun map(p: Offset): Offset {
            // a unidades del ancho para girar sin deformar
            val x = p.x - a.x; val y = (p.y - a.y) / ASPECT
            val rx = s * (c * x - sn * y); val ry = s * (sn * x + c * y)
            return Offset(b.x + rx, b.y + ry * ASPECT)
        }
    }

    private fun eyes(points: Map<String, Pt>): Pair<Offset, Offset>? {
        val r = points[Landmarks.EYE_R] ?: return null; val l = points[Landmarks.EYE_L] ?: return null
        return Offset(r.x, r.y * ASPECT) to Offset(l.x, l.y * ASPECT)
    }

    /** Ángulo de la recta entre ojos (radianes, en unidades del ancho) sin el sentido: en (−π/2, π/2]. */
    private fun axis(p: Offset, q: Offset): Float {
        var t = atan2((q.y - p.y) / ASPECT, q.x - p.x)
        while (t > PI / 2) t -= PI.toFloat()
        while (t <= -PI / 2) t += PI.toFloat()
        return t
    }

    private fun dist(p: Offset, q: Offset) = hypot(q.x - p.x, (q.y - p.y) / ASPECT)

    /** Semejanza modelo → cara detectada; null sin los dos ojos o con ojos casi superpuestos. */
    fun of(points: Map<String, Pt>, model: List<Offset> = ScalpGuideArt.FRONTAL_TILT_EYES): Similarity? {
        val (r, l) = eyes(points) ?: return null
        val dm = dist(model[0], model[1]); val dd = dist(r, l)
        if (dm < 1e-4f || dd < 0.02f) return null
        val t = axis(r, l) - axis(model[0], model[1])
        val a = Offset((model[0].x + model[1].x) / 2, (model[0].y + model[1].y) / 2)
        val b = Offset((r.x + l.x) / 2, (r.y + l.y) / 2)
        return Similarity(dd / dm, cos(t), sin(t), a, b)
    }

    /** Encuadre de la cara respecto de la guía (centro de los ojos y su separación), como [FaceFraming.of]. */
    fun framing(points: Map<String, Pt>, model: List<Offset> = ScalpGuideArt.FRONTAL_TILT_EYES): FaceFraming.Fit? {
        val (r, l) = eyes(points) ?: return null
        val dm = dist(model[0], model[1]); if (dm < 1e-4f) return null
        return FaceFraming.Fit((r.x + l.x - model[0].x - model[1].x) / 2, (r.y + l.y - model[0].y - model[1].y) / 2, dist(r, l) / dm)
    }
}
