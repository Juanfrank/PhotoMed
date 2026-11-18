package com.photomed.app.ui.camera

import androidx.compose.ui.geometry.Offset
import com.photomed.app.align.HeadModel
import com.photomed.app.align.HeadPose
import com.photomed.app.align.Pt
import kotlin.math.ln

/**
 * Deformación de la guía del rostro **rasgo a rasgo**: thin-plate spline (Bookstein, 1989) entre
 * ~140 puntos de control de la ilustración y los mismos puntos en la foto de referencia.
 *
 * - Ojos (16 puntos por ojo + pupila), cejas, nariz, labios (38 puntos) y óvalo (36 puntos: frente,
 *   sienes, mejillas, mandíbula, mentón) se llevan cada uno a su posición: posición, amplitud y
 *   altura de cada rasgo, inclinación y asimetrías.
 * - Orejas (borde superior, hélice, lóbulo) y vértice del cráneo, que los detectores no dan: se
 *   proyectan con la pose 3D de la cabeza ([HeadPose]) desde su posición en el dibujo, así siguen
 *   el giro, la inclinación y el cabeceo.
 *
 * Los puntos de control de la ilustración salen de la malla de MediaPipe detectada sobre el propio
 * dibujo (`tools/guides/face_controls.py`). Coordenadas: x del ancho, y del alto del encuadre 3:4;
 * internamente en unidades del ancho (isótropas).
 */
class FaceMeshWarp private constructor(
    private val src: Array<Pt>,
    private val wx: DoubleArray, private val wy: DoubleArray,
    private val ax: DoubleArray, private val ay: DoubleArray,
    val controlCount: Int,
) {
    private fun u(r2: Double) = if (r2 < 1e-12) 0.0 else r2 * ln(r2) / 2   // r²·ln r

    /** Punto de la ilustración (x del ancho, y del alto) → encuadre de la foto. */
    fun map(o: Offset): Offset {
        val x = o.x.toDouble(); val y = o.y * H.toDouble()
        var fx = ax[0] + ax[1] * x + ax[2] * y
        var fy = ay[0] + ay[1] * x + ay[2] * y
        for (i in src.indices) {
            val dx = x - src[i].x; val dy = y - src[i].y
            val k = u(dx * dx + dy * dy)
            fx += wx[i] * k; fy += wy[i] * k
        }
        return Offset(fx.toFloat(), (fy / H).toFloat())
    }

    /**
     * Fracción de celdas plegadas (orientación invertida) dentro del óvalo facial del dibujo: con
     * detecciones muy ruidosas o cabezas muy giradas la malla puede cruzarse; entonces conviene el
     * ajuste por franjas, más robusto.
     */
    fun foldFraction(keys: Array<String>, art: FloatArray, n: Int = 24): Float {
        val oval = (0 until 36).mapNotNull { i -> keys.indexOf("oval_$i").takeIf { it >= 0 && !art[it * 2].isNaN() }?.let { Offset(art[it * 2], art[it * 2 + 1]) } }
        if (oval.size < 8) return 0f
        fun inside(x: Float, y: Float): Boolean {
            var ins = false; var j = oval.size - 1
            for (i in oval.indices) { val a = oval[i]; val b = oval[j]
                if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) ins = !ins; j = i }
            return ins
        }
        var bad = 0; var all = 0
        for (j in 0 until n) for (i in 0 until n) {
            val x = i / n.toFloat(); val y = j / n.toFloat()
            if (!inside(x + 0.5f / n, y + 0.5f / n)) continue
            all++
            val p00 = map(Offset(x, y)); val p10 = map(Offset(x + 1f / n, y)); val p01 = map(Offset(x, y + 1f / n))
            if ((p10.x - p00.x) * (p01.y - p00.y) - (p10.y - p00.y) * (p01.x - p00.x) <= 0f) bad++
        }
        return if (all == 0) 0f else bad / all.toFloat()
    }

    /** Vértices de una malla [cols]×[rows] sobre la ilustración, en px del destino (para drawBitmapMesh). */
    fun meshVertices(cols: Int, rows: Int, width: Float, height: Float): FloatArray {
        val v = FloatArray((cols + 1) * (rows + 1) * 2)
        var k = 0
        for (j in 0..rows) for (i in 0..cols) {
            val m = map(Offset(i / cols.toFloat(), j / rows.toFloat()))
            v[k++] = m.x * width; v[k++] = m.y * height
        }
        return v
    }

    companion object {
        private const val H = 4f / 3f
        const val MIN_CONTROLS = 24
        private val SINGLES = setOf("eye_l", "eye_r", "mouth_l", "mouth_r", "nose_base")

        /**
         * Correspondencias ilustración → foto. [art]: puntos de control normalizados en el orden de
         * [keys]; [points]: puntos detectados en la foto (unidades del ancho, como los detectores).
         */
        fun fit(keys: Array<String>, art: FloatArray, points: Map<String, Pt>, lambda: Double = 1e-2): FaceMeshWarp? {
            val canon = com.photomed.app.align.FaceKeys.canonical(points)
            val pose = runCatching { HeadPose.fit(canon) }.getOrNull()
            // línea media de los labios (boca abierta o cerrada), como en los controles del dibujo;
            // en orden canónico el labio inferior va de derecha a izquierda
            val pts = HashMap(canon)
            for (i in 0 until 9) {
                val a = canon["lip_upper_bottom_$i"]; val b = canon["lip_lower_top_${8 - i}"]
                if (a != null && b != null) pts["lip_mid_$i"] = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
            }
            val s = ArrayList<Pt>(); val d = ArrayList<Pt>()
            // Orejas y cráneo proyectados con la pose: con la cabeza girada, los del lado lejano caen
            // sobre la cara (están ocultos) y plegarían la malla: solo se usan los que quedan fuera
            // del óvalo facial (visibles).
            val oval = (0 until 36).mapNotNull { canon["oval_$it"] }
            fun insideOval(p: Pt): Boolean {
                if (oval.size < 8) return false
                var inside = false; var j = oval.size - 1
                for (i in oval.indices) {
                    val a = oval[i]; val b = oval[j]
                    if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
                    j = i
                }
                return inside
            }
            val hiddenEar = listOf("ear_r_", "ear_l_").filter { side ->
                val out = pose?.let { p -> HeadModel.POINTS["${side}out"]?.let(p::project) }
                out == null || insideOval(out)
            }.toSet()
            // Óvalo del lado lejano con la cabeza girada: sus puntos 3D proyectados quedan por dentro
            // de la cola de la ceja o del ojo (están ocultos tras la mejilla) y cruzarían la malla.
            val feats = canon.filterKeys { it.startsWith("eye_") || it.startsWith("brow_") || it.startsWith("lip_") || it.startsWith("nose_") }.values
            val cx = feats.map { it.x }.average().toFloat()
            fun occludedOval(p: Pt): Boolean {
                val band = feats.filter { kotlin.math.abs(it.y - p.y) < 0.04f }
                if (band.isEmpty()) return false
                return if (p.x < cx) p.x > band.minOf { it.x } - 0.01f else p.x < band.maxOf { it.x } + 0.01f
            }
            for ((i, k) in keys.withIndex()) {
                if (hiddenEar.any { k.startsWith(it) }) continue
                if (k.startsWith("oval_")) { val q = pts[k]; if (q != null && occludedOval(q)) continue }
                // Puntos sueltos (pupilas, comisuras, base nasal): repiten o están dentro de un
                // contorno; su ruido (y la diferencia pupila/centro del ojo entre motores) curvaría
                // la malla. Los contornos ya fijan esos rasgos.
                if (k in SINGLES) continue
                val target: Pt = pts[k]
                    ?: (if (k.startsWith("ear_") || k.startsWith("skull_")) pose?.let { p -> HeadModel.POINTS[k]?.let(p::project) } else null)
                    ?: (if (k == "vertex") pose?.let(HeadPose::vertex) else null)
                    ?: continue
                if (art[i * 2].isNaN()) continue   // punto que no existe en este dibujo (p. ej. oreja oculta)
                if (k.startsWith("skull_") && insideOval(target)) continue
                val a = Pt(art[i * 2], art[i * 2 + 1] * H)
                // puntos repetidos en la ilustración (p. ej. comisuras compartidas): solo el primero
                if (s.any { kotlin.math.abs(it.x - a.x) < 1e-4f && kotlin.math.abs(it.y - a.y) < 1e-4f }) continue
                s += a; d += target
            }
            if (s.size < MIN_CONTROLS) return null
            return solve(s.toTypedArray(), d.toTypedArray(), lambda)
        }

        /** Thin-plate spline con regularización [lambda] (suaviza el ruido de la detección). */
        fun solve(src: Array<Pt>, dst: Array<Pt>, lambda: Double): FaceMeshWarp? {
            val n = src.size; val m = n + 3
            val a = Array(m) { DoubleArray(m) }
            val bx = DoubleArray(m); val by = DoubleArray(m)
            // escala del problema para que λ no dependa del tamaño
            var mean = 0.0
            for (i in 0 until n) for (j in 0 until n) {
                val dx = (src[i].x - src[j].x).toDouble(); val dy = (src[i].y - src[j].y).toDouble()
                mean += kotlin.math.sqrt(dx * dx + dy * dy)
            }
            mean /= (n * n).toDouble()
            for (i in 0 until n) {
                for (j in 0 until n) {
                    val dx = (src[i].x - src[j].x).toDouble(); val dy = (src[i].y - src[j].y).toDouble()
                    val r2 = dx * dx + dy * dy
                    a[i][j] = if (r2 < 1e-12) 0.0 else r2 * ln(r2) / 2
                }
                a[i][i] += lambda * mean * mean
                a[i][n] = 1.0; a[i][n + 1] = src[i].x.toDouble(); a[i][n + 2] = src[i].y.toDouble()
                a[n][i] = 1.0; a[n + 1][i] = src[i].x.toDouble(); a[n + 2][i] = src[i].y.toDouble()
                bx[i] = dst[i].x.toDouble(); by[i] = dst[i].y.toDouble()
            }
            val solX = gauss(a.map { it.copyOf() }.toTypedArray(), bx) ?: return null
            val solY = gauss(a.map { it.copyOf() }.toTypedArray(), by) ?: return null
            return FaceMeshWarp(src, solX.copyOf(n), solY.copyOf(n),
                doubleArrayOf(solX[n], solX[n + 1], solX[n + 2]), doubleArrayOf(solY[n], solY[n + 1], solY[n + 2]), n)
        }

        private fun gauss(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            for (c in 0 until n) {
                var p = c
                for (r in c + 1 until n) if (kotlin.math.abs(a[r][c]) > kotlin.math.abs(a[p][c])) p = r
                if (kotlin.math.abs(a[p][c]) < 1e-14) return null
                val t = a[c]; a[c] = a[p]; a[p] = t
                val tb = b[c]; b[c] = b[p]; b[p] = tb
                for (r in c + 1 until n) {
                    val f = a[r][c] / a[c][c]
                    if (f == 0.0) continue
                    for (k in c until n) a[r][k] -= f * a[c][k]
                    b[r] -= f * b[c]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var s = b[r]
                for (k in r + 1 until n) s -= a[r][k] * x[k]
                x[r] = s / a[r][r]
            }
            return x
        }
    }
}
