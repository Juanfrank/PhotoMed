package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.hypot

/**
 * Puntos de referencia adicionales de las zonas del cuerpo (no faciales), para alinear la toma con la
 * referencia y comprobarla con muchos más puntos que los del detector de cuerpo (que en primeros planos
 * de manos o pies apenas ve 3–4 por extremidad):
 * - **Manos**: los 21 puntos de cada mano (muñeca, articulaciones y yema de cada dedo) y, con la piel
 *   segmentada, el fondo de los cuatro espacios entre dedos y los dos bordes de la muñeca (del contorno).
 * - **Pies**: punta (extremo distal) y, medidos desde ella en anchos de pie (½ y 1), los bordes a cada
 *   lado y el centro: no dependen de cuánta pierna se vea (estables entre cuadros); con los cinco dedos
 *   distinguidos, la punta de cada uno.
 * - **Brazos, piernas y tronco**: los bordes del contorno a ¼, ½ y ¾ de cada segmento (brazo,
 *   antebrazo, muslo, pierna), a cada lado; el tronco (abdomen, tórax, espalda), a seis alturas.
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
    private val FOOT_PARTS = listOf("punta", "centro") + FOOT_ROWS.indices.flatMap { listOf("izq_${it + 1}", "der_${it + 1}") } + (1..5).map { "dedo_$it" }
    private val TS = listOf(25, 50, 75)
    /** Alturas (%) de los bordes del tronco, de los hombros a las caderas: más puntos que en los miembros. */
    private val TORSO_TS = listOf(10, 25, 40, 55, 70, 85)
    fun handEdge(side: Char, part: String) = "borde_mano_${side}_$part"
    private val HAND_EDGES = (1..4).map { "valle_$it" } + listOf("muneca_1", "muneca_2")

    private val L = Landmarks
    private val ARM_SEGS = listOf("brazo_d" to (L.SHOULDER_R to L.ELBOW_R), "antebrazo_d" to (L.ELBOW_R to L.WRIST_R),
        "brazo_i" to (L.SHOULDER_L to L.ELBOW_L), "antebrazo_i" to (L.ELBOW_L to L.WRIST_L))
    private val LEG_SEGS = listOf("muslo_d" to (L.HIP_R to L.KNEE_R), "pierna_d" to (L.KNEE_R to L.ANKLE_R),
        "muslo_i" to (L.HIP_L to L.KNEE_L), "pierna_i" to (L.KNEE_L to L.ANKLE_L))

    private fun edges(segs: List<String>, ts: List<Int> = TS) = buildSet { for (g in segs) for (t in ts) for (sd in 1..2) add(edge(g, t, sd)) }

    val HANDS: Set<String> = buildSet { for (sd in SIDES) { for (i in 0..20) add(hand(sd, i)); for (e in HAND_EDGES) add(handEdge(sd, e)) } }
    val FEET: Set<String> = buildSet { for (sd in SIDES) for (p in FOOT_PARTS) add(foot(sd, p)) }
    val ARMS: Set<String> = edges(ARM_SEGS.map { it.first })
    val LEGS: Set<String> = edges(LEG_SEGS.map { it.first })
    val TORSO: Set<String> = edges(listOf("tronco"), TORSO_TS)
    /** Alturas (%) de los bordes del cuello, de la boca a la base: por debajo de la mandíbula. */
    val NECK_TS = listOf(40, 60, 80)
    val NECK: Set<String> = edges(listOf("cuello"), NECK_TS)

    /** Claves adicionales de cada tipo de guía (para [ZoneAlignment]). */
    fun keysFor(guide: GuideType): Set<String> = when (guide) {
        GuideType.HANDS -> HANDS
        GuideType.FEET -> FEET
        GuideType.ARM -> ARMS
        GuideType.LEGS -> LEGS
        GuideType.GLUTES -> LEGS + TORSO
        GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE -> TORSO + ARMS
        GuideType.NECK -> NECK
        else -> emptySet()
    }

    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** Puntos adicionales de la zona con lo que se ve en la escena (vacío si faltan datos). */
    fun of(zone: Zone, s: ZoneCriteria.Scene): Map<String, Pt> {
        val out = HashMap<String, Pt>()
        when (zone.guide) {
            GuideType.HANDS -> s.hands?.let { hs ->
                val chosen = if (s.single) listOfNotNull(hs.maxByOrNull { d(it.points[0], it.points[9]) }) else hs.sortedBy { it.points[0].x }.take(2)
                chosen.forEachIndexed { i, h ->
                    if (h.points.size < 21) return@forEachIndexed
                    h.points.forEachIndexed { k, p -> out[hand(SIDES[i], k)] = p }
                    s.classes?.let { c -> runCatching { handEdges(c, h, SIDES[i]) }.getOrNull()?.let(out::putAll) }
                }
            }
            GuideType.FEET -> s.classes?.let { c ->
                // los dos mayores, ordenados por la posición de su punta (no por su caja, que cambia con la pierna)
                val fs = ZoneCriteria.feetOf(c).sortedByDescending { it.box.area }.take(if (s.single) 1 else 2)
                    .sortedBy { f -> f.outline.maxByOrNull { it.y }?.x ?: 0f }
                // cada pie por su mitad de la imagen (si en un cuadro se pierde o se une uno, el otro no cambia
                // de nombre); con uno solo, «a». Dos pies en la misma mitad: por el orden de su punta (antes se
                // llamaban igual y sus puntos se mezclaban)
                val sides = fs.map { f -> if ((f.outline.maxByOrNull { it.y }?.x ?: 0.5f) < 0.5f) 'a' else 'b' }
                val clash = sides.size == 2 && sides[0] == sides[1]
                fs.forEachIndexed { i, f ->
                    val side = if (s.single) 'a' else if (clash) "ab"[i] else sides[i]
                    out.putAll(footPoints(f, c, side))
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
        // cuello: sus bordes a tres alturas, de la boca (o la nariz) a la base del cuello
        if (zone.guide == GuideType.NECK) {
            val sh = listOfNotNull(p[L.SHOULDER_L], p[L.SHOULDER_R])
            val mouth = listOfNotNull(p[L.MOUTH_L], p[L.MOUTH_R]).takeIf { it.size == 2 }?.let { Pt((it[0].x + it[1].x) / 2, (it[0].y + it[1].y) / 2) } ?: p[L.NOSE]
            if (sh.size == 2 && mouth != null) {
                val base = Pt((sh[0].x + sh[1].x) / 2, (sh[0].y + sh[1].y) / 2)
                out.putAll(segmentEdges(c, "cuello", mouth, base, maxReach = d(sh[0], sh[1]) * 0.45f, ts = NECK_TS))
            }
        }
        if (zone.guide in setOf(GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE, GuideType.GLUTES)) {
            val sh = listOfNotNull(p[L.SHOULDER_L], p[L.SHOULDER_R]); val hp = listOfNotNull(p[L.HIP_L], p[L.HIP_R])
            if (sh.isNotEmpty() && hp.isNotEmpty()) {
                val a = Pt(sh.map { it.x }.average().toFloat(), sh.map { it.y }.average().toFloat())
                val b = Pt(hp.map { it.x }.average().toFloat(), hp.map { it.y }.average().toFloat())
                val wide = if (sh.size == 2) d(sh[0], sh[1]) else d(a, b) * 0.6f
                out.putAll(segmentEdges(c, "tronco", a, b, maxReach = wide * 0.9f, ts = TORSO_TS))
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
        // la punta de cada dedo, de izquierda a derecha de la imagen, solo con los cinco distinguidos (con
        // menos, el número de cada uno cambiaría entre cuadros)
        if (f.toeCount == 5 && f.toes.size == 5) f.toes.sortedBy { it.x }.forEachIndexed { i, t -> out[foot(side, "dedo_${i + 1}")] = t }
        return out
    }

    /**
     * Del contorno real de la mano ([ZoneCriteria.handOutline]): el fondo de cada espacio entre dedos
     * vecinos (pulgar–índice, índice–medio, medio–anular, anular–meñique: el punto del contorno entre los dos
     * dedos más cercano a la muñeca) y los dos bordes de la muñeca (el contorno a la altura de la muñeca, a
     * cada lado). Son puntos del borde de la piel, no del esqueleto: refuerzan la alineación donde el
     * detector de la mano solo da el eje de cada dedo.
     */
    private fun handEdges(c: ZoneCriteria.Classes, h: ZoneCriteria.Hand, side: Char): Map<String, Pt> {
        val o = ZoneCriteria.handOutline(c, h) ?: return emptyMap()
        val p = h.points
        val palm = d(p[0], p[9]).coerceAtLeast(1e-4f)
        val ux = (p[9].x - p[0].x) / palm; val uy = (p[9].y - p[0].y) / palm
        fun along(q: Pt) = (q.x - p[0].x) * ux + (q.y - p[0].y) * uy
        fun side(q: Pt, a: Pt, b: Pt) = (b.x - a.x) * (q.y - a.y) - (b.y - a.y) * (q.x - a.x)
        val out = HashMap<String, Pt>()
        // dedos vecinos: (base, punta) de cada uno
        val pairs = listOf((2 to 4) to (5 to 8), (5 to 8) to (9 to 12), (9 to 12) to (13 to 16), (13 to 16) to (17 to 20))
        pairs.forEachIndexed { i, (f1, f2) ->
            val a1 = p[f1.first]; val b1 = p[f1.second]; val a2 = p[f2.first]; val b2 = p[f2.second]
            val base = minOf(along(a1), along(a2)) - 0.15f * palm
            val v = o.filter { q -> side(q, a1, b1) * side(q, a2, b2) < 0f && along(q) > base && along(q) < maxOf(along(b1), along(b2)) }
                .minByOrNull(::along)
            v?.let { out[handEdge(side, "valle_${i + 1}")] = it }
        }
        // muñeca: el contorno a la altura del punto 0 (±0,1 palmas), a cada lado del eje
        val wr = o.filter { kotlin.math.abs(along(it)) < 0.1f * palm }
        val perp = { q: Pt -> (q.x - p[0].x) * -uy + (q.y - p[0].y) * ux }
        wr.minByOrNull(perp)?.takeIf { perp(it) < -0.2f * palm }?.let { out[handEdge(side, "muneca_1")] = it }
        wr.maxByOrNull(perp)?.takeIf { perp(it) > 0.2f * palm }?.let { out[handEdge(side, "muneca_2")] = it }
        return out
    }

    /**
     * Bordes del contorno del cuerpo (piel, ropa u otros: todo lo que no es fondo ni pelo) a ¼, ½ y ¾
     * del segmento [a]→[b], a cada lado de su eje (1: a la izquierda del sentido a→b, 2: a la derecha).
     */
    fun segmentEdges(c: ZoneCriteria.Classes, name: String, a: Pt, b: Pt, maxReach: Float = d(a, b) * 0.6f, ts: List<Int> = TS): Map<String, Pt> {
        val k = c.width.toFloat(); val len = d(a, b); if (len < 1e-3f) return emptyMap()
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        fun body(x: Float, y: Float) = c.at((x * k).toInt(), (y * k).toInt()).let { it != FaceLabels.BG && it != FaceLabels.HAIR }
        val out = HashMap<String, Pt>()
        for (t in ts) {
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
