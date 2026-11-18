package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Estimación de la distorsión vertical (keystone) entre dos fotos a partir de las proporciones
 * de los contornos, sin sensores:
 *
 * 1. **Puntos de contorno** (p. ej. los 133 del contorno facial): para cada φ candidato se corrige
 *    la foto "después", se ajusta la semejanza a "antes" y se mide el residuo; se elige el φ de
 *    menor residuo (búsqueda en rejilla + refinamiento por sección áurea). Solo se acepta si mejora
 *    claramente el ajuste sin corrección (evita sobreajuste).
 * 2. **Proporciones de la silueta** (óvalo facial, segmentación del sujeto o varita mágica): para
 *    cada φ candidato se transforma el contorno de "después", se recalcula su perfil de anchos
 *    normalizado por su altura (invariante a la escala) y se compara con el de "antes"; se elige el
 *    φ que mejor iguala las proporciones (p. ej. frente / pómulos / mandíbula).
 */
object KeystoneFit {
    const val RANGE = 15f

    data class Result(val keystoneDeg: Float, val similarity: Similarity, val rms: Float, val rmsWithout: Float)

    fun fromLandmarks(before: List<Pt>, after: List<Pt>, focalNorm: Float = Perspective.DEFAULT_FOCAL): Result? {
        if (before.size < 6) return null
        val ys = before.map { it.y }
        if ((ys.maxOrNull()!! - ys.minOrNull()!!) < 0.15f) return null // poca extensión vertical
        fun fit(phi: Float): Pair<Similarity, Float>? {
            val a = after.map { Perspective.mapPt(it, phi, focalNorm) }
            val t = Similarity.estimate(a, before) ?: return null
            return t to Similarity.rms(t, a, before)
        }
        val base = fit(0f) ?: return null
        var bestPhi = 0f; var bestRms = base.second
        var phi = -RANGE
        while (phi <= RANGE) {
            fit(phi)?.let { if (it.second < bestRms) { bestRms = it.second; bestPhi = phi } }
            phi += 1f
        }
        // sección áurea en ±1°
        var lo = bestPhi - 1f; var hi = bestPhi + 1f
        val g = 0.618034f
        repeat(30) {
            val m1 = hi - g * (hi - lo); val m2 = lo + g * (hi - lo)
            val r1 = fit(m1)?.second ?: Float.MAX_VALUE; val r2 = fit(m2)?.second ?: Float.MAX_VALUE
            if (r1 < r2) hi = m2 else lo = m1
        }
        val phiBest = (lo + hi) / 2
        val best = fit(phiBest) ?: return null
        // aceptar solo si reduce el residuo al menos un 10 %
        return if (best.second < base.second * 0.9f) Result(phiBest, best.first, best.second, base.second)
        else Result(0f, base.first, base.second, base.second)
    }

    /**
     * Perfil de anchos del polígono del contorno, exacto (intersección de cada horizontal con las
     * aristas), normalizado por su altura: invariante a escala y posición.
     */
    fun normProfile(outline: List<Pt>, n: Int = 61): FloatArray? {
        if (outline.size < 3) return null
        val top = outline.minOf { it.y }; val bottom = outline.maxOf { it.y }
        val hgt = bottom - top
        if (hgt <= 1e-4f) return null
        return FloatArray(n) { k ->
            val y = top + hgt * (0.02f + 0.96f * k / (n - 1))
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (i in outline.indices) {
                val a = outline[i]; val b = outline[(i + 1) % outline.size]
                if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
                    val x = a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
                    if (x < lo) lo = x; if (x > hi) hi = x
                }
            }
            if (hi >= lo) (hi - lo) / hgt else 0f
        }
    }

    private fun profileError(a: FloatArray, b: FloatArray): Float {
        // se ignoran los extremos (vértice y mentón), donde el ancho tiende a cero
        var e = 0f; var n = 0
        for (i in 4 until a.size - 4) { val d = a[i] - b[i]; e += d * d; n++ }
        return e / n
    }

    /** φ que iguala las proporciones del contorno "después" con las de "antes" (trazados normalizados). */
    fun fromOutlines(before: List<Pt>, after: List<Pt>, focalNorm: Float = Perspective.DEFAULT_FOCAL): Float? {
        if (before.size < 20 || after.size < 20) return null
        val pb = normProfile(before) ?: return null
        fun err(phi: Float): Float =
            normProfile(after.map { Perspective.mapPt(it, phi, focalNorm) })?.let { profileError(it, pb) } ?: Float.MAX_VALUE
        val e0 = err(0f)
        var best = 0f; var bestE = e0
        var phi = -RANGE
        while (phi <= RANGE) { val e = err(phi); if (e < bestE) { bestE = e; best = phi }; phi += 1f }
        var lo = best - 1f; var hi = best + 1f
        val g = 0.618034f
        repeat(20) {
            val m1 = hi - g * (hi - lo); val m2 = lo + g * (hi - lo)
            if (err(m1) < err(m2)) hi = m2 else lo = m1
        }
        val phiBest = (lo + hi) / 2
        return if (err(phiBest) < e0 * 0.8f) phiBest else 0f
    }

    fun rmsOf(values: List<Float>) = sqrt(values.sumOf { (it * it).toDouble() } / values.size).toFloat()
    fun significant(deg: Float) = abs(deg) >= 0.5f
}
