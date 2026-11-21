package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Detección libre de arrugas: además de los pasillos fijos (posiciones y ángulos predefinidos), un
 * mapa de crestas oscuras (valles) en toda la piel permitida, en cualquier orientación.
 *
 * 1. Luz normalizada (campo plano): la luminancia dividida por su versión muy suavizada; quita los
 *    gradientes de iluminación (flash, luz lateral) y deja el detalle fino.
 * 2. Respuesta de valle multiescala (Hessiano, autovalor mayor positivo, como [RidgeTracker]) con su
 *    dirección.
 * 3. Supresión de no máximos a través del valle + histéresis (umbral alto y bajo relativos a la piel):
 *    crestas de un píxel de ancho, conectadas.
 * 4. Cada componente se convierte en una polilínea (camino más largo entre sus extremos).
 *
 * Devuelve candidatos en unidades del ancho de la imagen; quien llama los vuelve a seguir con
 * [RidgeTracker] (fuerza y contraste comparables) y los puntúa ([score]).
 */
object WrinkleField {
    /** Escalas (px para una cara de 400 px de ancho; se escalan con la cara). */
    private val SIGMAS = floatArrayOf(1.4f, 2.2f, 3.4f)

    /** Luz normalizada: lum / suavizada(σ grande) − 1 (en contraste relativo local). */
    fun flatField(lum: GrayImage, sigmaPx: Float): GrayImage {
        val g = PhotometricStereo.blur(lum.data, lum.width, lum.height, sigmaPx)
        return GrayImage(lum.width, lum.height, FloatArray(lum.data.size) { i -> lum.data[i] / max(g[i], 0.02f) - 1f })
    }

    /** Luz normalizada con la escala de brillo original: lum / suavizada × media (umbrales comparables). */
    fun flatScaled(lum: GrayImage, sigmaPx: Float): GrayImage {
        val g = PhotometricStereo.blur(lum.data, lum.width, lum.height, sigmaPx)
        val mean = lum.data.average().toFloat()
        return GrayImage(lum.width, lum.height, FloatArray(lum.data.size) { i -> (lum.data[i] / max(g[i], 0.02f) * mean).coerceIn(0f, 1f) })
    }

    /**
     * Candidatos (polilíneas en unidades del ancho) dentro de [allowed] (x, y en px), de longitud
     * mínima [minLenPx]. [faceWPx]: ancho de la cara en px (escala de los filtros).
     */
    fun candidates(lum: GrayImage, faceWPx: Float, allowed: (Int, Int) -> Boolean, minLenPx: Float): List<List<Pt>> {
        val w = lum.width; val h = lum.height
        val k = faceWPx / 400f
        val n = flatField(lum, 0.06f * faceWPx)
        val resp = FloatArray(w * h); val nx = FloatArray(w * h); val ny = FloatArray(w * h)
        for (s0 in SIGMAS) {
            val s = s0 * k
            val g = PhotometricStereo.blur(n.data, w, h, s)
            Par.rows(h - 2) { ya, yb ->
                for (y in ya + 1 until yb + 1) for (x in 1 until w - 1) {
                    val i = y * w + x
                    val ixx = g[i + 1] - 2 * g[i] + g[i - 1]; val iyy = g[i + w] - 2 * g[i] + g[i - w]
                    val ixy = (g[i + w + 1] - g[i + w - 1] - g[i - w + 1] + g[i - w - 1]) / 4
                    val tr = ixx + iyy; val det = ixx * iyy - ixy * ixy
                    val disc = sqrt(max(0f, tr * tr / 4 - det))
                    val l1 = tr / 2 + disc; val l2 = tr / 2 - disc
                    if (l1 <= 0f) continue
                    val r = s * s * (l1 - max(0f, abs(l2)) * 0.5f)
                    if (r > resp[i]) {
                        resp[i] = r
                        var ax = ixy; var ay = l1 - ixx
                        if (abs(ax) + abs(ay) < 1e-9f) { ax = l1 - iyy; ay = ixy }
                        val nn = hypot(ax, ay).coerceAtLeast(1e-9f)
                        nx[i] = ax / nn; ny[i] = ay / nn            // normal: a través del valle
                    }
                }
            }
        }
        // umbrales relativos a la piel permitida (percentiles)
        val vals = ArrayList<Float>()
        for (y in 1 until h - 1 step 2) for (x in 1 until w - 1 step 2) if (allowed(x, y)) vals += resp[y * w + x]
        if (vals.size < 200) return emptyList()
        vals.sort()
        val hi = vals[(vals.size * 0.96f).toInt()].coerceAtLeast(1e-6f); val lo = vals[(vals.size * 0.88f).toInt()].coerceAtLeast(1e-7f)
        // supresión de no máximos a través del valle
        val ridge = BooleanArray(w * h)
        Par.rows(h - 2) { ya, yb ->
            for (y in ya + 1 until yb + 1) for (x in 1 until w - 1) {
                val i = y * w + x; val r = resp[i]
                if (r < lo || !allowed(x, y)) continue
                val ox = nx[i].roundToInt(); val oy = ny[i].roundToInt()
                if (r >= resp[(y + oy) * w + x + ox] && r >= resp[(y - oy) * w + x - ox]) ridge[i] = true
            }
        }
        // componentes conectados con algún píxel sobre el umbral alto
        val comp = IntArray(w * h) { -1 }
        val out = ArrayList<List<Pt>>()
        val stack = IntArray(w * h)
        var label = 0
        for (start in ridge.indices) {
            if (!ridge[start] || comp[start] >= 0) continue
            var sp = 0; stack[sp++] = start; comp[start] = label
            val px = ArrayList<Int>(); var strong = false
            while (sp > 0) {
                val i = stack[--sp]; px += i
                if (resp[i] >= hi) strong = true
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (ridge[j] && comp[j] < 0) { comp[j] = label; stack[sp++] = j }
                }
            }
            label++
            if (!strong || px.size < minLenPx * 0.7f) continue
            val path = longestPath(px, w) ?: continue
            if (path.size < minLenPx) continue
            // cada ~2 px, en unidades del ancho
            out += path.filterIndexed { idx, _ -> idx % 2 == 0 || idx == path.size - 1 }.map { Pt((it % w + 0.5f) / w, (it / w + 0.5f) / w) }
        }
        return out
    }

    /** Camino más largo aproximado en un componente: BFS desde un píxel al más lejano y de ahí al más lejano. */
    private fun longestPath(px: List<Int>, w: Int): List<Int>? {
        if (px.size < 3) return null
        val set = HashSet(px)
        fun bfs(src: Int): Pair<Int, HashMap<Int, Int>> {
            val prev = HashMap<Int, Int>(); prev[src] = -1
            val q = ArrayDeque<Int>(); q += src; var last = src
            while (q.isNotEmpty()) {
                val i = q.removeFirst(); last = i
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val j = (y + dy) * w + x + dx
                    if (j in set && j !in prev) { prev[j] = i; q += j }
                }
            }
            return last to prev
        }
        val (a, _) = bfs(px[0])
        val (b, prev) = bfs(a)
        val path = ArrayList<Int>(); var c = b
        while (c != -1) { path += c; c = prev[c] ?: -1 }
        return path
    }

    /**
     * Mapa de textura del artículo de FFHQ-Wrinkle (Moon et al., ICPR 2024), reimplementado:
     * T = max(0, 1 − I / (1 + I_G)) con I en 0–255 e I_G la luminancia suavizada (σ ≈ 5 px en una
     * foto de 1024; aquí escalado al ancho de la cara). Alto donde un píxel es más oscuro que su entorno.
     */
    fun textureMap(lum: GrayImage, sigmaPx: Float): GrayImage {
        val g = PhotometricStereo.blur(lum.data, lum.width, lum.height, sigmaPx)
        return GrayImage(lum.width, lum.height, FloatArray(lum.data.size) { i -> max(0f, 1f - lum.data[i] * 255f / (1f + g[i] * 255f)) })
    }

    /** Textura media a lo largo de [pts] frente a la media de la piel ([bg]): > 1 = más oscuro que la piel. */
    fun textureRatio(t: GrayImage, pts: List<Pt>, bg: Float): Float {
        if (pts.isEmpty() || bg <= 1e-6f) return 1f
        val w = t.width.toFloat()
        return pts.map { t.sample(it.x * w, it.y * w) }.average().toFloat() / bg
    }

    /** Rasgos de un candidato (ya seguido con [RidgeTracker]) para [score]. */
    class Features(val contrast: Float, val strength: Float, val lengthFace: Float, val orientation: Float, val straightness: Float,
                   /** Textura a lo largo de la línea frente a la piel ([textureRatio]); 1 = neutro. */
                   val texture: Float = 1f)

    /**
     * Puntuación por diseño (pesos fijados a mano, sin entrenar con datos de terceros): contraste y
     * fuerza sobre el mínimo de la zona (escala logarítmica), longitud, acuerdo con la orientación
     * esperada en la zona (1 = perfecto) y rectitud (cuerda / longitud). > 0 = arruga probable.
     */
    fun score(f: Features, minStrength: Float, minContrast: Float): Float {
        val c = kotlin.math.ln((f.contrast / minContrast).coerceAtLeast(1e-3f))
        val s = kotlin.math.ln((f.strength / minStrength).coerceAtLeast(1e-3f))
        val len = (f.lengthFace / 0.08f).coerceAtMost(2f)
        val tx = kotlin.math.ln(f.texture.coerceAtLeast(1e-3f))
        return 1.2f * c + 0.8f * s + 0.5f * (len - 1f) + 1.6f * (f.orientation - 0.6f) + 1.2f * (f.straightness - 0.85f) +
            WrinkleTuning.textureW * (tx - WrinkleTuning.textureRef)
    }

    /** cos² del ángulo entre la dirección del candidato (de extremo a extremo) y [dir]. */
    fun agreement(pts: List<Pt>, dir: Pt): Float {
        val a = pts.first(); val b = pts.last()
        val vx = b.x - a.x; val vy = b.y - a.y; val n = hypot(vx, vy).coerceAtLeast(1e-6f)
        val dn = hypot(dir.x, dir.y).coerceAtLeast(1e-6f)
        val c = (vx * dir.x + vy * dir.y) / (n * dn)
        return c * c
    }

    fun straightness(pts: List<Pt>): Float {
        var arc = 0f; for (i in 1 until pts.size) arc += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return if (arc < 1e-6f) 0f else min(1f, hypot(pts.last().x - pts.first().x, pts.last().y - pts.first().y) / arc)
    }

    @Suppress("unused") private fun gauss(x: Float, s: Float) = exp(-(x * x) / (2 * s * s))
}
