package com.photomed.app.align

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
                 useGaze: Boolean = true): MatchEval {
        val keys = spec.landmarks.filter { it in ref.points && it in cur.points }
        if (keys.size < MIN_POINTS) {
            val what = if (spec.detector == Detector.FACE) "No se detecta el rostro" else "No se detecta el cuerpo"
            return MatchEval(MatchStatus.SEARCHING, 0f, listOf("$what del paciente"), threshold = threshold)
        }
        val rp = keys.map { ref.points[it]!! }; val cp = keys.map { cur.points[it]!! }
        val t = landmarkTransform(spec.landmarks, ref.points, cur.points)?.first
        val ks = KeystoneFit.fromLandmarks(rp, cp)?.keystoneDeg ?: 0f
        val dYaw = if (ref.yaw != null && cur.yaw != null) cur.yaw - ref.yaw else 0f
        val dPitch = if (ref.pitch != null && cur.pitch != null) cur.pitch - ref.pitch else 0f
        val e = LiveMatch.evaluate(t, spec.pivot, wasMatched, LiveMatch.pointFraction(rp, cp), threshold, ks,
            dYaw, dPitch, gate = true)
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
