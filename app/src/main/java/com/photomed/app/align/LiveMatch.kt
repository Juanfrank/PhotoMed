package com.photomed.app.align

import com.photomed.app.i18n.trn
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

enum class MatchStatus { SEARCHING, FAR, CLOSE, MATCH }

/** Evaluación de un cuadro de la cámara frente a la foto de referencia. */
data class MatchEval(
    val status: MatchStatus,
    /** 0..1: cuán cerca está del encuadre de referencia. */
    val quality: Float,
    val hints: List<String>,
    val offset: Float = 0f,
    val scaleError: Float = 0f,
    val rotationDeg: Float = 0f,
    /** Fracción de puntos de referencia que coinciden con el cuadro actual (0..1), si se midió. */
    val pointFraction: Float? = null,
    val threshold: Float = LiveMatch.DEFAULT_THRESHOLD,
    /** Diferencia de perspectiva vertical estimada por proporciones (°). */
    val keystoneDeg: Float = 0f,
    /** Desplazamiento que necesita el contenido (fracción del ancho) y escala necesaria. */
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val headYawDelta: Float = 0f,
    val headPitchDelta: Float = 0f,
    /** Diferencia de mirada actual − referencia (o mirada absoluta en estandarización). */
    val gazeDelta: Pt? = null,
    /**
     * La expresión facial pedida (sorpresa, enojo, sonrisa…) se cumple; si no, no se dispara
     * automáticamente aunque el encuadre coincida (también con «casi alineada»).
     */
    val expressionOk: Boolean = true
)

/**
 * Criterios de coincidencia en vivo.
 * - Principal: **porcentaje de puntos coincidentes** ≥ umbral (por defecto 80 %); un punto coincide
 *   si está a ≤ [POINT_TOLERANCE] (3 %) del ancho de su posición en la referencia; umbral por defecto 80 %.
 * - Indicaciones: de la semejanza [t] (actual → referencia) medida en el pivote anatómico y de la
 *   perspectiva vertical estimada por proporciones de contornos.
 */
object LiveMatch {
    const val DEFAULT_THRESHOLD = 0.80f
    const val POINT_TOLERANCE = 0.03f
    const val MATCH_OFFSET = 0.03f
    const val MATCH_SCALE = 0.04f
    const val MATCH_ROT = 3f
    const val CLOSE_FACTOR = 2.5f
    const val KEYSTONE_HINT = 1.5f

    fun evaluate(
        t: Similarity?,
        pivot: Pt,
        wasMatched: Boolean = false,
        pointFraction: Float? = null,
        threshold: Float = DEFAULT_THRESHOLD,
        keystoneDeg: Float = 0f,
        headYawDelta: Float = 0f,
        headPitchDelta: Float = 0f,
        /** Exigir también geometría y pose (no solo el % de puntos). */
        gate: Boolean = false,
    ): MatchEval {
        if (t == null && pointFraction == null) {
            return MatchEval(MatchStatus.SEARCHING, 0f, listOf(trn("Buscando referencias anatómicas…")), threshold = threshold)
        }
        val tt = t ?: Similarity.IDENTITY
        val d = tt.apply(pivot) - pivot
        val off = hypot(d.x, d.y)
        val se = abs(ln(tt.scale))
        val rd = abs(tt.rotationDegrees)
        val k = if (wasMatched) 1.3f else 1f
        val norm = maxOf(off / MATCH_OFFSET, se / MATCH_SCALE, rd / MATCH_ROT)
        val status = if (pointFraction != null) {
            // histéresis: una vez alineado, tolera 3 puntos porcentuales menos
            val thr = if (wasMatched) threshold - 0.03f else threshold
            val geomOk = !gate || (norm <= k && LiveDecision.poseOk(headYawDelta, headPitchDelta))
            when {
                pointFraction >= thr && geomOk -> MatchStatus.MATCH
                pointFraction >= threshold - 0.25f || norm <= CLOSE_FACTOR -> MatchStatus.CLOSE
                else -> MatchStatus.FAR
            }
        } else when {
            norm <= k -> MatchStatus.MATCH
            norm <= CLOSE_FACTOR -> MatchStatus.CLOSE
            else -> MatchStatus.FAR
        }
        val hints = mutableListOf<String>()
        if (status != MatchStatus.MATCH && t != null) {
            if (abs(d.x) > MATCH_OFFSET * 0.7f) hints += if (d.x > 0) trn("← Mueva la cámara a la izquierda") else trn("Mueva la cámara a la derecha →")
            if (abs(d.y) > MATCH_OFFSET * 0.7f) hints += if (d.y > 0) trn("↑ Suba la cámara") else trn("↓ Baje la cámara")
            if (se > MATCH_SCALE * 0.7f) hints += if (tt.scale > 1f) trn("Acérquese") else trn("Aléjese")
            if (rd > MATCH_ROT * 0.7f) hints += if (tt.rotationDegrees > 0) trn("Gire la cámara ↺") else trn("Gire la cámara ↻")
        }
        if (status != MatchStatus.MATCH && abs(headYawDelta) > LiveDecision.POSE_MAX)
            hints += "Cabeza: corregir giro %.0f°".format(headYawDelta)
        if (status != MatchStatus.MATCH && abs(headPitchDelta) > LiveDecision.POSE_MAX)
            hints += (if (headPitchDelta > 0) "Barbilla: bajar %.0f°" else "Barbilla: subir %.0f°").format(abs(headPitchDelta))
        // keystoneDeg > 0: el cuadro actual necesita "nivelar" una cámara elevada → inclinarla hacia abajo
        if (status != MatchStatus.MATCH && abs(keystoneDeg) >= KEYSTONE_HINT) {
            hints += if (keystoneDeg > 0) trn("Cámara: inclinar hacia abajo") else trn("Cámara: inclinar hacia arriba")
        }
        val quality = pointFraction ?: (1f - (norm - 1f) / (CLOSE_FACTOR * 2f)).coerceIn(0f, 1f).let { if (norm <= 1f) 1f else it }
        return MatchEval(status, quality, hints, off, se, tt.rotationDegrees, pointFraction, threshold, keystoneDeg,
            d.x, d.y, tt.scale, headYawDelta, headPitchDelta)
    }

    /** Fracción de pares (referencia, actual) a ≤ [tol] de distancia (posiciones sin transformar). */
    /** Tolerancia de los puntos de contorno (bordes de pies, miembros y tronco): se mueven más que los anatómicos. */
    const val CONTOUR_TOLERANCE = 0.05f

    /** Como [pointFraction], con la tolerancia de cada punto según su clave ([BodyPoints.isContour]). */
    fun pointFraction(keys: List<String>, ref: List<Pt>, cur: List<Pt>): Float {
        if (ref.isEmpty()) return 0f
        return ref.indices.count { val tol = if (BodyPoints.isContour(keys[it])) CONTOUR_TOLERANCE else POINT_TOLERANCE
            hypot(ref[it].x - cur[it].x, ref[it].y - cur[it].y) <= tol } / ref.size.toFloat()
    }

    fun pointFraction(ref: List<Pt>, cur: List<Pt>, tol: Float = POINT_TOLERANCE): Float {
        if (ref.isEmpty()) return 0f
        return ref.indices.count { hypot(ref[it].x - cur[it].x, ref[it].y - cur[it].y) <= tol } / ref.size.toFloat()
    }
}
