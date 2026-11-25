package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Diagrama de las zonas no faciales (manos, pies, tronco, brazos, piernas), como el de la cara: capas que
 * se encienden y apagan en el visor y en Comparar.
 * - **Contorno**: la silueta real de la zona ([BodySchema]: borde de la piel o de la persona en la
 *   segmentación; cada mano dedo a dedo; cada pie cortado a la altura del pie).
 * - **Ejes y articulaciones**: el eje de cada miembro (hombro–codo–muñeca, cadera–rodilla–tobillo, línea de
 *   hombros y de caderas) y sus articulaciones.
 * - **Dedos**: el esqueleto de cada mano (cada dedo y la palma) o las líneas de separación entre los dedos
 *   del pie.
 * - **Uñas**: las uñas claramente visibles.
 * - **Puntos de referencia**: los mismos que usa la cámara para alinear la toma ([BodyPoints]).
 *
 * Coordenadas en unidades del ancho de la foto (como las demás líneas de anotación).
 */
object BodyDiagram {
    /** Capas propias del diagrama del cuerpo (el contorno es la capa [AnnotLayer.CONTOUR], común con la cara). */
    val LAYERS = listOf(AnnotLayer.CONTOUR, AnnotLayer.BODY_AXES, AnnotLayer.FINGERS, AnnotLayer.NAILS, AnnotLayer.BODY_POINTS,
        AnnotLayer.SCALP_MAP, AnnotLayer.HAIR_PART) + SkinLesions.LAYERS

    /** Círculo cerrado de radio [r] (punto del diagrama). */
    private fun dot(layer: AnnotLayer, c: Pt, r: Float) =
        AnnotLine(layer, (0 until 10).map { i -> val a = i * Math.PI * 2 / 10; Pt(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()) }, closed = true, filled = true)

    private val FINGER_CHAINS = listOf(listOf(0, 1, 2, 3, 4), listOf(5, 6, 7, 8), listOf(9, 10, 11, 12), listOf(13, 14, 15, 16), listOf(17, 18, 19, 20),
        listOf(0, 5, 9, 13, 17, 0))

    /**
     * Con [ZoneCriteria.Scene.hi] (la foto a más resolución), las uñas de las manos se buscan por el esqueleto y
     * el color ([Nails.plates]); sin ella, en la rejilla de la segmentación (círculos).
     */
    fun lines(zone: Zone, s: ZoneCriteria.Scene): List<AnnotLine> {
        val out = ArrayList<AnnotLine>()
        val schema = runCatching { BodySchema.of(zone, s) }.getOrNull()
        val digits = zone.guide == GuideType.HANDS || zone.guide == GuideType.FEET
        // tamaño de los puntos: proporcional a lo que se ve (manos y pies, más pequeños)
        val r = if (digits) 0.006f else 0.008f
        val pts = runCatching { BodyPoints.of(zone, s) }.getOrDefault(emptyMap())
        // esqueleto al estilo de las manos (articulaciones, falanges transversales y medidas en grados y proporciones)
        val skeleton = if (zone.guide == GuideType.HANDS) emptyList() else runCatching { BodySkeleton.lines(zone, s, pts, r) }.getOrDefault(emptyList())
        schema?.let { sc ->
            sc.polygons.filter { it.size >= 3 }.forEach { out += AnnotLine(AnnotLayer.CONTOUR, it, closed = true) }
            if (digits || skeleton.none { it.layer == AnnotLayer.BODY_AXES })
                sc.lines.filter { it.size >= 2 }.forEach { out += AnnotLine(if (digits) AnnotLayer.FINGERS else AnnotLayer.BODY_AXES, it, closed = false) }
            // los ejes del esquema, solo si no hay esqueleto (serían los mismos dos veces)
            if (!digits && skeleton.none { it.layer == AnnotLayer.BODY_AXES }) sc.dots.forEach { out += dot(AnnotLayer.BODY_AXES, it, r * 1.3f) }
        }
        out += skeleton
        // manos: el esqueleto de cada dedo y la palma; uñas visibles
        if (zone.guide == GuideType.HANDS) s.hands.orEmpty().filter { it.points.size >= 21 }.forEach { h ->
            FINGER_CHAINS.forEach { ch -> out += AnnotLine(AnnotLayer.FINGERS, ch.map { h.points[it] }, closed = false) }
            val hi = s.hi
            if (hi != null) {
                val w = hi.w
                Nails.plates(hi.px, hi.w, hi.h, h.points).filterNotNull().forEach { n ->
                    out += AnnotLine(AnnotLayer.NAILS, n.outline().map { Pt(it.x / w, it.y / w) }, closed = true, filled = true)
                }
            } else s.classes?.let { c -> c.rgb?.let { rgb ->
                Nails.ofHand(rgb, c.width, c.height, h.points, ZoneCriteria.STRONG_NAIL_DE).filterNotNull().forEach { n ->
                    out += dot(AnnotLayer.NAILS, Pt(n.x / c.width, n.y / c.width), (n.r / c.width).coerceAtLeast(r))
                }
            } }
        }
        if (zone.guide == GuideType.FEET) s.classes?.let { c ->
            ZoneCriteria.feetOf(c).flatMap { it.nails }.forEach { out += dot(AnnotLayer.NAILS, it, r * 1.5f) }
        }
        // puntos de referencia de la alineación
        pts.values.forEach { out += dot(AnnotLayer.BODY_POINTS, it, r) }
        return out
    }
}
