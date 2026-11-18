package com.photomed.app.ui.camera

import androidx.compose.ui.geometry.Rect
import com.photomed.app.align.Pt
import com.photomed.app.align.Silhouette
import com.photomed.app.data.Zone

/**
 * Modo individual de las extremidades (manos, pies, piernas): fotografiar una sola en la misma
 * zona. La guía se acerca a la mitad del dibujo que corresponde a ese lado del paciente y se
 * ajusta a su silueta; la foto guarda el lado (nombre de archivo y metadatos).
 */
enum class LimbSide(val label: String, val tag: String?) {
    BOTH("Ambas", null), RIGHT("Derecha", "der"), LEFT("Izquierda", "izq");

    companion object {
        fun ofTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: BOTH
        /** Lado indicado en el nombre de archivo (`<zona>_der_…` / `<zona>_izq_…`). */
        fun ofFileName(name: String, zone: Zone): LimbSide =
            when (name.removePrefix(zone.id + "_").substringBefore('_')) { "der" -> RIGHT; "izq" -> LEFT; else -> BOTH }
    }
}

/** La zona admite el modo individual (el dibujo tiene dos mitades: mano/pie/pierna izquierda y derecha). */
val Zone.hasLimbSides: Boolean get() = GuideArtByZone[id]?.halves?.size == 8

/**
 * Encuadre (normalizado, cuadrado en coordenadas normalizadas = 3:4 en la pantalla) de la mitad del
 * dibujo de ese lado, con margen; null si la zona o el lado no tienen modo individual.
 */
fun GuideArt.limbFrame(side: LimbSide, margin: Float = 0.12f): Rect? {
    if (side == LimbSide.BOTH || halves.size != 8) return null
    val left = (side == LimbSide.RIGHT) == leftIsRight
    val k = if (left) 0 else 4
    val x0 = halves[k]; val y0 = halves[k + 1]; val x1 = halves[k + 2]; val y1 = halves[k + 3]
    // en coordenadas normalizadas un cuadrado es 3:4 en pantalla, como el encuadre
    val size = maxOf(x1 - x0, y1 - y0) * (1f + 2f * margin)
    val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
    return Rect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
}

/** Recuadro (normalizado) de la extremidad de ese lado en el dibujo. */
fun GuideArt.limbBox(side: LimbSide): Rect? {
    if (side == LimbSide.BOTH || halves.size != 8) return null
    val k = if ((side == LimbSide.RIGHT) == leftIsRight) 0 else 4
    return Rect(halves[k], halves[k + 1], halves[k + 2], halves[k + 3])
}

/** Región del dibujo que se conserva en el modo individual: su mitad (sin la otra extremidad). */
fun GuideArt.limbClip(side: LimbSide): Rect? {
    if (side == LimbSide.BOTH || halves.size != 8) return null
    val left = (side == LimbSide.RIGHT) == leftIsRight
    // frontera: punto medio entre las dos mitades
    val mid = (halves[2] + halves[4]) / 2
    return if (left) Rect(-1f, -1f, mid, 2f) else Rect(mid, -1f, 2f, 2f)
}

/** Contorno por filas de la mitad del dibujo de ese lado. */
fun GuideArt.limbOutline(side: LimbSide): FloatArray? {
    if (side == LimbSide.BOTH || halves.size != 8) return null
    val left = (side == LimbSide.RIGHT) == leftIsRight
    return (if (left) outlineL else outlineR).takeIf { it.size >= 24 }
}

/** Punto del encuadre de la foto (unidades del ancho) → coordenadas del dibujo completo. */
fun Rect.toArt(p: Pt): Pt {
    val h = 4f / 3f
    return Pt(left + p.x * width, (top + (p.y / h) * height) * h)
}

/** Silueta del encuadre de la foto → silueta en coordenadas del dibujo completo. */
fun Rect.toArt(s: Silhouette): Silhouette {
    val rows = s.rows
    val l = FloatArray(rows) { Float.NaN }; val r = FloatArray(rows) { Float.NaN }
    for (i in 0 until rows) {
        val ya = (i + 0.5f) / rows
        val yf = (ya - top) / height
        val sp = s.span(yf) ?: continue
        l[i] = left + sp.first * width; r[i] = left + sp.second * width
    }
    return Silhouette(l, r)
}

fun limbFrameOf(art: GuideArt, side: LimbSide): Rect? = art.limbFrame(side)
fun limbOutlineOf(art: GuideArt, side: LimbSide): FloatArray? = art.limbOutline(side)
