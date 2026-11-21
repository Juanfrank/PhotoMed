package com.photomed.app.align

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estéreo fotométrico con la pantalla del teléfono como fuente de luz (cámara frontal).
 *
 * Se toman fotos con la pantalla negra (luz ambiente) y con la mitad izquierda, derecha, superior e
 * inferior de la pantalla en blanco, con la exposición bloqueada. Restando la toma oscura, cada
 * píxel cumple la ley de Lambert I_k = ρ · (n · L_k), con L_k la luz de la mitad k integrada sobre su
 * superficie (factor de forma de un emisor plano). Con ≥ 3 luces no coplanares se despeja por
 * mínimos cuadrados g = ρ n = (LᵀL)⁻¹ Lᵀ I (Woodham, 1980). De las normales salen las pendientes
 * p = ∂z/∂x y q = ∂z/∂y, que se integran a una superficie con el método de Frankot y Chellappa
 * (1988, proyección en el espacio de Fourier sobre las superficies integrables). El relieve fino
 * (arrugas, surcos) es la superficie menos su versión suavizada (pasa-altos de unos mm).
 *
 * Coordenadas de la cámara: x a la derecha y y hacia abajo en la imagen (sin espejar), z hacia
 * delante (de la cámara a la cara). La cámara frontal está en el centro del borde superior.
 */
object PhotometricStereo {
    enum class Pattern { FULL, DARK, LEFT, RIGHT, TOP, BOTTOM }

    /** Patrones iluminados (en este orden en cada ciclo). */
    val LIT = listOf(Pattern.LEFT, Pattern.RIGHT, Pattern.TOP, Pattern.BOTTOM)

    /**
     * Geometría de la pantalla en mm: ancho, alto y distancia de la cámara al borde superior de la
     * zona iluminada (la cámara está por encima o dentro del borde superior).
     */
    data class Screen(val widthMm: Float, val heightMm: Float, val topOffsetMm: Float = 4f)

    /**
     * Vector de luz de un patrón para un punto de la cara a [distMm] en el eje de la cámara:
     * Σ r · D · dA / |r|⁴ sobre la zona blanca (r, del punto a cada elemento de la pantalla), que es
     * la irradiancia vectorial de un emisor plano lambertiano. "Izquierda" es la del usuario que
     * mira la pantalla, que en la imagen sin espejar de la cámara frontal queda a la derecha (+x).
     */
    fun light(p: Pattern, s: Screen, distMm: Float, n: Int = 24): FloatArray {
        val x0: Float; val x1: Float; val y0: Float; val y1: Float
        val w2 = s.widthMm / 2; val top = s.topOffsetMm; val bottom = s.topOffsetMm + s.heightMm; val mid = (top + bottom) / 2
        when (p) {
            Pattern.LEFT -> { x0 = 0f; x1 = w2; y0 = top; y1 = bottom }
            Pattern.RIGHT -> { x0 = -w2; x1 = 0f; y0 = top; y1 = bottom }
            Pattern.TOP -> { x0 = -w2; x1 = w2; y0 = top; y1 = mid }
            Pattern.BOTTOM -> { x0 = -w2; x1 = w2; y0 = mid; y1 = bottom }
            Pattern.FULL -> { x0 = -w2; x1 = w2; y0 = top; y1 = bottom }
            Pattern.DARK -> return floatArrayOf(0f, 0f, 0f)
        }
        val d = distMm.toDouble()
        val da = ((x1 - x0) / n) * ((y1 - y0) / n).toDouble()
        var lx = 0.0; var ly = 0.0; var lz = 0.0
        for (i in 0 until n) for (j in 0 until n) {
            val sx = x0 + (x1 - x0) * (i + 0.5) / n; val sy = y0 + (y1 - y0) * (j + 0.5) / n
            // del punto de la cara (0, 0, D) al elemento de pantalla (sx, sy, 0)
            val rx = sx; val ry = sy; val rz = -d
            val r2 = rx * rx + ry * ry + rz * rz
            val k = d * da / (r2 * r2)
            lx += rx * k; ly += ry * k; lz += rz * k
        }
        // escala común arbitraria (solo importan las proporciones entre patrones)
        val norm = d * d / (s.widthMm * s.heightMm)
        return floatArrayOf((lx * norm).toFloat(), (ly * norm).toFloat(), (lz * norm).toFloat())
    }

    /** Normales (nx, ny, nz con nz < 0 hacia la cámara), albedo y validez de cada píxel. */
    class Normals(val width: Int, val height: Int, val nx: FloatArray, val ny: FloatArray, val nz: FloatArray,
                  val albedo: FloatArray, val valid: BooleanArray)

    /**
     * Normales por mínimos cuadrados. [images]: una imagen por luz, en luminancia lineal y sin la
     * luz ambiente; [lights]: vectores de luz en el mismo orden. [mask]: píxeles a resolver.
     */
    fun solve(images: List<FloatArray>, lights: List<FloatArray>, w: Int, h: Int, mask: BooleanArray? = null): Normals {
        require(images.size == lights.size && images.size >= 3)
        val m = pseudoInverse(lights)
        val n = w * h
        val nx = FloatArray(n); val ny = FloatArray(n); val nz = FloatArray(n); val al = FloatArray(n); val ok = BooleanArray(n)
        val k = images.size
        var maxAl = 0f
        for (i in 0 until n) {
            if (mask != null && !mask[i]) continue
            var gx = 0f; var gy = 0f; var gz = 0f
            for (j in 0 until k) { val v = images[j][i]; gx += m[0][j] * v; gy += m[1][j] * v; gz += m[2][j] * v }
            val r = sqrt(gx * gx + gy * gy + gz * gz)
            if (r <= 1e-6f || gz >= 0f) continue
            nx[i] = gx / r; ny[i] = gy / r; nz[i] = gz / r; al[i] = r
            if (r > maxAl) maxAl = r
        }
        // válidos: hacia la cámara y con señal (sombras propias y fondo oscuro fuera)
        for (i in 0 until n) ok[i] = al[i] > 0.04f * maxAl && nz[i] < -0.3f
        return Normals(w, h, nx, ny, nz, al, ok)
    }

    /** (LᵀL)⁻¹ Lᵀ, 3 × k. */
    fun pseudoInverse(lights: List<FloatArray>): Array<FloatArray> {
        val a = Array(3) { DoubleArray(3) }
        for (l in lights) for (r in 0..2) for (c in 0..2) a[r][c] += (l[r] * l[c]).toDouble()
        val inv = invert3(a)
        return Array(3) { r -> FloatArray(lights.size) { j -> (0..2).sumOf { c -> inv[r][c] * lights[j][c] }.toFloat() } }
    }

    private fun invert3(m: Array<DoubleArray>): Array<DoubleArray> {
        val (a, b, c) = Triple(m[0][0], m[0][1], m[0][2]); val (d, e, f) = Triple(m[1][0], m[1][1], m[1][2]); val (g, h, i) = Triple(m[2][0], m[2][1], m[2][2])
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) > 1e-18) { "luces coplanares" }
        return arrayOf(
            doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
            doubleArrayOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
            doubleArrayOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det),
        )
    }

    /**
     * Altura hacia la cámara (px) integrando las pendientes de las normales por Frankot-Chellappa.
     * [w] y [h] potencias de 2. Fuera de los válidos la pendiente es 0.
     */
    fun heights(nm: Normals): FloatArray {
        val w = nm.width; val h = nm.height
        val p = FloatArray(w * h); val q = FloatArray(w * h)
        for (i in 0 until w * h) if (nm.valid[i]) {
            // altura hacia la cámara t = −z; ∂t/∂x = −∂z/∂x = nx / nz
            p[i] = (nm.nx[i] / nm.nz[i]).coerceIn(-3f, 3f)
            q[i] = (nm.ny[i] / nm.nz[i]).coerceIn(-3f, 3f)
        }
        return frankotChellappa(p, q, w, h)
    }

    /** Integración de un campo de gradientes (p = ∂t/∂x, q = ∂t/∂y) en el espacio de Fourier. */
    fun frankotChellappa(p: FloatArray, q: FloatArray, w: Int, h: Int): FloatArray {
        require(w and (w - 1) == 0 && h and (h - 1) == 0) { "tamaño potencia de 2" }
        val pr = DoubleArray(w * h) { p[it].toDouble() }; val pi = DoubleArray(w * h)
        val qr = DoubleArray(w * h) { q[it].toDouble() }; val qi = DoubleArray(w * h)
        fft2(pr, pi, w, h, false); fft2(qr, qi, w, h, false)
        val zr = DoubleArray(w * h); val zi = DoubleArray(w * h)
        for (v in 0 until h) for (u in 0 until w) {
            val i = v * w + u
            // frecuencias con derivada espectral de diferencia central (más estable que j·ω)
            val wx = sin(2 * PI * u / w); val wy = sin(2 * PI * v / h)
            val den = wx * wx + wy * wy
            if (den < 1e-12) continue
            // Z = (−j wx P − j wy Q) / den
            zr[i] = (wx * pi[i] + wy * qi[i]) / den
            zi[i] = (-wx * pr[i] - wy * qr[i]) / den
        }
        fft2(zr, zi, w, h, true)
        return FloatArray(w * h) { zr[it].toFloat() }
    }

    /** Relieve fino: altura menos su suavizado gaussiano ([sigmaPx]); NaN fuera de [valid]. */
    fun relief(t: FloatArray, w: Int, h: Int, sigmaPx: Float, valid: BooleanArray): FloatArray {
        // suavizado normalizado solo con los válidos (los bordes de la cara no sesgan el relieve)
        val vm = FloatArray(w * h) { if (valid[it]) 1f else 0f }
        val tv = FloatArray(w * h) { if (valid[it]) t[it] else 0f }
        val bt = blur(tv, w, h, sigmaPx); val bm = blur(vm, w, h, sigmaPx)
        return FloatArray(w * h) { i -> if (!valid[i] || bm[i] < 0.3f) Float.NaN else t[i] - bt[i] / bm[i] }
    }

    /** sRGB (0..1) → luminancia lineal. */
    fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    /**
     * Desplazamiento entero (dx, dy) que mejor lleva [img] sobre [ref] (correlación normalizada de
     * sus bordes, que dependen poco de la dirección de la luz), en ±[maxShift] px; búsqueda
     * gruesa a 1/4 y fina a resolución completa.
     */
    fun align(ref: FloatArray, img: FloatArray, w: Int, h: Int, maxShift: Int = 24): Pair<Int, Int> {
        val er = edges(ref, w, h); val ei = edges(img, w, h)
        fun down(a: FloatArray, f: Int): FloatArray { val ww = w / f; val hh = h / f
            return FloatArray(ww * hh) { k -> val x = (k % ww) * f; val y = (k / ww) * f; var s = 0f; for (j in 0 until f) for (i in 0 until f) s += a[(y + j) * w + x + i]; s } }
        val f = 4; val dr = down(er, f); val di = down(ei, f)
        val (cx, cy) = bestShift(dr, di, w / f, h / f, -(maxShift / f)..(maxShift / f), -(maxShift / f)..(maxShift / f))
        return bestShift(er, ei, w, h, (cx * f - f)..(cx * f + f), (cy * f - f)..(cy * f + f))
    }

    private fun bestShift(a: FloatArray, b: FloatArray, w: Int, h: Int, xs: IntRange, ys: IntRange): Pair<Int, Int> {
        var best = -Double.MAX_VALUE; var bx = 0; var by = 0
        val m = max(abs(xs.first), abs(xs.last)).coerceAtLeast(max(abs(ys.first), abs(ys.last))) + 1
        for (dy in ys) for (dx in xs) {
            var sab = 0.0; var saa = 0.0; var sbb = 0.0
            for (y in m until h - m) { val yb = y - dy; for (x in m until w - m) {
                val va = a[y * w + x].toDouble(); val vb = b[yb * w + x - dx].toDouble()
                sab += va * vb; saa += va * va; sbb += vb * vb } }
            val c = sab / sqrt(saa * sbb + 1e-12)
            if (c > best) { best = c; bx = dx; by = dy }
        }
        return bx to by
    }

    /** Desplaza [img] (dx, dy) px (vecino más cercano, bordes repetidos). */
    fun shift(img: FloatArray, w: Int, h: Int, dx: Int, dy: Int): FloatArray =
        FloatArray(w * h) { k -> val x = (k % w - dx).coerceIn(0, w - 1); val y = (k / w - dy).coerceIn(0, h - 1); img[y * w + x] }

    private fun edges(a: FloatArray, w: Int, h: Int): FloatArray {
        // magnitud del gradiente de la imagen normalizada por su media local (atenúa el sombreado)
        val b = blur(a, w, h, 6f)
        val r = FloatArray(w * h) { a[it] / (b[it] + 1e-4f) }
        val out = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            out[i] = hypot(r[i + 1] - r[i - 1], r[i + w] - r[i - w])
        }
        return out
    }

    fun blur(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val r = (3 * sigma).roundToInt().coerceAtLeast(1)
        val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }.let { kk -> val s = kk.sum(); FloatArray(kk.size) { kk[it] / s } }
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        Par.rows(h) { ya, yb -> for (y in ya until yb) for (x in 0 until w) { var s = 0f; for (j in -r..r) s += k[j + r] * src[y * w + (x + j).coerceIn(0, w - 1)]; tmp[y * w + x] = s } }
        Par.rows(h) { ya, yb -> for (y in ya until yb) for (x in 0 until w) { var s = 0f; for (j in -r..r) s += k[j + r] * tmp[(y + j).coerceIn(0, h - 1) * w + x]; out[y * w + x] = s } }
        return out
    }

    // ---------- FFT ----------

    private fun fft2(re: DoubleArray, im: DoubleArray, w: Int, h: Int, inverse: Boolean) {
        // filas y luego columnas, repartidas entre núcleos (cada tramo con sus propios búferes)
        Par.rows(h, minRows = 16) { ya, yb ->
            val rr = DoubleArray(w); val ri = DoubleArray(w)
            for (y in ya until yb) {
                for (x in 0 until w) { rr[x] = re[y * w + x]; ri[x] = im[y * w + x] }
                fft(rr, ri, inverse)
                for (x in 0 until w) { re[y * w + x] = rr[x]; im[y * w + x] = ri[x] }
            }
        }
        Par.rows(w, minRows = 16) { xa, xb ->
            val cr = DoubleArray(h); val ci = DoubleArray(h)
            for (x in xa until xb) {
                for (y in 0 until h) { cr[y] = re[y * w + x]; ci[y] = im[y * w + x] }
                fft(cr, ci, inverse)
                for (y in 0 until h) { re[y * w + x] = cr[y]; im[y * w + x] = ci[y] }
            }
        }
    }

    /** FFT radix-2 in situ; la inversa incluye 1/n. */
    fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * (if (inverse) 1 else -1)
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }

    /**
     * Comprobación de signos con la forma convexa de la cara: en la mitad izquierda de la imagen las
     * normales miran a la izquierda (nx < 0) y en la derecha a la derecha. Si sale al revés (p. ej.
     * la cámara no está arriba), invierte x; ídem y (frente arriba, mentón abajo). En la imagen.
     */
    fun fixSigns(nm: Normals, faceBox: FloatArray /* x0, y0, x1, y1 en px */) {
        var l = 0.0; var r = 0.0; var t = 0.0; var b = 0.0
        val cx = (faceBox[0] + faceBox[2]) / 2; val cy = (faceBox[1] + faceBox[3]) / 2
        for (y in faceBox[1].toInt().coerceAtLeast(0) until min(nm.height, faceBox[3].toInt())) for (x in faceBox[0].toInt().coerceAtLeast(0) until min(nm.width, faceBox[2].toInt())) {
            val i = y * nm.width + x; if (!nm.valid[i]) continue
            if (x < cx) l += nm.nx[i] else r += nm.nx[i]
            if (y < cy) t += nm.ny[i] else b += nm.ny[i]
        }
        if (l > r) for (i in nm.nx.indices) nm.nx[i] = -nm.nx[i]
        if (t > b) for (i in nm.ny.indices) nm.ny[i] = -nm.ny[i]
    }
}
