package com.photomed.app.align

import com.photomed.app.i18n.trn
import kotlin.math.abs

/**
 * Decisión de coincidencia en vivo a partir de puntos anatómicos (lógica pura, probada con
 * fotos reales: ver `FaceMatchingRealImagesTest`).
 *
 * Coincide si, a la vez:
 * 1. % de puntos a ≤ 3 % del ancho de su posición en la referencia ≥ umbral (80 % por defecto);
 * 2. geometría: desplazamiento del pivote ≤ 3 %, escala ≤ 4 %, giro ≤ 3°;
 * 3. pose de la cabeza (rostro): |Δ guiñada| y |Δ cabeceo| ≤ 6°.
 * Si la zona usa un detector y la referencia tiene puntos, un cuadro sin puntos nunca coincide
 * (evita falsos positivos como apuntar al piso).
 */
object LiveDecision {
    const val POSE_MAX = 6f
    const val MIN_POINTS = 4

    fun evaluate(spec: ZoneAlignment, ref: Detection, cur: Detection, threshold: Float, wasMatched: Boolean,
                 useGaze: Boolean = true, useExpression: Boolean = true,
                 /** Expresión pedida: con referencia también debe cumplirse (no basta con parecerse). */
                 expression: com.photomed.app.data.FacialExpression = com.photomed.app.data.FacialExpression.NEUTRAL): MatchEval {
        val keys = spec.landmarks.filter { it in ref.points && it in cur.points }
        if (keys.size < MIN_POINTS) {
            val hint = if (spec.detector == Detector.FACE) trn("No se detecta el rostro del paciente") else trn("No se detecta el cuerpo del paciente")
            return MatchEval(MatchStatus.SEARCHING, 0f, listOf(hint), threshold = threshold)
        }
        val rp = keys.map { ref.points[it]!! }; val cp = keys.map { cur.points[it]!! }
        val t = landmarkTransform(spec.landmarks, ref.points, cur.points)?.first
        val ks = KeystoneFit.fromLandmarks(rp, cp)?.keystoneDeg ?: 0f
        val dYaw = if (ref.yaw != null && cur.yaw != null) cur.yaw - ref.yaw else 0f
        // Cabeceo relativo: con pose geométrica en ambas (mismo modelo, mismo paciente) el sesgo
        // anatómico se cancela; si no, Euler del motor.
        val dPitch = when {
            ref.pose != null && cur.pose != null -> cur.pose.pitch - ref.pose.pitch
            ref.pitch != null && cur.pitch != null -> cur.pitch - ref.pitch
            else -> 0f
        }
        val e0 = LiveMatch.evaluate(t, spec.pivot, wasMatched, LiveMatch.pointFraction(rp, cp), threshold, ks,
            dYaw, dPitch, gate = true)
        // Expresión: la misma que en la referencia (sonrisa, labios, ojos, cejas)
        val exprHints = if (useExpression && spec.detector == Detector.FACE && Expression.usable(cur.yaw) && Expression.usable(ref.yaw)) {
            val re = Expression.measure(ref.points); val ce = Expression.measure(cur.points)
            if (re != null && ce != null) Expression.compare(re, ce) else emptyList()
        } else emptyList()
        // expresión pedida (sorpresa, enojo, sonrisa…): obligatoria para el disparo automático
        val exprOk = spec.detector != Detector.FACE || Expression.meets(expression, cur.points, cur.yaw)
        val targetHints = if (exprOk || spec.detector != Detector.FACE) emptyList()
            else Expression.measure(cur.points)?.let { Expression.target(expression, it) }.orEmpty().ifEmpty { listOf("Expresión: ${expression.instruction}") }
        val allExpr = (exprHints + targetHints).distinct()
        val e = (if (allExpr.isEmpty()) e0 else e0.copy(
            status = if (e0.status == MatchStatus.MATCH) MatchStatus.CLOSE else e0.status, hints = e0.hints + allExpr)).copy(expressionOk = exprOk)
        // Mirada: debe coincidir con la de la referencia (si ambas se midieron)
        val rg = ref.gaze; val cg = cur.gaze
        if (!useGaze || rg == null || cg == null) return e
        val d = Pt(cg.x - rg.x, cg.y - rg.y)
        if (Gaze.matches(rg, cg)) return e.copy(gazeDelta = d)
        val hint = Gaze.hint(d)
        return e.copy(
            status = if (e.status == MatchStatus.MATCH) MatchStatus.CLOSE else e.status,
            hints = e.hints + listOfNotNull(hint), gazeDelta = d,
        )
    }

    fun poseOk(dYaw: Float, dPitch: Float) = abs(dYaw) <= POSE_MAX && abs(dPitch) <= POSE_MAX
}
