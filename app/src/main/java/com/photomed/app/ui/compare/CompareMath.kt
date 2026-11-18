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
fun gridColumns(n: Int, w: Float, h: Float): Int {
    if (n <= 1) return 1
    var best = 1
    var bestCell = 0f
    for (c in 1..n) {
        val rows = ceil(n / c.toFloat())
        val cellW = min(w / c, h / rows * 3f / 4f)
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
