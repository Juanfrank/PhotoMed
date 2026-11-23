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

    /** Pies: silueta de cada pie, la punta de cada dedo y la línea de separación entre dedos. */
    fun feet(feet: List<ZoneCriteria.Foot>): Schema =
        Schema(feet.map { it.outline }, emptyList(), feet.flatMap { it.toes } + feet.flatMap { it.nails }, feet.flatMap { f -> f.toeLines.map { (a, b) -> listOf(a, b) } })

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
        GuideType.LEGS, GuideType.ARM, GuideType.NECK -> s.pose.takeIf { it.size >= 4 }?.let { body(it, s.classes) }?.takeIf { !it.isEmpty }
        else -> null
    }
}
