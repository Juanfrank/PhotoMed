package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import com.photomed.app.i18n.trn
import kotlin.math.abs

/**
 * Estandarización de la toma sin foto de referencia (primera foto de una zona), según los
 * estándares de fotografía clínica (DiBernardo et al., Plast Reconstr Surg 1998; Yavuzer et al.,
 * Ann Plast Surg 2001; guías fotográficas de la ASPS):
 * - Vista estándar: giro de la cabeza 0° (frontal), 45° (oblicuo, hacia el lado de la zona) o
 *   perfil (MediaPipe estima ≈ 55–60° en un perfil verdadero y pierde el rostro cerca de 90°, donde se
 *   usa la silueta: se acepta ≥ [PROFILE_MIN_YAW], o ≥ [PROFILE_MIN_YAW_GEOM] con la punta nasal en el
 *   borde del óvalo).
 * - Plano de Frankfort horizontal: cabeceo ≈ 0°; cabeza sin inclinación lateral.
 * - Iluminación uniforme: diferencia de luminancia entre mejillas ≤ 15 %; exposición del rostro
 *   correcta (L* 35–85).
 * El sentido del giro (hacia la derecha o izquierda de la imagen) se obtiene de la geometría
 * (punta nasal respecto del centro del óvalo), común a ML Kit y MediaPipe.
 */
object Standardization {
    data class Target(val yaw: Float?, val yawTol: Float, val side: Int, val minYaw: Float? = null,
                      /** Cabeceo pedido (°, negativo = barbilla abajo) y su tolerancia. */
                      val pitch: Float = 0f, val pitchTol: Float = PITCH_TOL)

    /** side: +1 la cara mira hacia la derecha de la imagen, −1 hacia la izquierda, 0 de frente. */
    fun target(zone: Zone): Target? = when (zone.guide) {
        // giro tolerado de frente: 8° (la estimación de la malla tiene unos ±3–5° de ruido)
        GuideType.FACE_FRONT, GuideType.FOREHEAD, GuideType.PERIOCULAR, GuideType.LIPS -> Target(0f, 8f, 0)
        GuideType.FACE_OBLIQUE -> Target(45f, 8f, if (zone.mirrored) -1 else 1)
        // cuero cabelludo frontal: de frente con la cabeza inclinada hacia delante ≈ 30° (barbilla abajo)
        GuideType.SCALP_FRONT -> Target(0f, 8f, 0, pitch = -30f, pitchTol = 10f)
        GuideType.FACE_PROFILE -> Target(null, 0f, if (zone.mirrored) -1 else 1, minYaw = PROFILE_MIN_YAW)
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
            d > tol -> "Cámara: apuntar más abajo %.0f°".format(d)
            d < -tol -> "Cámara: apuntar más arriba %.0f°".format(-d)
            else -> null
        }
    }

    /** Perfil: giro mínimo estimado por MediaPipe (se satura ≈ 55–60° en un perfil verdadero). */
    const val PROFILE_MIN_YAW = 55f
    /** …o este giro con la punta nasal en el borde del óvalo ([facingSide] ≥ 0,95): perfil por geometría. */
    const val PROFILE_MIN_YAW_GEOM = 48f

    /** Cabeceo (plano de Frankfort) tolerado: la estimación de la malla tiene unos ±5° de ruido. */
    const val PITCH_TOL = 10f
    /** Corrección del cero del cabeceo (ver [evaluate]). */
    const val PITCH_ZERO = 4f
    /** Oblicuo por geometría: punta nasal (desde el centro del óvalo, en medios anchos) y giro mínimo. */
    const val OBLIQUE_SIDE_MIN = 0.45f
    const val OBLIQUE_SIDE_MAX = 1.02f
    const val OBLIQUE_MIN_YAW_GEOM = 15f
    const val ROLL_TOL = 5f
    const val ROLL_TOL_OBLIQUE = 12f
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
    /** Luz del rostro; [regional]: medida por regiones (sin pupilas ni nariz, ver [regionLighting]). */
    data class Lighting(val leftCheek: Float, val rightCheek: Float, val face: Float, val regional: Boolean = false)

    /**
     * Las indicaciones quedan en español (HintCues las analiza por su texto); se traducen al
     * mostrarlas (`ui.camera.HintText.display`).
     */
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
                // lo que falta para el perfil pesa por grados: una vista frontal no es «casi» un perfil
                val geomProfile = yawAbs >= PROFILE_MIN_YAW_GEOM && abs(side) >= 0.95f
                if (yawAbs < t.minYaw && !geomProfile) { hints += "Cabeza: girar hasta el perfil (faltan %.0f°)".format(t.minYaw - yawAbs); off += 1f + (t.minYaw - yawAbs) / 10f }
                // perfil del lado contrario (derecho ↔ izquierdo): no es la vista pedida
                if (sideSign != 0 && sideSign != t.side) {
                    hints += if (t.side > 0) trn("Cara: mirar a la derecha de la imagen") else trn("Cara: mirar a la izquierda de la imagen")
                    off += 2f + yawAbs / 10f
                }
            } else if (t.yaw == 0f) {
                if (yawAbs > t.yawTol) {
                    hints += (if (sideSign > 0) "Cabeza: girar a la izquierda %.0f°" else "Cabeza: girar a la derecha %.0f°").format(yawAbs)
                    off += yawAbs / t.yawTol
                }
            } else {
                val wrongSide = sideSign != 0 && sideSign != t.side
                // definición clínica del oblicuo de 45°: la punta nasal alineada con el contorno de la mejilla. El
                // giro estimado por la malla se queda corto en oblicuos reales (24–33° con la nariz ya en la mejilla,
                // fotos clínicas de dermatología): con la nariz entre ½ y el borde del óvalo y al menos 15° de giro, vale
                val geomOblique = !wrongSide && yawAbs >= OBLIQUE_MIN_YAW_GEOM && abs(side) in OBLIQUE_SIDE_MIN..OBLIQUE_SIDE_MAX
                val err = if (wrongSide) yawAbs + t.yaw!! else if (geomOblique) 0f else yawAbs - t.yaw!!
                if (wrongSide) hints += if (t.side > 0) trn("Cara: mirar a la derecha de la imagen") else trn("Cara: mirar a la izquierda de la imagen")
                else if (abs(err) > t.yawTol) hints += if (err < 0) "Cabeza: girar más (%.0f° de 45°)".format(yawAbs) else "Cabeza: girar menos (%.0f° de 45°)".format(yawAbs)
                off += abs(err) / t.yawTol
            }
            // cero del cabeceo calibrado con retratos frontales verificados: con el plano de Frankfort horizontal la
            // malla de MediaPipe da de media unos −5° (su modelo 3D canónico no está en el plano de Frankfort)
            det.pitch?.let { it + PITCH_ZERO - t.pitch }?.let { if (abs(it) > t.pitchTol) { hints += (if (it > 0) "Barbilla: bajar %.0f° (Frankfort)" else "Barbilla: subir %.0f° (Frankfort)").format(abs(it)); off += abs(it) / PITCH_TOL } }
            // Inclinación: línea entre los ojos en la imagen (de frente u oblicuo); Euler Z si no hay ojos.
            // De frente, la línea de los ojos; en el oblicuo la línea de los ojos se deforma con el giro y la
            // perspectiva (sesgo de 9–15° en fotos clínicas de oblicuos), así que se usa la del modelo 3D, que en el
            // oblicuo también lleva un sesgo de unos 6–7° hacia el lado del giro: tolerancia mayor (12°: sesgo de unos 7° más la tolerancia de frente)
            val oblique = abs(side) >= OBLIQUE_SIDE_MIN && t.yaw != 0f
            val roll = (if (yawAbs < 30f && !oblique) HeadRoll.of(det.points)?.first else null) ?: det.roll
            val rollTol = if (oblique) ROLL_TOL_OBLIQUE else ROLL_TOL
            roll?.let {
                if (abs(it) > rollTol) {
                    hints += (if (it > 0) "Cabeza: enderezar %.0f° (inclinada a la derecha)" else "Cabeza: enderezar %.0f° (inclinada a la izquierda)").format(abs(it))
                    off += abs(it) / rollTol
                }
            }
        }
        // Expresión neutra en las vistas faciales completas; ojos abiertos siempre
        // sonrisa o boca abierta con la expresión neutra pedida: impide el disparo (aunque el resto esté «casi»)
        var neutralBroken = false
        // (con la cabeza inclinada hacia delante no: los ojos miran abajo y la expresión no se mide bien)
        if (ZoneAlignment.of(zone).detector == Detector.FACE && Expression.usable(det.yaw) && (t?.pitch ?: 0f) == 0f) {
            Expression.measure(det.points)?.let { ex ->
                val h = if (expression == com.photomed.app.data.FacialExpression.NEUTRAL) Expression.neutral(zone, ex) else Expression.target(expression, ex)
                h.forEach { hints += it; off += 0.8f }
                if (expression == com.photomed.app.data.FacialExpression.NEUTRAL) neutralBroken = h.any { "sonrisa" in it || "juntar los labios" in it }
            }
        }
        // mirada al frente: solo en las vistas frontales. Con la cabeza girada (oblicuo, perfil), los ojos
        // que miran al frente de la cara proyectan el iris desplazado dentro del contorno del ojo; «iris
        // centrado» significaría mirar a la cámara. (Con referencia se compara la mirada entre fotos.)
        // (no en los labios: la mirada no cambia la zona; en la frente sí, mirar arriba la arruga)
        det.gaze?.takeIf { t?.yaw == 0f && t.pitch == 0f && zone != Zone.LIPS }?.let { g ->
            if (kotlin.math.abs(g.x) > Gaze.TOL_X * 1.2f || kotlin.math.abs(g.y) > Gaze.TOL_Y * 1.2f) {
                hints += trn("Mirada: al frente")
                off += 0.6f
            }
        }
        lighting?.let { l ->
            val m = maxOf(l.leftCheek, l.rightCheek)
            // la diferencia entre mejillas solo con las mejillas localizadas (no por regiones)
            // y solo en las vistas frontales: en oblicuos y perfiles una mejilla queda de lado
            // (solo en el rostro frontal: en los primeros planos las mejillas quedan en el borde o fuera)
            if (zone == Zone.FACE_FRONTAL && !l.regional && t?.yaw == 0f && m > 0f && abs(l.leftCheek - l.rightCheek) / m > LIGHT_ASYM) {
                hints += if (l.leftCheek < l.rightCheek) trn("Luz desigual: lado izquierdo más oscuro") else trn("Luz desigual: lado derecho más oscuro")
                off += 0.5f
            }
            lightHints(l, det.yaw).forEach { hints += it; off += 0.5f }
        }
        // la expresión pedida es condición para el disparo automático
        val expressionOk = (ZoneAlignment.of(zone).detector != Detector.FACE || Expression.meets(expression, det.points, det.yaw)) && !neutralBroken
        val status = when {
            hints.isEmpty() && expressionOk -> MatchStatus.MATCH
            off <= 2.5f -> MatchStatus.CLOSE
            else -> MatchStatus.FAR
        }
        return MatchEval(status, (1f - off / 5f).coerceIn(0f, 1f), hints, expressionOk = expressionOk, threshold = 1f,
            headYawDelta = det.yaw ?: 0f, headPitchDelta = det.pitch ?: 0f, gazeDelta = det.gaze)
    }

    /** Luminancia media (0..1) en un cuadrado de radio [rad] (unidades del ancho) alrededor de [p]. */
    /** Luz mínima y máxima del rostro (luminancia media 0..1) para avisar. */
    const val MIN_FACE_LIGHT = 0.35f
    const val MAX_FACE_LIGHT = 0.85f

    /** Luz mínima del rostro en vistas giradas (oblicuo, perfil): se mide el lado que mira a la cámara. */
    const val MIN_FACE_LIGHT_TURNED = 0.28f
    /** Giro de la cabeza a partir del cual la luz se mide en el lado que mira a la cámara. */
    const val TURNED_YAW = 30f

    /**
     * Avisos de luz insuficiente o excesiva del rostro (también con referencia, fuera de los protocolos).
     * Con la cabeza girada ([yaw] ≥ 30°) el lado lejano queda en sombra por la propia vista: se mide el lado
     * iluminado (el más claro), con un mínimo algo menor (no penaliza la sombra natural ni la piel oscura).
     */
    fun lightHints(l: Lighting, yaw: Float? = null): List<String> = buildList {
        val turned = yaw != null && abs(yaw) >= TURNED_YAW
        val v = if (turned) maxOf(l.face, l.leftCheek, l.rightCheek) else l.face
        if (v < (if (turned) MIN_FACE_LIGHT_TURNED else MIN_FACE_LIGHT)) add(trn("Poca luz en el rostro"))
        if (l.face > MAX_FACE_LIGHT) add(trn("Demasiada luz: sin flash"))
    }

    /** Luz mínima y máxima de la zona del cuerpo (luminancia media 0..1 de su piel) y fracción quemada máxima. */
    const val MIN_BODY_LIGHT = 0.30f
    const val MAX_BODY_LIGHT = 0.88f
    const val MAX_BURNT = 0.20f

    /**
     * Luz de una zona del cuerpo: luminancia media de la piel ([skin], misma rejilla que la imagen; si hay
     * pocos píxeles de piel, el centro del cuadro) y fracción quemada (casi blanca). Avisos si falta o sobra.
     */
    fun bodyLightHints(rgb: IntArray, w: Int, h: Int, skin: ((Int, Int) -> Boolean)?): List<String> {
        var s = 0f; var n = 0; var burnt = 0
        val step = maxOf(1, w / 120)
        fun add(c: Int) { val v = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f; s += v; n++; if (v > 0.97f) burnt++ }
        if (skin != null) for (y in 0 until h step step) for (x in 0 until w step step) if (skin(x, y)) add(rgb[y * w + x])
        if (n < 50) { s = 0f; n = 0; burnt = 0
            for (y in h / 4 until h * 3 / 4 step step) for (x in w / 4 until w * 3 / 4 step step) add(rgb[y * w + x]) }
        if (n == 0) return emptyList()
        val m = s / n
        return buildList {
            if (m < MIN_BODY_LIGHT) add(trn("Poca luz en la zona"))
            if (m > MAX_BODY_LIGHT || burnt > MAX_BURNT * n) add(trn("Demasiada luz en la zona: sin flash"))
        }
    }

    private fun lum(rgb: IntArray, w: Int, h: Int, p: Pt, rad: Float): Float {
        val cx = (p.x * w).toInt(); val cy = (p.y * w).toInt(); val rr = (rad * w).toInt().coerceAtLeast(1)
        var s = 0f; var n = 0
        val step = maxOf(1, rr / 12)
        for (y in cy - rr..cy + rr step step) for (x in cx - rr..cx + rr step step) if (x in 0 until w && y in 0 until h) {
            val c = rgb[y * w + x]
            s += (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f; n++
        }
        return if (n > 0) s / n else 0f
    }

    /**
     * Luz por regiones cuando no se ven pupilas y nariz (primeros planos: labios, ojo, frente, o la
     * cara parcial): en el rectángulo de los puntos detectados o, sin ellos, en el centro del encuadre.
     * Centro = rostro; tercios izquierdo y derecho = «mejillas» (lados de la imagen).
     */
    fun regionLighting(rgb: IntArray, w: Int, h: Int, points: Map<String, Pt>): Lighting {
        val (l, r, c) = regionAreas(w, h, points)
        return Lighting(lum(rgb, w, h, l.center, l.radius), lum(rgb, w, h, r.center, r.radius), lum(rgb, w, h, c.center, c.radius), regional = true)
    }

    /** Zona donde se mide la luz: centro y radio (unidades del ancho). */
    data class LightArea(val center: Pt, val radius: Float)

    /** Áreas de [regionLighting]: «mejilla» izquierda y derecha de la imagen y centro. */
    fun regionAreas(w: Int, h: Int, points: Map<String, Pt>): Triple<LightArea, LightArea, LightArea> {
        val hw = h / w.toFloat()
        val (x0, x1, y0, y1) = if (points.size >= 8) {
            listOf(points.values.minOf { it.x }, points.values.maxOf { it.x }, points.values.minOf { it.y }, points.values.maxOf { it.y })
        } else listOf(0.25f, 0.75f, hw * 0.25f, hw * 0.75f)
        val bw = (x1 - x0).coerceAtLeast(0.1f); val bh = (y1 - y0).coerceAtLeast(0.1f)
        val cy = y0 + bh * 0.55f
        val rad = (minOf(bw, bh) * 0.14f).coerceIn(0.02f, 0.08f)
        return Triple(LightArea(Pt(x0 + bw * 0.22f, cy), rad), LightArea(Pt(x0 + bw * 0.78f, cy), rad), LightArea(Pt(x0 + bw * 0.5f, y0 + bh * 0.5f), rad * 1.6f))
    }

    /** Centro de los puntos cuyo nombre empieza por [prefix] (contorno del ojo, base nasal…). */
    private fun centroid(points: Map<String, Pt>, prefix: String): Pt? {
        val g = points.filterKeys { it.startsWith(prefix) }.values
        return if (g.size < 2) null else Pt(g.map { it.x }.average().toFloat(), g.map { it.y }.average().toFloat())
    }

    /**
     * Luminancia en las mejillas (entre ojo y borde del óvalo) y en el centro del rostro. Si faltan las
     * pupilas o la nariz se usan los contornos de los ojos y de la nariz y, si tampoco están, la luz
     * por regiones ([regionLighting]): siempre hay medida cuando hay imagen.
     */
    fun lighting(rgb: IntArray, w: Int, h: Int, points: Map<String, Pt>,
                 /** Giro de la cabeza: muy girada (> 40°), las mejillas proyectadas caen fuera del rostro. */
                 yaw: Float? = null): Lighting? {
        if (w <= 0 || h <= 0 || rgb.size < w * h) return null
        val (a, regional) = lightAreas(w, h, points, yaw)
        return Lighting(lum(rgb, w, h, a.first.center, a.first.radius), lum(rgb, w, h, a.second.center, a.second.radius),
            lum(rgb, w, h, a.third.center, a.third.radius), regional)
    }

    /**
     * Dónde se mide la luz (mejilla izquierda y derecha de la imagen, centro del rostro) y si es por
     * regiones: por las mejillas entre ojo y óvalo; por regiones si la cabeza está muy girada o faltan
     * pupilas y nariz.
     */
    fun lightAreas(w: Int, h: Int, points: Map<String, Pt>, yaw: Float? = null): Pair<Triple<LightArea, LightArea, LightArea>, Boolean> {
        if (yaw != null && abs(yaw) > 40f && points.size >= 8) return regionAreas(w, h, points) to true
        val l = points[Landmarks.EYE_L] ?: centroid(points, "eye_l_")
        val r = points[Landmarks.EYE_R] ?: centroid(points, "eye_r_")
        val nose = points["nose_bottom_1"] ?: points[Landmarks.NOSE_BASE] ?: centroid(points, "nose_bottom_")
        if (l == null || r == null || nose == null) return regionAreas(w, h, points) to true
        val ovalL = points["oval_8"]; val ovalR = points["oval_28"]
        // mejilla: a medio camino entre la pupila (bajada a la altura de la nariz) y el borde del óvalo
        fun cheek(eye: Pt, edge: Pt?): Pt = Pt(if (edge != null) (eye.x + edge.x) / 2 else eye.x, (eye.y + nose.y) / 2)
        val leftImg = if (r.x < l.x) cheek(r, ovalR) else cheek(l, ovalL)
        val rightImg = if (r.x < l.x) cheek(l, ovalL) else cheek(r, ovalR)
        return Triple(LightArea(leftImg, 0.03f), LightArea(rightImg, 0.03f), LightArea(Pt((l.x + r.x) / 2, (l.y + nose.y) / 2), 0.05f)) to false
    }
}
