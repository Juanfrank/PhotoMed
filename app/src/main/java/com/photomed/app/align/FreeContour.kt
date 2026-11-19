package com.photomed.app.align

import android.graphics.Bitmap
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Modo libre: contorno diseñado a partir de lo visible en una imagen (la foto de referencia o el
 * cuadro actual de la cámara), sin dibujo anatómico predefinido.
 *
 * 1. **Silueta** del sujeto: fondo liso (sustracción por color, [ForegroundOnPlain]) o persona
 *    (segmentación); se traza su borde exterior (el extremo de lo visible), suavizado y simplificado.
 * 2. Si no se puede aislar un sujeto: **bordes** dominantes de la imagen (gradiente, el 7 % más
 *    fuerte, sin ruido aislado) como dibujo lineal.
 * Coordenadas: x del ancho, y en unidades del ancho (como los puntos de los detectores).
 */
class FreeContour(
    /** Borde exterior del sujeto (cerrado), o vacío. */
    val outline: List<Pt>,
    /** Mapa de bordes ([edgeW]×[edgeH], true = borde) si no hay silueta. */
    val edges: BooleanArray? = null,
    val edgeW: Int = 0,
    val edgeH: Int = 0,
    /** Cómo se obtuvo ("silueta sobre fondo liso", "silueta de la persona", "bordes"). */
    val source: String,
) {
    companion object {
        const val EDGE_W = 120

        /**
         * [segment]: segmentación de personas opcional (la pasa quien tiene el segmentador; null en
         * pruebas o si no está disponible).
         */
        fun from(bmp: Bitmap, segment: ((Bitmap) -> Mask?)? = null): FreeContour? {
            val w = bmp.width; val h = bmp.height
            val rgb = bmp.rgb()
            ForegroundOnPlain.mask(rgb, w, h)?.let { m -> outlineOf(m)?.let { return FreeContour(it, source = "silueta sobre fondo liso") } }
            segment?.invoke(bmp)?.takeIf { val f = it.area / (it.width * it.height).toFloat(); f in 0.03f..0.92f }
                ?.let { m -> outlineOf(m)?.let { return FreeContour(it, source = "silueta de la persona") } }
            return edgesOf(bmp)
        }

        /** Borde exterior del componente mayor, suavizado (media móvil) y simplificado (~120 puntos). */
        fun outlineOf(m: Mask): List<Pt>? {
            val raw = ContourMetrics.of(m)?.outline ?: return null
            if (raw.size < 20) return null
            val k = max(2, raw.size / 150)
            val smooth = raw.indices.map { i ->
                var sx = 0f; var sy = 0f
                for (d in -k..k) { val p = raw[(i + d + raw.size) % raw.size]; sx += p.x; sy += p.y }
                Pt(sx / (2 * k + 1), sy / (2 * k + 1))
            }
            val step = max(1, smooth.size / 120)
            return smooth.filterIndexed { i, _ -> i % step == 0 }
        }

        /**
         * Dibujo lineal de los bordes dominantes (gradiente de Sobel sobre la imagen reducida).
         * Con [body] (ver [BodyRegion]) solo cuentan los bordes del cuerpo y su contorno exterior;
         * los del fondo se descartan y el umbral se calcula solo dentro del cuerpo.
         */
        fun edgesOf(bmp: Bitmap, body: Mask? = null): FreeContour? {
            val g = ImageOps.gray(bmp, EDGE_W, body)
            val w = g.width; val h = g.height
            val gate = body?.let { BodyRegion.edgeGate(it, w, h) }
            val mag = FloatArray(w * h)
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                fun p(dx: Int, dy: Int) = g[x + dx, y + dy]
                val gx = (p(1, -1) + 2 * p(1, 0) + p(1, 1)) - (p(-1, -1) + 2 * p(-1, 0) + p(-1, 1))
                val gy = (p(-1, 1) + 2 * p(0, 1) + p(1, 1)) - (p(-1, -1) + 2 * p(0, -1) + p(1, -1))
                mag[y * w + x] = hypot(gx, gy)
            }
            if (gate != null) for (i in mag.indices) if (!gate[i]) mag[i] = 0f
            val inside = if (gate == null) mag else mag.filterIndexed { i, _ -> gate[i] }.toFloatArray()
            if (inside.size < 20) return null
            val sorted = inside.sortedArray()
            // dentro del cuerpo hay menos píxeles: se conserva una fracción algo mayor (12 %)
            val thr = sorted[(sorted.size * (if (gate == null) 0.93f else 0.88f)).toInt()]
            if (thr <= 1e-3f) return null
            val e = BooleanArray(w * h) { mag[it] >= thr }
            // sin puntos aislados (ruido): al menos 2 vecinos de borde
            val clean = BooleanArray(w * h) { i ->
                if (!e[i]) false else {
                    val x = i % w; val y = i / w; var n = 0
                    for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0)) {
                        val xx = min(max(x + dx, 0), w - 1); val yy = min(max(y + dy, 0), h - 1)
                        if (e[yy * w + xx]) n++
                    }
                    n >= 2
                }
            }
            return FreeContour(emptyList(), clean, w, h, if (gate == null) "bordes de la imagen" else "bordes del cuerpo")
        }
    }
}
