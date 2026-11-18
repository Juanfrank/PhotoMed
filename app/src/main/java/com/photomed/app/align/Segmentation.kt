package com.photomed.app.align

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Máscara binaria (fila mayor). */
class Mask(val width: Int, val height: Int, val data: BooleanArray = BooleanArray(width * height)) {
    operator fun get(x: Int, y: Int) = x in 0 until width && y in 0 until height && data[y * width + x]
    val area: Int get() = data.count { it }
}

/**
 * Selección tipo "varita mágica": crecimiento de región desde un punto semilla.
 * - Similitud de color en CIELAB (ΔE) respecto de la media de la región, que se actualiza al
 *   crecer (se adapta a degradados suaves de la piel).
 * - Los bordes fuertes (gradiente) frenan el crecimiento, como el "ajuste a bordes" de los
 *   editores; así la selección sigue contornos (mandíbula, labios, silueta…).
 * - Limpieza: relleno de huecos y apertura/cierre morfológicos.
 */
object MagicWand {
    fun select(rgb: IntArray, w: Int, h: Int, seedX: Int, seedY: Int, tolerance: Float = 12f, edgeStop: Float = 0.35f): Mask {
        val lab = toLab(rgb)
        val grad = gradient(rgb, w, h)
        val gMax = grad.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val mask = Mask(w, h)
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        val s = seedY.coerceIn(0, h - 1) * w + seedX.coerceIn(0, w - 1)
        queue[tail++] = s; seen[s] = true
        var mL = 0.0; var mA = 0.0; var mB = 0.0; var n = 0
        // media inicial: vecindad 5×5 de la semilla
        for (dy in -2..2) for (dx in -2..2) {
            val x = (s % w + dx).coerceIn(0, w - 1); val y = (s / w + dy).coerceIn(0, h - 1); val i = y * w + x
            mL += lab[3 * i]; mA += lab[3 * i + 1]; mB += lab[3 * i + 2]; n++
        }
        mL /= n; mA /= n; mB /= n; n = 1
        while (head < tail) {
            val i = queue[head++]
            val dL = lab[3 * i] - mL; val dA = lab[3 * i + 1] - mA; val dB = lab[3 * i + 2] - mB
            if (sqrt(dL * dL + dA * dA + dB * dB) > tolerance || grad[i] / gMax > edgeStop && i != s) continue
            mask.data[i] = true
            n++
            mL += (lab[3 * i] - mL) / n; mA += (lab[3 * i + 1] - mA) / n; mB += (lab[3 * i + 2] - mB) / n
            val x = i % w; val y = i / w
            if (x > 0 && !seen[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1 }
            if (x < w - 1 && !seen[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1 }
            if (y > 0 && !seen[i - w]) { seen[i - w] = true; queue[tail++] = i - w }
            if (y < h - 1 && !seen[i + w]) { seen[i + w] = true; queue[tail++] = i + w }
        }
        return MaskOps.clean(mask, radius = max(1, min(w, h) / 120))
    }

    private fun toLab(rgb: IntArray): DoubleArray {
        val out = DoubleArray(rgb.size * 3)
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        for (i in rgb.indices) {
            val r = lin((rgb[i] shr 16) and 0xFF); val g = lin((rgb[i] shr 8) and 0xFF); val b = lin(rgb[i] and 0xFF)
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            val fx = f(x); val fy = f(y); val fz = f(z)
            out[3 * i] = 116 * fy - 16; out[3 * i + 1] = 500 * (fx - fy); out[3 * i + 2] = 200 * (fy - fz)
        }
        return out
    }

    private fun gradient(rgb: IntArray, w: Int, h: Int): FloatArray {
        val l = FloatArray(rgb.size) { i ->
            val c = rgb[i]; (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF))
        }
        val g = FloatArray(rgb.size)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = l[i + 1] - l[i - 1] + 0.5f * (l[i - w + 1] - l[i - w - 1] + l[i + w + 1] - l[i + w - 1])
            val gy = l[i + w] - l[i - w] + 0.5f * (l[i + w - 1] - l[i - w - 1] + l[i + w + 1] - l[i - w + 1])
            g[i] = sqrt(gx * gx + gy * gy)
        }
        return g
    }
}

object MaskOps {
    /** Rasteriza un polígono (coordenadas normalizadas por el ancho) — p. ej. el óvalo facial de ML Kit. */
    fun polygon(points: List<Pt>, w: Int, h: Int): Mask {
        val m = Mask(w, h)
        if (points.size < 3) return m
        val px = points.map { it.x * w }; val py = points.map { it.y * w }
        for (y in 0 until h) {
            val yc = y + 0.5f
            val xs = ArrayList<Float>()
            for (i in points.indices) {
                val j = (i + 1) % points.size
                val y1 = py[i]; val y2 = py[j]
                if ((y1 <= yc && y2 > yc) || (y2 <= yc && y1 > yc)) xs += px[i] + (yc - y1) / (y2 - y1) * (px[j] - px[i])
            }
            xs.sort()
            var k = 0
            while (k + 1 < xs.size) {
                for (x in max(0, xs[k].toInt())..min(w - 1, xs[k + 1].toInt())) m.data[y * w + x] = true
                k += 2
            }
        }
        return m
    }

    fun clean(m: Mask, radius: Int): Mask = largestComponent(fillHoles(close(open(m, radius), radius)))

    fun dilate(m: Mask, r: Int): Mask = morph(m, r, true)
    fun erode(m: Mask, r: Int): Mask = morph(m, r, false)
    fun open(m: Mask, r: Int) = dilate(erode(m, r), r)
    fun close(m: Mask, r: Int) = erode(dilate(m, r), r)

    private fun morph(m: Mask, r: Int, dil: Boolean): Mask {
        // separable (cuadrado), suficiente para limpieza
        val w = m.width; val h = m.height
        val tmp = BooleanArray(w * h); val out = Mask(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var v = !dil
            for (k in -r..r) { val xx = x + k; val b = xx in 0 until w && m.data[y * w + xx]; if (dil && b) { v = true; break }; if (!dil && !b) { v = false; break } }
            tmp[y * w + x] = v
        }
        for (y in 0 until h) for (x in 0 until w) {
            var v = !dil
            for (k in -r..r) { val yy = y + k; val b = yy in 0 until h && tmp[yy * w + x]; if (dil && b) { v = true; break }; if (!dil && !b) { v = false; break } }
            out.data[y * w + x] = v
        }
        return out
    }

    fun fillHoles(m: Mask): Mask {
        val w = m.width; val h = m.height
        val outside = BooleanArray(w * h); val q = ArrayDeque<Int>()
        for (x in 0 until w) { q += x; q += (h - 1) * w + x }
        for (y in 0 until h) { q += y * w; q += y * w + w - 1 }
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            if (outside[i] || m.data[i]) continue
            outside[i] = true
            val x = i % w; val y = i / w
            if (x > 0) q += i - 1; if (x < w - 1) q += i + 1; if (y > 0) q += i - w; if (y < h - 1) q += i + w
        }
        return Mask(w, h, BooleanArray(w * h) { !outside[it] })
    }

    fun largestComponent(m: Mask): Mask {
        val w = m.width; val h = m.height
        val label = IntArray(w * h); var best = 0; var bestSize = 0; var next = 0
        val stack = IntArray(w * h)
        for (s in 0 until w * h) {
            if (!m.data[s] || label[s] != 0) continue
            next++; var sp = 0; stack[sp++] = s; label[s] = next; var size = 0
            while (sp > 0) {
                val i = stack[--sp]; size++
                val x = i % w; val y = i / w
                for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                    if (j >= 0 && m.data[j] && label[j] == 0) { label[j] = next; stack[sp++] = j }
                }
            }
            if (size > bestSize) { bestSize = size; best = next }
        }
        return Mask(w, h, BooleanArray(w * h) { label[it] == best && best != 0 })
    }
}

/** Dimensiones de un contorno seleccionado (normalizadas por el ancho de la imagen). */
data class ContourMetrics(
    val top: Float, val bottom: Float, val left: Float, val right: Float,
    val height: Float, val maxWidth: Float, val area: Float,
    val centroid: Pt,
    /** Anchos a 25 %, 50 % y 75 % de la altura (p. ej. frente, pómulos, mandíbula en el rostro). */
    val widthUpper: Float, val widthMiddle: Float, val widthLower: Float,
    /** Perfil de anchos en [PROFILE_N] alturas equiespaciadas (de arriba abajo). */
    val widthProfile: FloatArray,
    /** Contorno exterior (polilínea cerrada) para dibujar. */
    val outline: List<Pt>,
) {
    val aspect: Float get() = if (height > 0f) maxWidth / height else 0f
    /** Proporción tercio superior / inferior de anchos (sensible a la perspectiva vertical). */
    val upperLowerRatio: Float get() = if (widthLower > 0f) widthUpper / widthLower else 0f

    companion object {
        const val PROFILE_N = 41

        fun of(m: Mask): ContourMetrics? {
            val w = m.width; val h = m.height
            var top = h; var bottom = -1; var left = w; var right = -1; var cnt = 0; var sx = 0.0; var sy = 0.0
            val rowL = IntArray(h) { w }; val rowR = IntArray(h) { -1 }
            for (y in 0 until h) for (x in 0 until w) if (m.data[y * w + x]) {
                cnt++; sx += x; sy += y
                top = min(top, y); bottom = max(bottom, y); left = min(left, x); right = max(right, x)
                rowL[y] = min(rowL[y], x); rowR[y] = max(rowR[y], x)
            }
            if (cnt < 20) return null
            val fw = w.toFloat()
            fun widthAt(f: Float): Float {
                val y = (top + f * (bottom - top)).toInt().coerceIn(0, h - 1)
                return if (rowR[y] >= rowL[y]) (rowR[y] - rowL[y] + 1) / fw else 0f
            }
            var maxW = 0
            for (y in top..bottom) if (rowR[y] >= rowL[y]) maxW = max(maxW, rowR[y] - rowL[y] + 1)
            return ContourMetrics(
                top / fw, bottom / fw, left / fw, right / fw,
                (bottom - top + 1) / fw, maxW / fw, cnt / (fw * fw),
                Pt((sx / cnt / fw).toFloat(), (sy / cnt / fw).toFloat()),
                widthAt(0.25f), widthAt(0.5f), widthAt(0.75f),
                FloatArray(PROFILE_N) { widthAt(it / (PROFILE_N - 1f)) },
                trace(m).map { Pt(it.first / fw, it.second / fw) },
            )
        }

        /** Trazado del borde exterior (vecindad de Moore), simplificado a ~400 puntos. */
        fun trace(m: Mask): List<Pair<Int, Int>> {
            val w = m.width; val h = m.height
            var start = -1
            loop@ for (y in 0 until h) for (x in 0 until w) if (m.data[y * w + x]) { start = y * w + x; break@loop }
            if (start < 0) return emptyList()
            val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1); val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
            val out = ArrayList<Pair<Int, Int>>()
            var cx = start % w; var cy = start / w; var dir = 7
            val limit = 4 * (w + h) * 4
            do {
                out += cx to cy
                var found = false
                for (k in 0 until 8) {
                    val d = (dir + 6 + k) % 8 // empezar a la izquierda de la dirección previa
                    val nx = cx + dx[d]; val ny = cy + dy[d]
                    if (m[nx, ny]) { cx = nx; cy = ny; dir = d; found = true; break }
                }
                if (!found || out.size > limit) break
            } while (cx + cy * w != start)
            val step = max(1, out.size / 400)
            return out.filterIndexed { i, _ -> i % step == 0 }
        }
    }
}


/**
 * Sujeto sobre fondo liso (manos, pies aislados, donde la segmentación de personas falla):
 * sustracción de fondo por color. El fondo se modela con los píxeles del borde de la imagen
 * (mediana en CIELAB y su dispersión, MAD); es sujeto todo píxel a una distancia ΔE mayor que
 * max(12, 4·MAD) del color del fondo. Después: apertura/cierre, relleno de huecos y componente
 * mayor. Null si el fondo no es liso (dispersión alta) o el resultado no es plausible.
 */
object ForegroundOnPlain {
    fun mask(rgb: IntArray, w: Int, h: Int): Mask? {
        val lab = DoubleArray(3)
        val bw = maxOf(2, (minOf(w, h) * 0.03f).toInt())
        val border = ArrayList<DoubleArray>()
        for (y in 0 until h) for (x in 0 until w) {
            if (x >= bw && x < w - bw && y >= bw && y < h - bw) continue
            if ((x + y) % 3 != 0) continue
            Lab.fromRgb(rgb[y * w + x], lab); border += lab.copyOf()
        }
        if (border.size < 20) return null
        fun med(k: Int) = border.map { it[k] }.sorted()[border.size / 2]
        val c = doubleArrayOf(med(0), med(1), med(2))
        val d = border.map { kotlin.math.sqrt((it[0] - c[0]).let { v -> v * v } + (it[1] - c[1]).let { v -> v * v } + (it[2] - c[2]).let { v -> v * v }) }.sorted()
        val mad = d[d.size / 2]
        if (mad > 10) return null                       // fondo no liso
        val thr = maxOf(12.0, 4 * mad)
        val m = Mask(w, h)
        for (i in 0 until w * h) {
            Lab.fromRgb(rgb[i], lab)
            val dl = lab[0] - c[0]; val da = lab[1] - c[1]; val db = lab[2] - c[2]
            m.data[i] = kotlin.math.sqrt(dl * dl + da * da + db * db) > thr
        }
        val out = MaskOps.clean(m, radius = 2)
        val frac = out.area / (w * h).toFloat()
        return if (frac in 0.03f..0.92f) out else null
    }
}
