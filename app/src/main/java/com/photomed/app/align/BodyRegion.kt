package com.photomed.app.align

import android.graphics.Bitmap
import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone

/**
 * Región del cuerpo en una imagen, para que la detección de bordes (fantasma de bordes,
 * coincidencia en vivo y afinado de la alineación) use solo los bordes del paciente y no los del
 * fondo (muebles, puertas, azulejos, sombras en la pared…).
 *
 * - Manos, pies y modo libre: primero el sujeto sobre fondo liso ([ForegroundOnPlain]); si no,
 *   segmentación de personas.
 * - Resto de zonas: segmentación de personas (incluye pelo, orejas y cuello); si no, fondo liso.
 * Si no se aísla un sujeto plausible (3–97 % del encuadre), null: se usan todos los bordes.
 */
object BodyRegion {
    const val MIN_FRACTION = 0.03f
    const val MAX_FRACTION = 0.97f

    fun of(zone: Zone, bmp: Bitmap, segment: ((Bitmap) -> Mask?)?): Mask? {
        val plainFirst = zone.guide == GuideType.HANDS || zone.guide == GuideType.FEET || zone.guide == GuideType.FREE
        fun plain() = runCatching { ForegroundOnPlain.mask(bmp.rgb(), bmp.width, bmp.height) }.getOrNull()?.takeIf(::plausible)
        fun person() = runCatching { segment?.invoke(bmp) }.getOrNull()?.takeIf(::plausible)
        return if (plainFirst) plain() ?: person() else person() ?: plain()
    }

    fun plausible(m: Mask): Boolean {
        val f = m.area / (m.width * m.height).toFloat()
        return f in MIN_FRACTION..MAX_FRACTION
    }

    /** Remuestreo por vecino más cercano a [w]×[h]. */
    fun resample(m: Mask, w: Int, h: Int): Mask {
        if (m.width == w && m.height == h) return m
        val out = Mask(w, h)
        for (y in 0 until h) {
            val sy = (y * m.height / h).coerceAtMost(m.height - 1)
            for (x in 0 until w) out.data[y * w + x] = m.data[sy * m.width + (x * m.width / w).coerceAtMost(m.width - 1)]
        }
        return out
    }

    /**
     * Gris sin la textura del fondo: los píxeles fuera del cuerpo toman el gris medio del fondo,
     * así desaparecen sus bordes y se conserva el contraste real del contorno del cuerpo.
     * [g] sin estandarizar; devuelve la imagen estandarizada.
     */
    fun flattenBackground(g: GrayImage, body: Mask): GrayImage {
        val m = resample(body, g.width, g.height)
        var s = 0.0; var n = 0
        for (i in g.data.indices) if (!m.data[i]) { s += g.data[i]; n++ }
        if (n == 0 || n == g.data.size) return g.standardized()
        val bg = (s / n).toFloat()
        val out = GrayImage(g.width, g.height, FloatArray(g.data.size) { if (m.data[it]) g.data[it] else bg })
        return out.standardized()
    }

    /** Máscara dilatada a [w]×[h] para filtrar mapas de bordes (conserva el contorno exterior). */
    fun edgeGate(body: Mask, w: Int, h: Int): BooleanArray =
        MaskOps.dilate(resample(body, w, h), maxOf(1, w / 60)).data
}
