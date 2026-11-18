package com.photomed.app.align

/**
 * Orden canónico de los contornos faciales, el mismo para ML Kit y MediaPipe (y para los puntos de
 * control de los dibujos). Cada motor numera sus contornos a su manera (p. ej. ML Kit recorre los
 * ojos empezando por el párpado superior y el labio inferior de derecha a izquierda; la malla de
 * MediaPipe agrupada no tiene por qué coincidir). Si los órdenes difieren, la correspondencia
 * punto a punto se cruza y la guía se deforma sin sentido. Aquí se reordena cada grupo por su
 * geometría:
 *
 * - lados: los grupos "_l" (izquierda del paciente) quedan a la derecha de la imagen;
 * - ojos (16): desde la comisura izquierda de la imagen, primero el párpado superior;
 * - óvalo (36): desde el punto superior central, hacia la derecha de la imagen;
 * - cejas: de fuera hacia dentro (derecha del paciente: izquierda → derecha; izquierda: al revés);
 * - labios superiores: izquierda → derecha; inferiores: derecha → izquierda; base nasal:
 *   izquierda → derecha; puente nasal: de arriba abajo.
 *
 * Coincide con el orden documentado de ML Kit (diagrama de contornos de Google) y con el de
 * `tools/eval/canonical.py`.
 */
object FaceKeys {
    private val LR_GROUPS = listOf("eye_l_" to "eye_r_", "brow_l_top_" to "brow_r_top_", "brow_l_bottom_" to "brow_r_bottom_")
    private val LR_SINGLES = listOf(Landmarks.EYE_L to Landmarks.EYE_R, Landmarks.MOUTH_L to Landmarks.MOUTH_R)

    private fun group(p: Map<String, Pt>, g: String): List<Pt>? {
        val out = ArrayList<Pt>()
        var i = 0
        while (true) { out += p["$g$i"] ?: break; i++ }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun mean(l: List<Pt>) = l.sumOf { it.x.toDouble() } / l.size

    fun canonical(points: Map<String, Pt>): Map<String, Pt> {
        if (points.keys.none { it.startsWith("oval_") || it.startsWith("eye_") || it.startsWith("lip_") || it.startsWith("brow_") }) return points
        val out = HashMap(points)
        // 1. lados: "_l" a la derecha de la imagen
        for ((l, r) in LR_GROUPS) {
            val gl = group(points, l); val gr = group(points, r)
            if (gl != null && gr != null && mean(gl) < mean(gr)) {
                gl.forEachIndexed { i, p -> out["$r$i"] = p }; gr.forEachIndexed { i, p -> out["$l$i"] = p }
            }
        }
        for ((l, r) in LR_SINGLES) {
            val pl = points[l]; val pr = points[r]
            if (pl != null && pr != null && pl.x < pr.x) { out[l] = pr; out[r] = pl }
        }
        fun put(g: String, l: List<Pt>) = l.forEachIndexed { i, p -> out["$g$i"] = p }
        group(out, "nose_bridge_")?.takeIf { it.size >= 2 && it.last().y < it.first().y }?.let { put("nose_bridge_", it.reversed()) }
        // 2. ojos: desde la comisura izquierda, párpado superior primero
        for (g in listOf("eye_l_", "eye_r_")) group(out, g)?.takeIf { it.size >= 4 }?.let { e ->
            val s = e.indices.minBy { e[it].x }
            var r = List(e.size) { e[(s + it) % e.size] }
            if (r[1].y > r[r.size - 1].y) r = listOf(r[0]) + r.drop(1).reversed()
            put(g, r)
        }
        // 3. óvalo: desde arriba al centro, hacia la derecha de la imagen
        group(out, "oval_")?.takeIf { it.size >= 8 }?.let { o ->
            // punto superior sobre la línea media de la cara: estable con la cabeza girada o inclinada
            val cx = mean(o).toFloat(); val cy = o.map { it.y }.average().toFloat()
            // línea media de la cara: puente nasal → centro del labio superior (o de la base nasal)
            val top = out["nose_bridge_0"]
            val low = out["lip_upper_top_5"] ?: out["nose_bottom_1"]
            val s: Int = (if (top != null && low != null && (top.y != low.y || top.x != low.x)) {
                var ux = top.x - low.x; var uy = top.y - low.y
                val nrm = kotlin.math.hypot(ux, uy).coerceAtLeast(1e-6f); ux /= nrm; uy /= nrm
                o.indices.filter { (o[it].x - top.x) * ux + (o[it].y - top.y) * uy > 0 }
                    .minByOrNull { kotlin.math.abs((o[it].x - top.x) * uy - (o[it].y - top.y) * ux) }
            } else null)
                ?: o.indices.filter { o[it].y < cy }.minByOrNull { kotlin.math.abs(o[it].x - cx) } ?: 0
            var r = List(o.size) { o[(s + it) % o.size] }
            if (r[1].x < r[0].x) r = listOf(r[0]) + r.drop(1).reversed()
            put("oval_", r)
        }
        // 4. curvas abiertas
        fun orient(g: String, increasingX: Boolean) = group(out, g)?.takeIf { it.size >= 2 }?.let { c ->
            if ((c.last().x > c.first().x) != increasingX) put(g, c.reversed())
        }
        orient("brow_r_top_", true); orient("brow_r_bottom_", true)
        orient("brow_l_top_", false); orient("brow_l_bottom_", false)
        orient("lip_upper_top_", true); orient("lip_upper_bottom_", true)
        orient("lip_lower_top_", false); orient("lip_lower_bottom_", false)
        orient("nose_bottom_", true)
        group(out, "nose_bridge_")?.takeIf { it.size >= 2 && it.last().y < it.first().y }?.let { put("nose_bridge_", it.reversed()) }
        return out
    }
}
