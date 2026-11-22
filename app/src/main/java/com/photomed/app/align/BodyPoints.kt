package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.hypot

/**
 * Puntos de referencia adicionales de las zonas del cuerpo (no faciales), para alinear la toma con la
 * referencia y comprobarla con muchos más puntos que los del detector de cuerpo (que en primeros planos
 * de manos o pies apenas ve 3–4 por extremidad):
 * - **Manos**: los 21 puntos de cada mano (muñeca, articulaciones y yema de cada dedo).
 * - **Pies**: punta (extremo distal), centro y bordes interno / externo a media longitud y en el antepié.
 * - **Brazos, piernas y tronco**: los bordes del contorno a ¼, ½ y ¾ de cada segmento (brazo,
 *   antebrazo, muslo, pierna, tronco), a cada lado.
 *
 * Los lados se nombran por la imagen (a = izquierda, b = derecha; en el modo de una sola extremidad,
 * a), así la referencia y la toma se emparejan igual. Coordenadas en unidades del ancho.
 */
object BodyPoints {
    fun hand(side: Char, i: Int) = "mano_${side}_$i"
    fun foot(side: Char, part: String) = "pie_${side}_$part"
    fun edge(seg: String, t: Int, side: Int) = "borde_${seg}_${t}_$side"

    private val SIDES = listOf('a', 'b')
    private val FOOT_PARTS = listOf("punta", "centro", "izq_50", "der_50", "izq_75", "der_75")
    private val TS = listOf(25, 50, 75)

    private val L = Landmarks
    private val ARM_SEGS = listOf("brazo_d" to (L.SHOULDER_R to L.ELBOW_R), "antebrazo_d" to (L.ELBOW_R to L.WRIST_R),
        "brazo_i" to (L.SHOULDER_L to L.ELBOW_L), "antebrazo_i" to (L.ELBOW_L to L.WRIST_L))
    private val LEG_SEGS = listOf("muslo_d" to (L.HIP_R to L.KNEE_R), "pierna_d" to (L.KNEE_R to L.ANKLE_R),
        "muslo_i" to (L.HIP_L to L.KNEE_L), "pierna_i" to (L.KNEE_L to L.ANKLE_L))

    private fun edges(segs: List<String>) = buildSet { for (g in segs) for (t in TS) for (sd in 1..2) add(edge(g, t, sd)) }

    val HANDS: Set<String> = buildSet { for (sd in SIDES) for (i in 0..20) add(hand(sd, i)) }
    val FEET: Set<String> = buildSet { for (sd in SIDES) for (p in FOOT_PARTS) add(foot(sd, p)) }
    val ARMS: Set<String> = edges(ARM_SEGS.map { it.first })
    val LEGS: Set<String> = edges(LEG_SEGS.map { it.first })
    val TORSO: Set<String> = edges(listOf("tronco"))

    /** Claves adicionales de cada tipo de guía (para [ZoneAlignment]). */
    fun keysFor(guide: GuideType): Set<String> = when (guide) {
        GuideType.HANDS -> HANDS
        GuideType.FEET -> FEET
        GuideType.ARM -> ARMS
        GuideType.LEGS -> LEGS
        GuideType.GLUTES -> LEGS + TORSO
        GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE -> TORSO + ARMS
        else -> emptySet()
    }

    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** Puntos adicionales de la zona con lo que se ve en la escena (vacío si faltan datos). */
    fun of(zone: Zone, s: ZoneCriteria.Scene): Map<String, Pt> {
        val out = HashMap<String, Pt>()
        when (zone.guide) {
            GuideType.HANDS -> s.hands?.let { hs ->
                val chosen = if (s.single) listOfNotNull(hs.maxByOrNull { d(it.points[0], it.points[9]) }) else hs.sortedBy { it.points[0].x }.take(2)
                chosen.forEachIndexed { i, h -> if (h.points.size >= 21) h.points.forEachIndexed { k, p -> out[hand(SIDES[i], k)] = p } }
            }
            GuideType.FEET -> s.classes?.let { c ->
                val fs = ZoneCriteria.feetOf(c).sortedByDescending { it.box.area }.take(if (s.single) 1 else 2).sortedBy { it.box.x0 }
                fs.forEachIndexed { i, f -> out.putAll(footPoints(f, c, SIDES[i])) }
            }
            else -> {}
        }
        val c = s.classes ?: return out
        val p = s.pose
        fun segEdges(name: String, a: Pt?, b: Pt?) { if (a != null && b != null) out.putAll(segmentEdges(c, name, a, b)) }
        if (zone.guide in setOf(GuideType.ARM, GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE))
            ARM_SEGS.forEach { (n, ab) -> segEdges(n, p[ab.first], p[ab.second]) }
        if (zone.guide == GuideType.LEGS || zone.guide == GuideType.GLUTES)
            LEG_SEGS.forEach { (n, ab) -> segEdges(n, p[ab.first], p[ab.second]) }
        if (zone.guide in setOf(GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE, GuideType.GLUTES)) {
            val sh = listOfNotNull(p[L.SHOULDER_L], p[L.SHOULDER_R]); val hp = listOfNotNull(p[L.HIP_L], p[L.HIP_R])
            if (sh.isNotEmpty() && hp.isNotEmpty()) {
                val a = Pt(sh.map { it.x }.average().toFloat(), sh.map { it.y }.average().toFloat())
                val b = Pt(hp.map { it.x }.average().toFloat(), hp.map { it.y }.average().toFloat())
                val wide = if (sh.size == 2) d(sh[0], sh[1]) else d(a, b) * 0.6f
                out.putAll(segmentEdges(c, "tronco", a, b, maxReach = wide * 0.9f))
            }
        }
        return out
    }

    /** Punta, centro y bordes del pie (a media longitud y en el antepié, ¾) a partir de su silueta. */
    private fun footPoints(f: ZoneCriteria.Foot, c: ZoneCriteria.Classes, side: Char): Map<String, Pt> {
        val o = f.outline; if (o.size < 6) return emptyMap()
        val k = c.width.toFloat()
        val y0 = f.box.y0 / k; val y1 = (f.box.y1 + 1) / k; val cx = (f.box.x0 + f.box.x1 + 1) / 2f / k
        val out = HashMap<String, Pt>()
        val tipY = o.maxOf { it.y }
        val tip = o.filter { it.y >= tipY - 0.01f }
        out[foot(side, "punta")] = Pt(tip.map { it.x }.average().toFloat(), tipY)
        out[foot(side, "centro")] = Pt(cx, (y0 + y1) / 2)
        for ((t, name) in listOf(0.5f to "50", 0.75f to "75")) {
            val y = y0 + (y1 - y0) * t
            val row = o.filter { kotlin.math.abs(it.y - y) < 1.5f / k + 0.004f }
            if (row.size >= 2) { out[foot(side, "izq_$name")] = row.minBy { it.x }; out[foot(side, "der_$name")] = row.maxBy { it.x } }
        }
        return out
    }

    /**
     * Bordes del contorno del cuerpo (piel, ropa u otros: todo lo que no es fondo ni pelo) a ¼, ½ y ¾
     * del segmento [a]→[b], a cada lado de su eje (1: a la izquierda del sentido a→b, 2: a la derecha).
     */
    fun segmentEdges(c: ZoneCriteria.Classes, name: String, a: Pt, b: Pt, maxReach: Float = d(a, b) * 0.6f): Map<String, Pt> {
        val k = c.width.toFloat(); val len = d(a, b); if (len < 1e-3f) return emptyMap()
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        fun body(x: Float, y: Float) = c.at((x * k).toInt(), (y * k).toInt()).let { it != FaceLabels.BG && it != FaceLabels.HAIR }
        val out = HashMap<String, Pt>()
        for (t in TS) {
            val mx = a.x + (b.x - a.x) * t / 100f; val my = a.y + (b.y - a.y) * t / 100f
            if (!body(mx, my)) continue
            for (sd in 1..2) {
                val sg = if (sd == 1) 1f else -1f
                var dd = 0f; val step = 1f / k
                while (dd < maxReach && body(mx + nx * dd * sg, my + ny * dd * sg)) dd += step
                if (dd in step * 2..maxReach * 0.999f) out[edge(name, t, sd)] = Pt(mx + nx * dd * sg, my + ny * dd * sg)
            }
        }
        return out
    }
}
