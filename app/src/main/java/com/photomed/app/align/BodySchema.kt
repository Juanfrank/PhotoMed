package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.hypot

/**
 * Esquema de la guía ajustado a la anatomía del paciente (zonas no faciales). La ilustración fija de
 * la guía no puede girar ni alargar un dedo o un miembro; cuando la cámara ve la zona, se dibuja este
 * esquema sobre lo detectado: cada dedo de la mano con su ángulo y su longitud (21 puntos de la mano),
 * cada pie con su silueta y la punta de cada dedo, y brazos, piernas y tronco con los suyos (puntos
 * del cuerpo). Así la diferencia de ángulo o de longitud con la ilustración no es un incumplimiento:
 * los criterios ([ZoneCriteria]) solo exigen que las partes existan y estén como pide la toma.
 *
 * Coordenadas en unidades del ancho del cuadro (como los puntos).
 */
object BodySchema {
    /** Segmento con grosor (dedo, falange, miembro): de [a] a [b] con radio [r]. */
    class Capsule(val a: Pt, val b: Pt, val r: Float)

    class Schema(val polygons: List<List<Pt>> = emptyList(), val capsules: List<Capsule> = emptyList(), val dots: List<Pt> = emptyList(),
                 /** Líneas abiertas (separación entre los dedos del pie). */
                 val lines: List<List<Pt>> = emptyList()) {
        val isEmpty get() = polygons.isEmpty() && capsules.isEmpty() && dots.isEmpty() && lines.isEmpty()
    }

    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /**
     * Manos: con la piel segmentada, el contorno real de cada mano (su borde, dedo a dedo) y la yema de
     * cada dedo; sin ella, la palma ensanchada hasta su borde y cada falange con su grosor.
     */
    fun hands(hands: List<ZoneCriteria.Hand>, classes: ZoneCriteria.Classes? = null): Schema {
        val polys = ArrayList<List<Pt>>(); val caps = ArrayList<Capsule>(); val dots = ArrayList<Pt>()
        for (h in hands) {
            val p = h.points; if (p.size < 21) continue
            val palm = d(p[0], p[9]).coerceAtLeast(1e-4f)
            val outline = classes?.let { runCatching { ZoneCriteria.handOutline(it, h) }.getOrNull() }
            // uñas claramente visibles (dorso): se marcan como punto de referencia
            classes?.rgb?.let { rgb -> Nails.ofHand(rgb, classes.width, classes.height, p, ZoneCriteria.STRONG_NAIL_DE).filterNotNull()
                .forEach { n -> dots += Pt(n.x / classes.width, n.y / classes.width) } }
            if (outline != null) {
                polys += outline
                listOf(4, 8, 12, 16, 20).forEach { dots += p[it] }
                continue
            }
            val r = palm * 0.11f
            // los puntos de la palma van por el eje de los dedos: se ensancha hasta el borde de la mano
            val ctr = Pt(listOf(0, 5, 9, 13, 17).map { p[it].x }.average().toFloat(), listOf(0, 5, 9, 13, 17).map { p[it].y }.average().toFloat())
            polys += listOf(p[0], p[1], p[2], p[5], p[9], p[13], p[17]).map { q ->
                val dd = d(q, ctr).coerceAtLeast(1e-4f); Pt(q.x + (q.x - ctr.x) / dd * r, q.y + (q.y - ctr.y) / dd * r)
            }
            for (chain in listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8), listOf(9, 10, 11, 12), listOf(13, 14, 15, 16), listOf(17, 18, 19, 20))) {
                for (i in 1 until chain.size) caps += Capsule(p[chain[i - 1]], p[chain[i]], r * (if (chain[0] == 1) 1.15f else 1f) * (1f - 0.12f * (i - 1)))
                dots += p[chain.last()]
            }
        }
        return Schema(polys, caps, dots)
    }

    /**
     * Pies: silueta de cada pie, la punta de cada dedo y la línea de separación entre dedos. La silueta se
     * corta a la altura del pie (≈ 2,8 anchos desde la punta de los dedos): con las piernas en el cuadro la
     * mancha de piel sube hasta el borde y el contorno parecía el de la pierna entera.
     */
    fun feet(feet: List<ZoneCriteria.Foot>): Schema =
        Schema(feet.map { footOutline(it) }, emptyList(), feet.flatMap { it.toes } + feet.flatMap { it.nails }, feet.flatMap { f -> f.toeLines.map { (a, b) -> listOf(a, b) } })

    private fun footOutline(f: ZoneCriteria.Foot): List<Pt> {
        val o = f.outline; if (o.size < 8) return o
        val n = o.size / 2
        // contorno: lado izquierdo de arriba abajo y el derecho de abajo arriba (filas emparejadas)
        val left = o.take(n); val right = o.drop(n).reversed()
        if (left.size != right.size) return o
        val y1 = left.maxOf { it.y }; val y0 = left.minOf { it.y }
        val band = left.indices.filter { left[it].y >= y1 - 0.3f * (y1 - y0) }
        val fw = band.map { right[it].x - left[it].x }.sorted().let { it.getOrNull(it.size / 2) } ?: return o
        val cut = y1 - 2.8f * fw
        if (cut <= y0) return o
        val keep = left.indices.filter { left[it].y >= cut }
        return keep.map { left[it] } + keep.reversed().map { right[it] }
    }

    /**
     * Silueta real del cuerpo en la zona: el borde de la persona en la segmentación (piel, ropa y pelo),
     * recortado a la región de la zona según los puntos del cuerpo (tronco: de los hombros a las caderas;
     * piernas: de las caderas a los tobillos; brazo: del hombro a la muñeca…), con el eje de cada miembro y
     * las articulaciones. Sin puntos del cuerpo (primeros planos, de espaldas) la mancha de la persona más
     * grande del cuadro. null si no hay segmentación o la silueta no sale.
     */
    fun bodyOutline(zone: Zone, pose: Map<String, Pt>, c: ZoneCriteria.Classes, aspect: Float): Schema? {
        val L = Landmarks
        val k = c.width.toFloat()
        fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
        val sh = listOfNotNull(pose[L.SHOULDER_L], pose[L.SHOULDER_R])
        val hp = listOfNotNull(pose[L.HIP_L], pose[L.HIP_R])
        // región de la zona (x0, y0, x1, y1 en unidades del ancho); null: todo el cuadro
        val roi: FloatArray? = when (zone.guide) {
            GuideType.TORSO_FRONT, GuideType.TORSO_SIDE, GuideType.BREASTS, GuideType.BACK -> if (sh.isNotEmpty() && hp.isNotEmpty()) {
                val sy = sh.minOf { it.y }; val hy = hp.maxOf { it.y }; val t = hy - sy
                floatArrayOf(0f, sy - 0.12f * t, 1f, hy + 0.15f * t)
            } else null
            GuideType.GLUTES -> {
                val kn = listOfNotNull(pose[L.KNEE_L], pose[L.KNEE_R])
                if (hp.size == 2 && kn.isNotEmpty()) { val hy = hp.minOf { it.y }; val ky = kn.maxOf { it.y }; floatArrayOf(0f, hy - 0.45f * (ky - hy), 1f, hy + 0.7f * (ky - hy)) } else null
            }
            GuideType.LEGS -> {
                val an = listOfNotNull(pose[L.ANKLE_L], pose[L.ANKLE_R], pose[L.HEEL_L], pose[L.HEEL_R], pose[L.FOOT_INDEX_L], pose[L.FOOT_INDEX_R])
                if (hp.isNotEmpty() && an.isNotEmpty()) { val hy = hp.minOf { it.y }; val ay = an.maxOf { it.y }; floatArrayOf(0f, hy - 0.08f * (ay - hy), 1f, ay + 0.04f * (ay - hy)) } else null
            }
            GuideType.ARM -> {
                val arms = listOf(listOf(L.SHOULDER_R, L.ELBOW_R, L.WRIST_R), listOf(L.SHOULDER_L, L.ELBOW_L, L.WRIST_L)).map { ks -> ks.mapNotNull { pose[it] } }
                val want = if (zone == Zone.ARM_R) 0 else 1
                val arm = arms[want].takeIf { it.size == 3 } ?: arms.firstOrNull { it.size == 3 }
                arm?.let { a ->
                    val len = d(a[0], a[1]) + d(a[1], a[2]); val m = 0.22f * len
                    floatArrayOf(a.minOf { it.x } - m, a.minOf { it.y } - 0.1f * len, a.maxOf { it.x } + m, a.maxOf { it.y } + 0.25f * len)
                }
            }
            GuideType.NECK -> if (sh.size == 2) {
                val mouth = listOfNotNull(pose[L.MOUTH_L], pose[L.MOUTH_R], pose[L.NOSE]).minOfOrNull { it.y } ?: (sh.minOf { it.y } - 0.6f * d(sh[0], sh[1]))
                floatArrayOf(0f, mouth, 1f, sh.maxOf { it.y } + 0.45f * d(sh[0], sh[1]))
            } else null
            else -> return null
        }
        val px0 = ((roi?.get(0) ?: 0f) * k).toInt().coerceIn(0, c.width - 1); val px1 = ((roi?.get(2) ?: 1f) * k).toInt().coerceIn(0, c.width - 1)
        val py0 = ((roi?.get(1) ?: 0f) * k).toInt().coerceIn(0, c.height - 1); val py1 = ((roi?.get(3) ?: aspect) * k).toInt().coerceIn(0, c.height - 1)
        if (px1 - px0 < 4 || py1 - py0 < 4) return null
        // el brazo y los primeros planos sin puntos: la piel (la ropa del resto del cuerpo no es la zona);
        // el tronco y las piernas: la persona entera (con ropa interior o pantalón corto, la silueta sigue)
        val skinOnly = zone.guide == GuideType.ARM || pose.isEmpty()
        fun inside(x: Int, y: Int): Boolean {
            if (x !in px0..px1 || y !in py0..py1) return false
            val q = c.at(x, y)
            return if (skinOnly) q == FaceLabels.BODY_SKIN || q == FaceLabels.FACE_SKIN else q != 0
        }
        // semilla: el punto de la persona más cercano al centro de la zona (de los puntos del cuerpo o del cuadro)
        val anchor = when {
            zone.guide == GuideType.ARM && roi != null -> Pt((roi[0] + roi[2]) / 2, (roi[1] + roi[3]) / 2)
            sh.isNotEmpty() && hp.isNotEmpty() -> mid(mid(sh.first(), sh.last()), mid(hp.first(), hp.last()))
            roi != null -> Pt((roi[0] + roi[2]) / 2, (roi[1] + roi[3]) / 2)
            else -> Pt(0.5f, aspect / 2)
        }
        val ax = (anchor.x * k).toInt(); val ay = (anchor.y * k).toInt()
        var seed: Pair<Int, Int>? = null; var best = Int.MAX_VALUE
        for (y in py0..py1 step 2) for (x in px0..px1 step 2) if (inside(x, y)) {
            val dd = (x - ax) * (x - ax) + (y - ay) * (y - ay); if (dd < best) { best = dd; seed = x to y }
        }
        val sd = seed ?: return null
        val (pts, area) = ZoneCriteria.traceComponent(c.width, c.height, ::inside, sd) ?: return null
        if (area < 0.03f * c.width * c.height) return null
        // suavizado (la máscara es de baja resolución: escalones de un píxel)
        val raw = pts.map { (x, y) -> Pt((x + 0.5f) / k, (y + 0.5f) / k) }
        val n = raw.size
        val outline = raw.indices.map { i ->
            val a = raw[(i - 1 + n) % n]; val b = raw[i]; val e = raw[(i + 1) % n]
            Pt((a.x + 2 * b.x + e.x) / 4, (a.y + 2 * b.y + e.y) / 4)
        }
        // eje de cada miembro (líneas) y articulaciones (puntos)
        val chains = listOf(listOf(L.SHOULDER_L, L.ELBOW_L, L.WRIST_L), listOf(L.SHOULDER_R, L.ELBOW_R, L.WRIST_R),
            listOf(L.HIP_L, L.KNEE_L, L.ANKLE_L), listOf(L.HIP_R, L.KNEE_R, L.ANKLE_R), listOf(L.SHOULDER_L, L.SHOULDER_R), listOf(L.HIP_L, L.HIP_R))
        fun inRoi(p: Pt) = roi == null || (p.x in roi[0]..roi[2] && p.y in roi[1]..roi[3])
        val lines = chains.map { ch -> ch.mapNotNull { pose[it] }.filter(::inRoi) }.filter { it.size >= 2 }
        val joints = listOf(L.SHOULDER_L, L.SHOULDER_R, L.ELBOW_L, L.ELBOW_R, L.WRIST_L, L.WRIST_R, L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R, L.ANKLE_L, L.ANKLE_R)
            .mapNotNull { pose[it] }.filter(::inRoi)
        return Schema(listOf(outline), emptyList(), joints, lines)
    }

    /** Brazos, piernas y tronco con el ángulo y la longitud de los del paciente. */
    fun body(pose: Map<String, Pt>, classes: ZoneCriteria.Classes? = null): Schema {
        val L = Landmarks
        val sw = listOfNotNull(pose[L.SHOULDER_L], pose[L.SHOULDER_R]).let { if (it.size == 2) d(it[0], it[1]) else 0.25f }
        val caps = ArrayList<Capsule>()
        // grosor de cada miembro: el de la piel medida a lo ancho (si se ve) o proporcional a los hombros
        fun bone(a: String, b: String, k: Float) {
            val pa = pose[a]; val pb = pose[b]; if (pa == null || pb == null) return
            val r = classes?.let { ZoneCriteria.halfWidth(it, pa, pb) }?.coerceIn(sw * k * 0.5f, sw * k * 1.8f) ?: (sw * k)
            caps += Capsule(pa, pb, r)
        }
        bone(L.SHOULDER_L, L.ELBOW_L, 0.17f); bone(L.ELBOW_L, L.WRIST_L, 0.13f)
        bone(L.SHOULDER_R, L.ELBOW_R, 0.17f); bone(L.ELBOW_R, L.WRIST_R, 0.13f)
        bone(L.HIP_L, L.KNEE_L, 0.26f); bone(L.KNEE_L, L.ANKLE_L, 0.18f)
        bone(L.HIP_R, L.KNEE_R, 0.26f); bone(L.KNEE_R, L.ANKLE_R, 0.18f)
        bone(L.ANKLE_L, L.FOOT_INDEX_L, 0.08f); bone(L.ANKLE_R, L.FOOT_INDEX_R, 0.08f)
        val torso = listOf(L.SHOULDER_R, L.SHOULDER_L, L.HIP_L, L.HIP_R).mapNotNull { pose[it] }
        return Schema(if (torso.size == 4) listOf(torso) else emptyList(), caps)
    }

    /** El mismo esquema con cada punto transformado (p. ej. de la orientación de la guía al cuadro). */
    fun Schema.map(f: (Pt) -> Pt): Schema = Schema(polygons.map { it.map(f) }, capsules.map { Capsule(f(it.a), f(it.b), it.r) }, dots.map(f), lines.map { it.map(f) })

    /** Esquema de la zona con lo detectado (null si la zona usa la ilustración fija o no hay datos). */
    fun of(zone: Zone, s: ZoneCriteria.Scene): Schema? = when (zone.guide) {
        GuideType.HANDS -> s.hands?.takeIf { it.isNotEmpty() }?.let { hands(it, s.classes) }
            // sin los puntos de la mano: su contorno por la piel, con la separación entre dedos
            ?: s.classes?.let { feet(ZoneCriteria.skinHands(it)) }?.takeIf { !it.isEmpty }
        GuideType.FEET -> s.classes?.let { feet(ZoneCriteria.feetOf(it)) }?.takeIf { !it.isEmpty }
        GuideType.TORSO_FRONT, GuideType.TORSO_SIDE, GuideType.BREASTS, GuideType.BACK, GuideType.GLUTES,
        GuideType.LEGS, GuideType.ARM, GuideType.NECK ->
            // la silueta real (segmentación) y, sin ella, el esquema por los puntos del cuerpo
            s.classes?.let { c -> runCatching { bodyOutline(zone, s.pose, c, s.aspect) }.getOrNull() }
                ?: s.pose.takeIf { it.size >= 4 }?.let { body(it, s.classes) }?.takeIf { !it.isEmpty }
        else -> null
    }
}
