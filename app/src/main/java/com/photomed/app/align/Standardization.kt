package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.abs

/**
 * Estandarización de la toma sin foto de referencia (primera foto de una zona), según los
 * estándares de fotografía clínica (DiBernardo et al., Plast Reconstr Surg 1998; Yavuzer et al.,
 * Ann Plast Surg 2001; guías fotográficas de la ASPS):
 * - Vista estándar: giro de la cabeza 0° (frontal), 45° (oblicuo, hacia el lado de la zona) o
 *   ≥ 70° (perfil; los detectores pierden el rostro cerca de 90°).
 * - Plano de Frankfort horizontal: cabeceo ≈ 0°; cabeza sin inclinación lateral.
 * - Iluminación uniforme: diferencia de luminancia entre mejillas ≤ 15 %; exposición del rostro
 *   correcta (L* 35–85).
 * El sentido del giro (hacia la derecha o izquierda de la imagen) se obtiene de la geometría
 * (punta nasal respecto del centro del óvalo), común a ML Kit y MediaPipe.
 */
object Standardization {
    data class Target(val yaw: Float?, val yawTol: Float, val side: Int, val minYaw: Float? = null)

    /** side: +1 la cara mira hacia la derecha de la imagen, −1 hacia la izquierda, 0 de frente. */
    fun target(zone: Zone): Target? = when (zone.guide) {
        GuideType.FACE_FRONT, GuideType.FOREHEAD, GuideType.PERIOCULAR, GuideType.LIPS -> Target(0f, 5f, 0)
        GuideType.FACE_OBLIQUE -> Target(45f, 8f, if (zone.mirrored) -1 else 1)
        GuideType.FACE_PROFILE -> Target(null, 0f, if (zone.mirrored) -1 else 1, minYaw = 70f)
        else -> null
    }

    const val PITCH_TOL = 7f
    const val ROLL_TOL = 4f
    const val LIGHT_ASYM = 0.15f

    /** Sentido del giro por geometría: punta nasal frente al centro del óvalo (normalizado por su ancho). */
    fun facingSide(points: Map<String, Pt>): Float? {
        val nose = points["nose_bottom_1"] ?: points[Landmarks.NOSE_BASE] ?: return null
        val oval = (0 until 36).mapNotNull { points["oval_$it"] }
        if (oval.size < 20) return null
        val minX = oval.minOf { it.x }; val maxX = oval.maxOf { it.x }
        val w = maxX - minX
        if (w < 1e-3f) return null
        return ((nose.x - (minX + maxX) / 2) / (w / 2))
    }

    /** Luminancia media (0..1) de las mejillas izquierda y derecha de la imagen. */
    data class Lighting(val leftCheek: Float, val rightCheek: Float, val face: Float)

    fun evaluate(zone: Zone, det: Detection, lighting: Lighting?): MatchEval {
        val t = target(zone)
        if (det.points.size < LiveDecision.MIN_POINTS) {
            val hint = if (t?.minYaw != null) "Perfil: alinee la oreja y el plano de Frankfort con la guía" else "No se detecta el rostro del paciente"
            return MatchEval(MatchStatus.SEARCHING, 0f, listOf(hint), threshold = 1f)
        }
        val hints = mutableListOf<String>()
        var off = 0f
        if (t != null) {
            val yawAbs = abs(det.yaw ?: 0f)
            val side = facingSide(det.points) ?: 0f
            val sideSign = if (side > 0.08f) 1 else if (side < -0.08f) -1 else 0
            if (t.minYaw != null) {
                if (yawAbs < t.minYaw) { hints += "Gire la cabeza hasta el perfil completo (faltan %.0f°)".format(t.minYaw - yawAbs); off += 1f }
            } else if (t.yaw == 0f) {
                if (yawAbs > t.yawTol) {
                    hints += "Gire la cabeza hacia la " + (if (sideSign > 0) "izquierda" else "derecha") + " de la imagen %.0f°".format(yawAbs)
                    off += yawAbs / t.yawTol
                }
            } else {
                val wrongSide = sideSign != 0 && sideSign != t.side
                val err = if (wrongSide) yawAbs + t.yaw!! else yawAbs - t.yaw!!
                if (wrongSide) hints += "La cara debe mirar hacia la " + (if (t.side > 0) "derecha" else "izquierda") + " de la imagen"
                else if (abs(err) > t.yawTol) hints += if (err < 0) "Gire más la cabeza (%.0f° de 45°)".format(yawAbs) else "Gire menos la cabeza (%.0f° de 45°)".format(yawAbs)
                off += abs(err) / t.yawTol
            }
            det.pitch?.let { if (abs(it) > PITCH_TOL) { hints += "Frankfort: " + (if (it > 0) "baje" else "suba") + " un poco la barbilla (%.0f°)".format(abs(it)); off += abs(it) / PITCH_TOL } }
            det.roll?.let { if (abs(it) > ROLL_TOL) { hints += "Enderece la cabeza (inclinada %.0f°)".format(abs(it)); off += abs(it) / ROLL_TOL } }
        }
        det.gaze?.let { g ->
            if (kotlin.math.abs(g.x) > Gaze.TOL_X * 1.2f || kotlin.math.abs(g.y) > Gaze.TOL_Y * 1.2f) {
                hints += "Mirada: pida que mire al frente, en la dirección de la cabeza"
                off += 0.6f
            }
        }
        lighting?.let { l ->
            val m = maxOf(l.leftCheek, l.rightCheek)
            if (m > 0f && abs(l.leftCheek - l.rightCheek) / m > LIGHT_ASYM) {
                hints += "Iluminación desigual: lado " + (if (l.leftCheek < l.rightCheek) "izquierdo" else "derecho") + " de la imagen más oscuro"
                off += 0.5f
            }
            if (l.face < 0.35f) { hints += "Rostro subexpuesto: más luz"; off += 0.5f }
            if (l.face > 0.85f) { hints += "Rostro sobreexpuesto: menos luz o sin flash"; off += 0.5f }
        }
        val status = when {
            hints.isEmpty() -> MatchStatus.MATCH
            off <= 2.5f -> MatchStatus.CLOSE
            else -> MatchStatus.FAR
        }
        return MatchEval(status, (1f - off / 5f).coerceIn(0f, 1f), hints, threshold = 1f,
            headYawDelta = det.yaw ?: 0f, headPitchDelta = det.pitch ?: 0f, gazeDelta = det.gaze)
    }

    /** Muestreo de luminancia en las mejillas (entre ojo y borde del óvalo) y en el centro del rostro. */
    fun lighting(rgb: IntArray, w: Int, h: Int, points: Map<String, Pt>): Lighting? {
        val l = points[Landmarks.EYE_L] ?: return null; val r = points[Landmarks.EYE_R] ?: return null
        val nose = points["nose_bottom_1"] ?: points[Landmarks.NOSE_BASE] ?: return null
        val ovalL = points["oval_8"]; val ovalR = points["oval_28"]
        fun lum(p: Pt, rad: Float): Float {
            val cx = (p.x * w).toInt(); val cy = (p.y * w).toInt(); val rr = (rad * w).toInt().coerceAtLeast(1)
            var s = 0f; var n = 0
            for (y in cy - rr..cy + rr) for (x in cx - rr..cx + rr) if (x in 0 until w && y in 0 until h) {
                val c = rgb[y * w + x]
                s += (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f; n++
            }
            return if (n > 0) s / n else 0f
        }
        // mejilla: a medio camino entre la pupila (bajada a la altura de la nariz) y el borde del óvalo
        fun cheek(eye: Pt, edge: Pt?): Pt = Pt(if (edge != null) (eye.x + edge.x) / 2 else eye.x, (eye.y + nose.y) / 2)
        val leftImg = if (r.x < l.x) cheek(r, ovalR) else cheek(l, ovalL)
        val rightImg = if (r.x < l.x) cheek(l, ovalL) else cheek(r, ovalR)
        return Lighting(lum(leftImg, 0.03f), lum(rightImg, 0.03f), lum(Pt((l.x + r.x) / 2, (l.y + nose.y) / 2), 0.05f))
    }
}
