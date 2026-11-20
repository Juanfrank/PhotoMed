package com.photomed.app.ui.compare

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Línea de tiempo de N fotos para la yuxtaposición progresiva: el valor t ∈ [0, N−1] recorre las
 * transiciones 1→2, 2→3, …; [segment] es la transición activa y [fraction] su avance (0 = foto
 * [segment], 1 = foto [segment] + 1).
 */
data class Timeline(val count: Int, val segment: Int, val fraction: Float) {
    val value: Float get() = segment + fraction
    val from: Int get() = segment
    val to: Int get() = min(segment + 1, count - 1)

    /** Divisor del deslizador: a la izquierda la foto [from], a la derecha [to]; avanza de derecha a izquierda. */
    val dividerPosition: Float get() = 1f - fraction

    /** Mueve el divisor dentro de la transición activa (no cambia de transición a mitad del gesto). */
    fun withDivider(position: Float) = copy(fraction = (1f - position).coerceIn(0f, 1f))

    companion object {
        const val MAX_PHOTOS = 12

        fun of(count: Int, t: Float): Timeline {
            if (count < 2) return Timeline(count.coerceAtLeast(0), 0, 0f)
            val v = t.coerceIn(0f, (count - 1).toFloat())
            val seg = min(floor(v).toInt(), count - 2)
            return Timeline(count, seg, v - seg)
        }
    }
}

/** Columnas de la cuadrícula que maximizan el tamaño de celdas 3:4 en un área [w]×[h]. */
fun gridColumns(n: Int, w: Float, h: Float, aspect: Float = 3f / 4f): Int {
    if (n <= 1) return 1
    var best = 1
    var bestCell = 0f
    for (c in 1..n) {
        val rows = ceil(n / c.toFloat())
        val cellW = min(w / c, h / rows * aspect)
        if (cellW > bestCell + 0.01f) { bestCell = cellW; best = c }
    }
    return best
}

/** Orden en el que se seleccionan fotos: añade al final o quita; con más del máximo descarta la más antigua elegida. */
fun toggleSelection(selected: List<String>, key: String, max: Int = Timeline.MAX_PHOTOS): List<String> = when {
    key in selected -> selected - key
    selected.size >= max -> selected.drop(1) + key
    else -> selected + key
}

/**
 * Zona de la foto "después" que cubre el encuadre de la referencia tras la alineación: las esquinas
 * de la foto original → corrección de perspectiva [phiDeg] → semejanza [t] (unidades del ancho,
 * encuadre 1 × 4/3).
 */
fun footprint(t: com.photomed.app.align.Similarity?, phiDeg: Float): List<com.photomed.app.align.Pt> =
    listOf(0f to 0f, 1f to 0f, 1f to 4f / 3f, 0f to 4f / 3f).map { (x, y) ->
        val p = com.photomed.app.align.Perspective.mapPt(com.photomed.app.align.Pt(x, y), phiDeg)
        t?.apply(p) ?: p
    }

/**
 * Recorte según la alineación: el mayor rectángulo 3:4 dentro del encuadre y de la zona que cubren
 * TODAS las fotos alineadas ([polys], convexos), para que ninguna muestre bordes negros. Devuelve la
 * semejanza que lleva ese rectángulo al encuadre completo (escala ≥ 1), o null si no hace falta
 * recortar (o no queda área útil).
 */
fun overlapCrop(polys: List<List<com.photomed.app.align.Pt>>): com.photomed.app.align.Similarity? {
    val frameH = 4f / 3f
    fun inside(x: Float, y: Float): Boolean {
        if (x < -1e-4f || y < -1e-4f || x > 1f + 1e-4f || y > frameH + 1e-4f) return false
        for (poly in polys) {
            var sign = 0
            for (i in poly.indices) {
                val a = poly[i]; val b = poly[(i + 1) % poly.size]
                val cr = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
                val s = if (cr > 1e-6f) 1 else if (cr < -1e-6f) -1 else 0
                if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
            }
        }
        return true
    }
    fun fits(cx: Float, cy: Float, w: Float): Boolean {
        val h = w * 4f / 3f
        return inside(cx - w / 2, cy - h / 2) && inside(cx + w / 2, cy - h / 2) &&
            inside(cx + w / 2, cy + h / 2) && inside(cx - w / 2, cy + h / 2)
    }
    var best = 0f; var bx = 0.5f; var by = frameH / 2
    val n = 12
    for (i in 0..n) for (j in 0..n) {
        val cx = 0.2f + 0.6f * i / n; val cy = frameH * (0.2f + 0.6f * j / n)
        if (!fits(cx, cy, best + 1e-3f)) continue
        var lo = best; var hi = 1f
        repeat(18) { val m = (lo + hi) / 2; if (fits(cx, cy, m)) lo = m else hi = m }
        if (lo > best) { best = lo; bx = cx; by = cy }
    }
    if (best < 0.2f || best > 0.995f) return null
    val k = 1f / best
    val x0 = bx - best / 2; val y0 = by - best * 2f / 3f
    return com.photomed.app.align.Similarity(k, 0f, -k * x0, -k * y0)
}

/**
 * Etiquetas de fecha de la comparación: la hora y minuto se añaden solo a las fotos que comparten
 * día con otra de la selección (para distinguirlas); las demás, solo la fecha.
 */
fun stampLabels(times: List<Long>): List<String> {
    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
    val days = times.map { day.format(java.util.Date(it)) }
    return times.mapIndexed { i, t ->
        if (days.count { it == days[i] } > 1) com.photomed.app.ui.formatDateTime(t) else com.photomed.app.ui.formatDate(t)
    }
}

/** Orden de las fotos en la cuadrícula y lado a lado (solo presentación: no cambia la referencia). */
enum class GridOrder(val label: String) { OLDEST_FIRST("Fecha: antigua primero"), NEWEST_FIRST("Fecha: reciente primero"), MANUAL("Manual (arrastrar)") }

/** Claves en el orden de presentación; en manual, las nuevas se añaden al final. */
fun displayOrder(seqByDate: List<String>, order: GridOrder, manual: List<String>): List<String> = when (order) {
    GridOrder.OLDEST_FIRST -> seqByDate
    GridOrder.NEWEST_FIRST -> seqByDate.reversed()
    GridOrder.MANUAL -> manual.filter { it in seqByDate } + seqByDate.filter { it !in manual }
}

/** Mueve el elemento [from] a la posición [to] (arrastrar para reordenar). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices) return this
    val m = toMutableList(); val x = m.removeAt(from); m.add(to.coerceIn(0, m.size), x); return m
}
