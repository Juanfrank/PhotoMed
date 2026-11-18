package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Dirección de la mirada: posición del iris dentro de cada ojo, normalizada por la mitad del ancho
 * (comisura a comisura) y la mitad de la altura (párpados) del contorno ocular; media de ambos ojos.
 * x > 0: el iris está desplazado hacia la derecha de la imagen; y > 0: hacia abajo.
 * - MediaPipe: centro del iris del modelo (puntos 468/473).
 * - ML Kit (sin iris): centroide de los píxeles oscuros dentro del contorno del ojo.
 */
object Gaze {
    private data class Eye(val center: Pt, val halfW: Float, val halfH: Float, val poly: List<Pt>)

    private fun eye(points: Map<String, Pt>, prefix: String): Eye? {
        val c = (0 until 16).mapNotNull { points["$prefix$it"] }
        if (c.size < 8) return null
        val l = c.minBy { it.x }; val r = c.maxBy { it.x }
        val top = c.minOf { it.y }; val bottom = c.maxOf { it.y }
        val halfW = hypot(r.x - l.x, r.y - l.y) / 2
        if (halfW < 1e-4f) return null
        return Eye(Pt((l.x + r.x) / 2, (l.y + r.y) / 2), halfW, ((bottom - top) / 2).coerceAtLeast(halfW * 0.25f), c)
    }

    private fun norm(e: Eye, iris: Pt) = Pt((iris.x - e.center.x) / e.halfW, (iris.y - e.center.y) / e.halfH)

    /** Con iris del modelo (MediaPipe). */
    fun fromIris(points: Map<String, Pt>): Pt? {
        val pairs = listOf("eye_l_" to Landmarks.EYE_L, "eye_r_" to Landmarks.EYE_R).mapNotNull { (pre, k) ->
            val e = eye(points, pre) ?: return@mapNotNull null
            val i = points[k] ?: return@mapNotNull null
            norm(e, i)
        }
        return avg(pairs)
    }

    /** Sin iris del modelo: centroide oscuro dentro del contorno del ojo (imagen [rgb] w×h). */
    fun fromImage(points: Map<String, Pt>, rgb: IntArray, w: Int, h: Int): Pt? {
        val pairs = listOf("eye_l_", "eye_r_").mapNotNull { pre ->
            val e = eye(points, pre) ?: return@mapNotNull null
            irisCentroid(e, rgb, w, h)?.let { norm(e, it) }
        }
        return avg(pairs)
    }

    private fun avg(p: List<Pt>): Pt? = if (p.isEmpty()) null else Pt(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat())

    private fun irisCentroid(e: Eye, rgb: IntArray, w: Int, h: Int): Pt? {
        val xs = e.poly.map { it.x * w }; val ys = e.poly.map { it.y * w }
        val x0 = xs.min().toInt().coerceAtLeast(0); val x1 = xs.max().toInt().coerceAtMost(w - 1)
        val y0 = ys.min().toInt().coerceAtLeast(0); val y1 = ys.max().toInt().coerceAtMost(h - 1)
        if (x1 - x0 < 4 || y1 - y0 < 2) return null
        val inside = ArrayList<Triple<Int, Int, Float>>()
        for (y in y0..y1) for (x in x0..x1) {
            if (!contains(xs, ys, x + 0.5f, y + 0.5f)) continue
            val c = rgb[y * w + x]
            inside += Triple(x, y, (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f)
        }
        if (inside.size < 8) return null
        val sorted = inside.map { it.third }.sorted()
        val thr = sorted[(sorted.size * 0.4f).toInt()] // 40 % más oscuro = iris/pupila (no esclera ni piel)
        var sx = 0.0; var sy = 0.0; var sw = 0.0
        for ((x, y, l) in inside) if (l <= thr) { val wt = (thr - l + 0.02f).toDouble(); sx += wt * (x + 0.5); sy += wt * (y + 0.5); sw += wt }
        if (sw <= 0) return null
        return Pt((sx / sw / w).toFloat(), (sy / sw / w).toFloat())
    }

    private fun contains(xs: List<Float>, ys: List<Float>, x: Float, y: Float): Boolean {
        var inside = false; var j = xs.size - 1
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
            j = i
        }
        return inside
    }

    const val TOL_X = 0.15f
    const val TOL_Y = 0.22f

    fun matches(a: Pt, b: Pt) = abs(a.x - b.x) <= TOL_X && abs(a.y - b.y) <= TOL_Y

    /** Diferencia de mirada entre dos fotos ([d] = después − antes), o null si coinciden. */
    fun describe(d: Pt): String? {
        val parts = buildList {
            if (abs(d.x) > TOL_X) add(if (d.x > 0) "más a la derecha" else "más a la izquierda")
            if (abs(d.y) > TOL_Y) add(if (d.y > 0) "más abajo" else "más arriba")
        }
        return if (parts.isEmpty()) null else "La mirada difiere: en “después” mira " + parts.joinToString(" y ") + " (de la imagen)"
    }

    /** Indicación para el paciente (en coordenadas de la imagen guardada). [d] = actual − referencia. */
    fun hint(d: Pt): String? {
        val parts = buildList {
            if (abs(d.x) > TOL_X) add(if (d.x > 0) "más a la izquierda" else "más a la derecha")
            if (abs(d.y) > TOL_Y) add(if (d.y > 0) "más arriba" else "más abajo")
        }
        return if (parts.isEmpty()) null else "Mirada: " + parts.joinToString(" y ")
    }
}
