package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn
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

    /**
     * Ángulo de la cámara (grados hacia abajo respecto de la horizontal; 90 = cenital) que pide
     * cada vista del cuero cabelludo, con su tolerancia; null si la zona no lo exige.
     */
    fun cameraDownTarget(zone: Zone): Pair<Float, Float>? = when (zone.guide) {
        GuideType.SCALP -> 90f to 8f
        GuideType.SCALP_CROWN -> 35f to 12f
        GuideType.SCALP_PARIETAL -> 45f to 12f
        else -> null
    }

    /** Indicación para la inclinación del teléfono ([downDeg]: 90 = cámara trasera mirando al suelo). */
    fun cameraAngleHint(zone: Zone, downDeg: Float): String? {
        val (t, tol) = cameraDownTarget(zone) ?: return null
        val d = t - downDeg
        return when {
            d > tol -> HintText.es(HintText.CAMERA_DOWN, d)
            d < -tol -> HintText.es(HintText.CAMERA_UP, -d)
            else -> null
        }
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

    fun evaluate(zone: Zone, det: Detection, lighting: Lighting?,
                 /** Expresión pedida (arrugas dinámicas): sustituye a la expresión neutra. */
                 expression: com.photomed.app.data.FacialExpression = com.photomed.app.data.FacialExpression.NEUTRAL): MatchEval {
        val t = target(zone)
        // Zonas sin detector (cuero cabelludo, modo libre): sin referencia no hay nada que evaluar
        // en la imagen; las indicaciones (p. ej. cámara cenital) las añade la cámara.
        if (ZoneAlignment.of(zone).detector == Detector.NONE) return MatchEval(MatchStatus.SEARCHING, 0f, emptyList(), threshold = 1f)
        if (det.points.size < LiveDecision.MIN_POINTS) {
            val hint = if (t?.minYaw != null) trn("Perfil: alinee la oreja y el plano de Frankfort con la guía") else trn("No se detecta el rostro del paciente")
            return MatchEval(MatchStatus.SEARCHING, 0f, listOf(hint), threshold = 1f)
        }
        val hints = mutableListOf<String>()
        var off = 0f
        if (t != null) {
            val yawAbs = abs(det.yaw ?: 0f)
            val side = facingSide(det.points) ?: 0f
            val sideSign = if (side > 0.08f) 1 else if (side < -0.08f) -1 else 0
            if (t.minYaw != null) {
                if (yawAbs < t.minYaw) { hints += HintText.es(HintText.TO_PROFILE, t.minYaw - yawAbs); off += 1f }
            } else if (t.yaw == 0f) {
                if (yawAbs > t.yawTol) {
                    hints += HintText.es(if (sideSign > 0) HintText.TURN_LEFT else HintText.TURN_RIGHT, yawAbs)
                    off += yawAbs / t.yawTol
                }
            } else {
                val wrongSide = sideSign != 0 && sideSign != t.side
                val err = if (wrongSide) yawAbs + t.yaw!! else yawAbs - t.yaw!!
                if (wrongSide) hints += if (t.side > 0) trn("Cara: mirar a la derecha de la imagen") else trn("Cara: mirar a la izquierda de la imagen")
                else if (abs(err) > t.yawTol) hints += HintText.es(if (err < 0) HintText.TURN_MORE else HintText.TURN_LESS, yawAbs)
                off += abs(err) / t.yawTol
            }
            det.pitch?.let { if (abs(it) > PITCH_TOL) { hints += HintText.es(if (it > 0) HintText.CHIN_DOWN_FH else HintText.CHIN_UP_FH, abs(it)); off += abs(it) / PITCH_TOL } }
            // Inclinación: línea entre los ojos en la imagen (de frente u oblicuo); Euler Z si no hay ojos.
            val roll = (if (yawAbs < 30f) HeadRoll.of(det.points)?.first else null) ?: det.roll
            roll?.let {
                if (abs(it) > ROLL_TOL) {
                    hints += HintText.es(if (it > 0) HintText.ROLL_RIGHT else HintText.ROLL_LEFT, abs(it))
                    off += abs(it) / ROLL_TOL
                }
            }
        }
        // Expresión neutra en las vistas faciales completas; ojos abiertos siempre
        if (ZoneAlignment.of(zone).detector == Detector.FACE && Expression.usable(det.yaw)) {
            Expression.measure(det.points)?.let { ex ->
                val h = if (expression == com.photomed.app.data.FacialExpression.NEUTRAL) Expression.neutral(zone, ex) else Expression.target(expression, ex)
                h.forEach { hints += it; off += 0.8f }
            }
        }
        det.gaze?.let { g ->
            if (kotlin.math.abs(g.x) > Gaze.TOL_X * 1.2f || kotlin.math.abs(g.y) > Gaze.TOL_Y * 1.2f) {
                hints += trn("Mirada: al frente")
                off += 0.6f
            }
        }
        lighting?.let { l ->
            val m = maxOf(l.leftCheek, l.rightCheek)
            if (m > 0f && abs(l.leftCheek - l.rightCheek) / m > LIGHT_ASYM) {
                hints += if (l.leftCheek < l.rightCheek) trn("Luz desigual: lado izquierdo más oscuro") else trn("Luz desigual: lado derecho más oscuro")
                off += 0.5f
            }
            if (l.face < 0.35f) { hints += trn("Poca luz en el rostro"); off += 0.5f }
            if (l.face > 0.85f) { hints += trn("Demasiada luz: sin flash"); off += 0.5f }
        }
        // la expresión pedida es condición para el disparo automático
        val expressionOk = ZoneAlignment.of(zone).detector != Detector.FACE || Expression.meets(expression, det.points, det.yaw)
        val status = when {
            hints.isEmpty() && expressionOk -> MatchStatus.MATCH
            off <= 2.5f -> MatchStatus.CLOSE
            else -> MatchStatus.FAR
        }
        return MatchEval(status, (1f - off / 5f).coerceIn(0f, 1f), hints, expressionOk = expressionOk, threshold = 1f,
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

/**
 * Indicaciones de la toma (pose, mirada, luz, cámara…). Se generan en español porque otro código las
 * analiza por su texto (iconos de `HintCues`, filtros de la cámara); se traducen solo al mostrarlas
 * con [display]. Las que llevan números se crean con [es] a partir de una plantilla de [TEMPLATES]
 * (mismo resultado que `"…".format(…)`); las fijas se marcan con trn donde se crean.
 */
object HintText {
    val CAMERA_DOWN = trn("Cámara: apuntar más abajo %1\$.0f°")
    val CAMERA_UP = trn("Cámara: apuntar más arriba %1\$.0f°")
    val TO_PROFILE = trn("Cabeza: girar hasta el perfil (faltan %1\$.0f°)")
    val TURN_LEFT = trn("Cabeza: girar a la izquierda %1\$.0f°")
    val TURN_RIGHT = trn("Cabeza: girar a la derecha %1\$.0f°")
    val TURN_MORE = trn("Cabeza: girar más (%1\$.0f° de 45°)")
    val TURN_LESS = trn("Cabeza: girar menos (%1\$.0f° de 45°)")
    val CHIN_DOWN_FH = trn("Barbilla: bajar %1\$.0f° (Frankfort)")
    val CHIN_UP_FH = trn("Barbilla: subir %1\$.0f° (Frankfort)")
    val ROLL_RIGHT = trn("Cabeza: enderezar %1\$.0f° (inclinada a la derecha)")
    val ROLL_LEFT = trn("Cabeza: enderezar %1\$.0f° (inclinada a la izquierda)")
    val YAW_FIX = trn("Cabeza: corregir giro %1\$.0f°")
    val CHIN_DOWN = trn("Barbilla: bajar %1\$.0f°")
    val CHIN_UP = trn("Barbilla: subir %1\$.0f°")
    val EXPRESSION = trn("Expresión: %1\$s")
    val DETECTOR_HAIR = trn("Detector (pelo y piel): %1\$s")
    val DETECTOR = trn("Detector (%1\$s): %2\$s")

    /** Plantillas con datos, para reconocerlas al mostrar (las más específicas primero). */
    private val TEMPLATES = listOf(CAMERA_DOWN, CAMERA_UP, TO_PROFILE, TURN_LEFT, TURN_RIGHT, TURN_MORE, TURN_LESS,
        CHIN_DOWN_FH, CHIN_UP_FH, ROLL_RIGHT, ROLL_LEFT, YAW_FIX, CHIN_DOWN, CHIN_UP, EXPRESSION, DETECTOR_HAIR, DETECTOR)

    /** Indicación en español con datos (formato del idioma por defecto, igual que `"…".format`). */
    fun es(template: String, vararg args: Any?): String = String.format(template, *args)

    private val FMT = Regex("%(\\d+)\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?([sdf])|%%")

    private class Tpl(val template: String, val regex: Regex, val kinds: List<Pair<Int, Char>>)

    private val patterns: List<Tpl> by lazy {
        TEMPLATES.map { t ->
            val sb = StringBuilder(); val kinds = ArrayList<Pair<Int, Char>>(); var last = 0
            for (m in FMT.findAll(t)) {
                if (m.range.first > last) sb.append(Regex.escape(t.substring(last, m.range.first)))
                if (m.value == "%%") sb.append(Regex.escape("%"))
                else {
                    val kind = m.groupValues[2][0]
                    kinds += m.groupValues[1].toInt() to kind
                    sb.append(if (kind == 's') "(.+?)" else "([-+]?\\d+(?:[.,]\\d+)?)")
                }
                last = m.range.last + 1
            }
            if (last < t.length) sb.append(Regex.escape(t.substring(last)))
            Tpl(t, Regex(sb.toString()), kinds)
        }
    }

    /**
     * Texto de una indicación (en español, tal como se generó) en el idioma de la interfaz: las de
     * [TEMPLATES] se reconocen y se formatean con sus datos; las demás se traducen tal cual.
     */
    fun display(hint: String): String {
        for (p in patterns) {
            val m = p.regex.matchEntire(hint) ?: continue
            val args = arrayOfNulls<Any?>(p.kinds.maxOf { it.first })
            p.kinds.forEachIndexed { i, (pos, kind) ->
                val v = m.groupValues[i + 1]
                args[pos - 1] = when (kind) {
                    's' -> tr(v)
                    'd' -> v.toLongOrNull() ?: return tr(hint)
                    else -> v.replace(',', '.').toDoubleOrNull() ?: return tr(hint)
                }
            }
            return tr(p.template, *args)
        }
        return tr(hint)
    }
}
