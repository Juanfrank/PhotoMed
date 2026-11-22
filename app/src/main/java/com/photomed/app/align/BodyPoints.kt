package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.hypot

/**
 * Puntos de referencia adicionales de las zonas del cuerpo (no faciales), para alinear la toma con la
 * referencia y comprobarla con muchos más puntos que los del detector de cuerpo (que en primeros planos
 * de manos o pies apenas ve 3–4 por extremidad):
 * - **Manos**: los 21 puntos de cada mano (muñeca, articulaciones y yema de cada dedo).
 * - **Pies**: punta (extremo distal) y, medidos desde ella en anchos de pie (½ y 1), los bordes a cada
 *   lado y el centro: no dependen de cuánta pierna se vea (estables entre cuadros).
 * - **Brazos, piernas y tronco**: los bordes del contorno a ¼, ½ y ¾ de cada segmento (brazo,
 *   antebrazo, muslo, pierna, tronco), a cada lado.
 *
 * Los lados se nombran por la imagen (a = izquierda, b = derecha; en el modo de una sola extremidad,
 * a), así la referencia y la toma se emparejan igual. Coordenadas en unidades del ancho.
 */
object BodyPoints {
    /** Punto del contorno (pie, borde de miembro o tronco), no anatómico: algo menos preciso entre cuadros. */
    fun isContour(key: String) = key.startsWith("pie_") || key.startsWith("borde_")

    fun hand(side: Char, i: Int) = "mano_${side}_$i"
    fun foot(side: Char, part: String) = "pie_${side}_$part"
    fun edge(seg: String, t: Int, side: Int) = "borde_${seg}_${t}_$side"

    private val SIDES = listOf('a', 'b')
    /** Alturas (en anchos de pie desde la punta) de los bordes del pie. */
    private val FOOT_ROWS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f)
    private val FOOT_PARTS = listOf("punta", "centro") + FOOT_ROWS.indices.flatMap { listOf("izq_${it + 1}", "der_${it + 1}") }
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
                // los dos mayores, ordenados por la posición de su punta (no por su caja, que cambia con la pierna)
                val fs = ZoneCriteria.feetOf(c).sortedByDescending { it.box.area }.take(if (s.single) 1 else 2)
                    .sortedBy { f -> f.outline.maxByOrNull { it.y }?.x ?: 0f }
                // cada pie por su mitad de la imagen (si en un cuadro se pierde o se une uno, el otro no cambia
                // de nombre); con uno solo, «a»
                fs.forEach { f ->
                    val tx = f.outline.maxByOrNull { it.y }?.x ?: 0.5f
                    val side = if (tx < 0.5f) 'a' else 'b'
                    out.putAll(footPoints(f, c, if (s.single) 'a' else side))
                }
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

    /**
     * Punta del pie y, a ½ y a 1 ancho de pie de ella hacia el tobillo, los bordes a cada lado; el centro,
     * entre los bordes a ¾. Todo referido a la punta y al ancho del antepié, no a la caja del pie (que cambia
     * según cuánta pierna entra en el cuadro).
     */
    private fun footPoints(f: ZoneCriteria.Foot, c: ZoneCriteria.Classes, side: Char): Map<String, Pt> {
        val o = f.outline; if (o.size < 6) return emptyMap()
        val k = c.width.toFloat()
        // filas del contorno: y → (x mínima, x máxima)
        val rows = o.groupBy { Math.round(it.y * k / 2f) }.mapValues { (_, v) -> v.minOf { it.x } to v.maxOf { it.x } }
        val tipY = o.maxOf { it.y }
        val tipRow = o.filter { it.y >= tipY - 1.5f / k }
        val out = HashMap<String, Pt>()
        out[foot(side, "punta")] = Pt((tipRow.minOf { it.x } + tipRow.maxOf { it.x }) / 2, tipY)
        // ancho del antepié: percentil 90 del ancho de las filas cercanas a la punta
        val near = rows.filterKeys { it * 2f / k > tipY - 0.6f * (f.box.x1 - f.box.x0 + 1) / k }.values.map { it.second - it.first }.sorted()
        if (near.isEmpty()) return out
        val fw = near[(near.size * 0.9f).toInt().coerceAtMost(near.size - 1)]
        fun rowAt(y: Float) = rows[Math.round(y * k / 2f)]
        FOOT_ROWS.forEachIndexed { i, d ->
            rowAt(tipY - d * fw)?.let { (l, r) -> out[foot(side, "izq_${i + 1}")] = Pt(l, tipY - d * fw); out[foot(side, "der_${i + 1}")] = Pt(r, tipY - d * fw) }
        }
        rowAt(tipY - 0.75f * fw)?.let { (l, r) -> out[foot(side, "centro")] = Pt((l + r) / 2, tipY - 0.75f * fw) }
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
