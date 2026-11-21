package com.photomed.app.align

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Dibujo de las anotaciones con el Canvas de Android (el mismo en pantalla y al exportar): trazos a mano
 * alzada, símbolos con su texto y formas. El borrador tipo pintura se dibuja con CLEAR en una capa propia
 * (no borra la foto ni el diagrama).
 */
object InkRenderer {
    /** [strokes] en coordenadas de [map] (unidades del ancho) con grosores en esas unidades; [unit]: px por unidad. */
    fun draw(c: Canvas, strokes: List<InkStroke>, unit: Float, map: (Pt) -> PointF, textScale: Float = 1f) {
        if (strokes.isEmpty()) return
        val layer = c.saveLayer(null, null)
        for (s in strokes) drawOne(c, s, unit, map, textScale)
        c.restoreToCount(layer)
    }

    fun drawOne(c: Canvas, s: InkStroke, unit: Float, map: (Pt) -> PointF, textScale: Float = 1f) {
        if (s.points.isEmpty()) return
        val w = (s.width * unit).coerceAtLeast(1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.color; strokeWidth = w; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val pts = s.points.map(map)
        when (s.kind) {
            InkStroke.SYMBOL -> symbol(c, s.shape ?: "dot", pts[0], (s.size * unit).coerceAtLeast(6f), p, s.text, s.side, textScale)
            InkStroke.SHAPE -> if (pts.size >= 2) shape(c, s.shape ?: "line", pts.first(), pts.last(), p)
            else -> if (pts.size == 1) c.drawCircle(pts[0].x, pts[0].y, w / 2, Paint(p).apply { style = Paint.Style.FILL })
                else c.drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, p)
        }
    }

    private fun symbol(c: Canvas, kind: String, at: PointF, size: Float, p: Paint, text: String?, side: String?, textScale: Float) {
        val r = size / 2
        val sp = Paint(p).apply { strokeWidth = (size * 0.14f).coerceAtLeast(2f) }
        when (kind) {
            "dot" -> c.drawCircle(at.x, at.y, r * 0.6f, Paint(sp).apply { style = Paint.Style.FILL })
            "circle" -> c.drawCircle(at.x, at.y, r, sp)
            "square" -> c.drawRect(at.x - r, at.y - r, at.x + r, at.y + r, sp)
            "triangle" -> c.drawPath(Path().apply { moveTo(at.x, at.y - r); lineTo(at.x + r * 0.95f, at.y + r * 0.75f); lineTo(at.x - r * 0.95f, at.y + r * 0.75f); close() }, sp)
            "x" -> { c.drawLine(at.x - r, at.y - r, at.x + r, at.y + r, sp); c.drawLine(at.x - r, at.y + r, at.x + r, at.y - r, sp) }
            "plus" -> { c.drawLine(at.x - r, at.y, at.x + r, at.y, sp); c.drawLine(at.x, at.y - r, at.x, at.y + r, sp) }
            "star" -> c.drawPath(Path().apply {
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5; val rr = if (i % 2 == 0) r else r * 0.45f
                    val x = at.x + (rr * cos(a)).toFloat(); val y = at.y + (rr * sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }, sp)
        }
        if (text.isNullOrBlank() || p.xfermode != null) return
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = (size * 0.8f).coerceAtLeast(12f * textScale); typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val tw = tp.measureText(text); val th = tp.textSize; val gap = r + th * 0.4f
        val (x, y) = when (side ?: "right") {
            "top" -> at.x - tw / 2 to at.y - gap
            "bottom" -> at.x - tw / 2 to at.y + gap + th * 0.8f
            "left" -> at.x - gap - tw to at.y + th * 0.35f
            else -> at.x + gap to at.y + th * 0.35f
        }
        val pad = th * 0.25f
        c.drawRoundRect(RectF(x - pad, y - th * 0.9f, x + tw + pad, y + th * 0.3f), pad, pad,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC202020.toInt() })
        c.drawText(text, x, y, tp)
        // borde del color del símbolo bajo el texto
        c.drawLine(x - pad, y + th * 0.3f, x + tw + pad, y + th * 0.3f, Paint(p).apply { strokeWidth = (th * 0.1f).coerceAtLeast(2f) })
    }

    private fun shape(c: Canvas, kind: String, a: PointF, b: PointF, p: Paint) {
        val l = minOf(a.x, b.x); val t = minOf(a.y, b.y); val rgt = maxOf(a.x, b.x); val btm = maxOf(a.y, b.y)
        when (kind) {
            "line" -> c.drawLine(a.x, a.y, b.x, b.y, p)
            "arrow" -> {
                c.drawLine(a.x, a.y, b.x, b.y, p)
                val ang = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                val hl = (hypot(b.x - a.x, b.y - a.y) * 0.25f).coerceIn(p.strokeWidth * 3f, p.strokeWidth * 8f)
                for (d in listOf(Math.PI * 0.85, -Math.PI * 0.85)) {
                    c.drawLine(b.x, b.y, b.x + (hl * cos(ang + d)).toFloat(), b.y + (hl * sin(ang + d)).toFloat(), p)
                }
            }
            "circle" -> c.drawCircle((a.x + b.x) / 2, (a.y + b.y) / 2, hypot(b.x - a.x, b.y - a.y) / 2, p)
            "rect" -> c.drawRect(l, t, rgt, btm, p)
            "ellipse" -> c.drawOval(RectF(l, t, rgt, btm), p)
        }
    }

    /** Puntos del contorno de un trazo (para el borrador por trazo), en sus mismas coordenadas. */
    fun outline(s: InkStroke): List<Pt> {
        if (s.kind != InkStroke.SHAPE || s.points.size < 2) return s.points
        val a = s.points.first(); val b = s.points.last()
        return when (s.shape) {
            "line", "arrow" -> s.points
            "rect" -> listOf(Pt(a.x, a.y), Pt(b.x, a.y), Pt(b.x, b.y), Pt(a.x, b.y), Pt(a.x, a.y))
            else -> { // círculo y elipse
                val cx = (a.x + b.x) / 2; val cy = (a.y + b.y) / 2
                val rx = if (s.shape == "circle") hypot(b.x - a.x, b.y - a.y) / 2 else kotlin.math.abs(b.x - a.x) / 2
                val ry = if (s.shape == "circle") rx else kotlin.math.abs(b.y - a.y) / 2
                (0..32).map { k -> val t = 2 * Math.PI * k / 32; Pt(cx + rx * cos(t).toFloat(), cy + ry * sin(t).toFloat()) }
            }
        }
    }
}
