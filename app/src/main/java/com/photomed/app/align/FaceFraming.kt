package com.photomed.app.align

/**
 * ¿Coincide el rostro detectado con el marco de la guía? Compara el óvalo facial del cuadro con el
 * óvalo del dibujo de la guía (sus puntos de control): centro y ancho, en coordenadas del encuadre
 * normalizadas (x por el ancho, y por el alto). Si coincide, la guía de la primera toma (sin foto
 * de referencia) se adapta a la cara del paciente (cráneo, mentón, ojos, boca…).
 * Con histéresis para que no parpadee en el límite.
 */
object FaceFraming {
    const val MAX_SHIFT = 0.05f
    const val MAX_SCALE = 0.12f
    const val RELEASE = 1.6f

    data class Fit(val dx: Float, val dy: Float, val scale: Float) {
        fun within(k: Float) = kotlin.math.abs(dx) <= MAX_SHIFT * k && kotlin.math.abs(dy) <= MAX_SHIFT * k &&
            kotlin.math.abs(scale - 1f) <= MAX_SCALE * k
    }

    /** Óvalo del dibujo: [controls] (x, y normalizados por ancho y alto) en el orden de las claves de control. */
    fun of(controls: FloatArray, keys: Array<String>, points: Map<String, Pt>): Fit? {
        val artOval = (0 until 36).mapNotNull { i ->
            val k = keys.indexOf("oval_$i").takeIf { it >= 0 } ?: return@mapNotNull null
            val x = controls.getOrNull(2 * k) ?: return@mapNotNull null; val y = controls.getOrNull(2 * k + 1) ?: return@mapNotNull null
            if (x.isNaN() || y.isNaN()) null else Pt(x, y)
        }
        val p = FaceKeys.canonical(points)
        val live = (0 until 36).mapNotNull { p["oval_$it"] }.map { Pt(it.x, it.y * 0.75f) }  // y del ancho → del alto
        if (artOval.size < 30 || live.size < 30) return null
        fun box(l: List<Pt>) = floatArrayOf(l.minOf { it.x }, l.minOf { it.y }, l.maxOf { it.x }, l.maxOf { it.y })
        val a = box(artOval); val b = box(live)
        val aw = a[2] - a[0]; val bw = b[2] - b[0]
        if (aw <= 1e-4f) return null
        return Fit((b[0] + b[2] - a[0] - a[2]) / 2, (b[1] + b[3] - a[1] - a[3]) / 2, bw / aw)
    }

    /** Coincidencia con histéresis: entra con las tolerancias y sale al superarlas [RELEASE] veces. */
    fun matches(fit: Fit?, wasMatched: Boolean): Boolean = fit != null && fit.within(if (wasMatched) RELEASE else 1f)
}
