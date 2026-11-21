package com.photomed.app.align

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Dibujo de las anotaciones con el Canvas de Android (el mismo en pantalla y al exportar).
 *
 * Símbolos: con un punto son un **marcador** del tamaño elegido; con dos (arrastrando) ocupan el
 * rectángulo entre ellos (círculo → elipse, cuadrado → rectángulo…), y la línea y la flecha van de un
 * extremo al otro. El texto no lleva fondo (solo un contorno fino para leerse), es pequeño y va en la
 * misma línea que el símbolo, pegado al borde del lado elegido sin cortarlo; en «auto» se elige el lado
 * que no se superpone con otras anotaciones. En las flechas el texto va en el origen; en las líneas,
 * encima o debajo, con su mismo ángulo. El borrador tipo pintura se dibuja con CLEAR en una capa propia.
 */
object InkRenderer {
    /** Símbolos con área (marcador o rectángulo arrastrado). */
    val AREA = setOf("dot", "circle", "triangle", "square", "x", "plus", "star")

    fun draw(c: Canvas, strokes: List<InkStroke>, unit: Float, map: (Pt) -> PointF, textScale: Float = 1f) {
        if (strokes.isEmpty()) return
        val layer = c.saveLayer(null, null)
        val occupied = ArrayList<RectF>()
        for (s in strokes) { drawOne(c, s, unit, map); bounds(s, unit, map)?.let { occupied += it } }
        // textos al final, evitando lo ya dibujado y los otros textos
        for (s in strokes) if (s.kind == InkStroke.SYMBOL && !s.text.isNullOrBlank() && !s.erase) drawText(c, s, unit, map, textScale, occupied)
        c.restoreToCount(layer)
    }

    private fun paintOf(s: InkStroke, unit: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = s.color; strokeWidth = (s.width * unit).coerceAtLeast(2f); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /** Rectángulo del símbolo en px (marcador: cuadrado del tamaño, centrado en el punto). */
    fun symbolRect(s: InkStroke, unit: Float, map: (Pt) -> PointF): RectF {
        val pts = s.points.map(map)
        if (pts.size >= 2 && (s.shape ?: "dot") in AREA) {
            val a = pts.first(); val b = pts.last()
            return RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
        }
        val r = (s.size * unit).coerceAtLeast(8f) / 2
        return RectF(pts[0].x - r, pts[0].y - r, pts[0].x + r, pts[0].y + r)
    }

    fun drawOne(c: Canvas, s: InkStroke, unit: Float, map: (Pt) -> PointF, @Suppress("UNUSED_PARAMETER") textScale: Float = 1f) {
        if (s.points.isEmpty()) return
        val p = paintOf(s, unit)
        val pts = s.points.map(map)
        if (s.kind != InkStroke.SYMBOL) {
            if (pts.size == 1) c.drawCircle(pts[0].x, pts[0].y, p.strokeWidth / 2, Paint(p).apply { style = Paint.Style.FILL })
            else c.drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, p)
            return
        }
        val kind = s.shape ?: "dot"
        if (kind == "line" || kind == "arrow") {
            val a = pts.first()
            val b = if (pts.size >= 2) pts.last() else PointF(a.x + s.size * unit, a.y)   // marcador: corto, horizontal
            c.drawLine(a.x, a.y, b.x, b.y, p)
            if (kind == "arrow") {
                val ang = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                val hl = (hypot(b.x - a.x, b.y - a.y) * 0.25f).coerceIn(p.strokeWidth * 3f, p.strokeWidth * 8f)
                for (d in listOf(Math.PI * 0.85, -Math.PI * 0.85))
                    c.drawLine(b.x, b.y, b.x + (hl * cos(ang + d)).toFloat(), b.y + (hl * sin(ang + d)).toFloat(), p)
            }
            return
        }
        val r = symbolRect(s, unit, map)
        val cx = r.centerX(); val cy = r.centerY(); val rx = r.width() / 2; val ry = r.height() / 2
        when (kind) {
            "dot" -> c.drawOval(if (pts.size >= 2) r else RectF(cx - rx * 0.6f, cy - ry * 0.6f, cx + rx * 0.6f, cy + ry * 0.6f), Paint(p).apply { style = Paint.Style.FILL })
            "circle" -> c.drawOval(r, p)
            "square" -> c.drawRect(r, p)
            "triangle" -> c.drawPath(Path().apply { moveTo(cx, r.top); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, p)
            "x" -> { c.drawLine(r.left, r.top, r.right, r.bottom, p); c.drawLine(r.left, r.bottom, r.right, r.top, p) }
            "plus" -> { c.drawLine(r.left, cy, r.right, cy, p); c.drawLine(cx, r.top, cx, r.bottom, p) }
            "star" -> c.drawPath(Path().apply {
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5; val k = if (i % 2 == 0) 1f else 0.45f
                    val x = cx + (rx * k * cos(a)).toFloat(); val y = cy + (ry * k * sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }, p)
        }
    }

    /** Rectángulo ocupado por un trazo (px), para que los textos no se le superpongan. */
    private fun bounds(s: InkStroke, unit: Float, map: (Pt) -> PointF): RectF? {
        if (s.erase || s.points.isEmpty()) return null
        if (s.kind == InkStroke.SYMBOL && (s.shape ?: "dot") in AREA) return symbolRect(s, unit, map)
        val pts = s.points.map(map)
        val w = (s.width * unit) / 2
        return RectF(pts.minOf { it.x } - w, pts.minOf { it.y } - w, pts.maxOf { it.x } + w, pts.maxOf { it.y } + w)
    }

    private fun drawText(c: Canvas, s: InkStroke, unit: Float, map: (Pt) -> PointF, textScale: Float, occupied: MutableList<RectF>) {
        val text = s.text ?: return
        val pts = s.points.map(map)
        val ts = (s.size * unit * 0.45f).coerceIn(11f * textScale, 18f * textScale)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = ts; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val halo = Paint(fill).apply { color = 0xE6000000.toInt(); style = Paint.Style.STROKE; strokeWidth = ts * 0.18f; strokeJoin = Paint.Join.ROUND }
        val tw = fill.measureText(text); val gap = ts * 0.35f
        val kind = s.shape ?: "dot"
        fun drawAt(x: Float, baseline: Float) {
            c.drawText(text, x, baseline, halo); c.drawText(text, x, baseline, fill)
            occupied += RectF(x, baseline - ts, x + tw, baseline + ts * 0.25f)
        }
        if (kind == "line" && pts.size >= 2) {
            // encima o debajo de la línea, con su ángulo (texto siempre legible, no al revés)
            var a = pts.first(); var b = pts.last()
            if (b.x < a.x) { val t = a; a = b; b = t }
            val ang = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
            val below = s.side == "bottom"
            c.save(); c.rotate(ang, mx, my)
            val base = if (below) my + (s.width * unit) / 2 + gap + ts * 0.8f else my - (s.width * unit) / 2 - gap
            c.drawText(text, mx - tw / 2, base, halo); c.drawText(text, mx - tw / 2, base, fill)
            c.restore()
            return
        }
        // flecha: el texto en el origen (al otro lado de la dirección, salvo que se elija un lado)
        val r = if (kind == "arrow") RectF(pts[0].x - 2f, pts[0].y - 2f, pts[0].x + 2f, pts[0].y + 2f) else symbolRect(s, unit, map)
        val arrowSide = if (kind == "arrow" && pts.size >= 2) {
            val dx = pts.last().x - pts[0].x; val dy = pts.last().y - pts[0].y
            if (abs(dx) >= abs(dy)) (if (dx > 0) "left" else "right") else (if (dy > 0) "top" else "bottom")
        } else null
        fun place(side: String): Pair<Float, Float> = when (side) {
            "left" -> (r.left - gap - tw) to (r.centerY() + ts * 0.35f)
            "top" -> (r.centerX() - tw / 2) to (r.top - gap)
            "bottom" -> (r.centerX() - tw / 2) to (r.bottom + gap + ts * 0.8f)
            else -> (r.right + gap) to (r.centerY() + ts * 0.35f)     // derecha: en la misma línea
        }
        val wanted = s.side ?: "auto"
        val side = if (wanted == "auto") {
            val order = listOfNotNull(arrowSide, "right", "left", "top", "bottom").distinct()
            order.firstOrNull { sd ->
                val (x, y) = place(sd)
                val box = RectF(x, y - ts, x + tw, y + ts * 0.25f)
                occupied.none { o -> o !== r && RectF.intersects(o, box) && !(o.left == r.left && o.top == r.top && o.right == r.right && o.bottom == r.bottom) }
            } ?: order.first()
        } else wanted
        val (x, y) = place(side)
        drawAt(x, y)
    }

    /** Contorno de un trazo (coordenadas de la foto), para el borrador por trazo y la selección. */
    fun outline(s: InkStroke): List<Pt> {
        if (s.kind != InkStroke.SYMBOL) return s.points
        val kind = s.shape ?: "dot"
        if (kind == "line" || kind == "arrow") return if (s.points.size >= 2) listOf(s.points.first(), s.points.last()) else s.points
        if (s.points.size < 2) return s.points   // marcador: un punto con su radio
        val a = s.points.first(); val b = s.points.last()
        val l = minOf(a.x, b.x); val t = minOf(a.y, b.y); val rr = maxOf(a.x, b.x); val bb = maxOf(a.y, b.y)
        return when (kind) {
            "circle", "dot", "star" -> { val cx = (l + rr) / 2; val cy = (t + bb) / 2
                (0..32).map { k -> val ang = 2 * Math.PI * k / 32; Pt(cx + (rr - l) / 2 * cos(ang).toFloat(), cy + (bb - t) / 2 * sin(ang).toFloat()) } }
            "triangle" -> listOf(Pt((l + rr) / 2, t), Pt(rr, bb), Pt(l, bb), Pt((l + rr) / 2, t))
            else -> listOf(Pt(l, t), Pt(rr, t), Pt(rr, bb), Pt(l, bb), Pt(l, t))
        }
    }

    /** ¿El símbolo tiene interior (se selecciona o borra tocando dentro)? */
    fun isArea(s: InkStroke) = s.kind == InkStroke.SYMBOL && (s.shape ?: "dot") in AREA
}
