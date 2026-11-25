package com.photomed.app.align

import android.content.Context
import android.net.Uri
import com.photomed.app.data.PhotoLocks
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.XmpFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Trazo de anotación (planificación del tratamiento) sobre una foto, sin modificarla: [points] en la
 * foto original (x, y normalizadas 0..1, como los trazos confirmados, ver [TraceMap]); [width] en
 * fracción del ancho de la original; [erase] = borrador tipo pintura (quita lo dibujado debajo).
 */
data class InkStroke(val color: Int, val width: Float, val points: List<Pt>, val erase: Boolean = false,
                     /** "free" (mano alzada) o "symbol" (marcador en un punto o, arrastrado, de un extremo a otro; con texto opcional). */
                     val kind: String = FREE,
                     /** Símbolo ([SYMBOLS]). */
                     val shape: String? = null,
                     /** Texto del símbolo y lado donde va: "top", "bottom", "left", "right". */
                     val text: String? = null, val side: String? = null,
                     /** Tamaño del símbolo (fracción del ancho, como [width]). */
                     val size: Float = 0f) {
    companion object {
        const val FREE = "free"; const val SYMBOL = "symbol"
        /** Antiguas «formas» (2.32): se leen como símbolos arrastrados. */
        const val LEGACY_SHAPE = "shape"
        val SYMBOLS = listOf("dot", "circle", "triangle", "square", "x", "plus", "star", "line", "arrow", "polyline")
    }
}

/** Anotaciones de una foto; [baseAspect]: alto / ancho de la original. */
data class InkSet(val baseAspect: Float, val strokes: List<InkStroke> = emptyList()) {
    /** Trazos llevados a [frame] con la edición [e]: puntos y grosor en unidades del ancho del encuadre. */
    fun inView(e: ImageEdit, frame: TraceFrame): List<InkStroke> = strokes.map { s ->
        val pts = TraceMap.toView(e, baseAspect, frame, s.points)
        // grosor: un segmento horizontal de ese ancho en la original, medido en el encuadre
        val p0 = s.points.firstOrNull() ?: Pt(0.5f, 0.5f)
        val (a, b) = TraceMap.toView(e, baseAspect, frame, listOf(p0, Pt(p0.x + s.width, p0.y)))
        val k = hypot(b.x - a.x, b.y - a.y) / s.width.coerceAtLeast(1e-6f)
        s.copy(points = pts, width = s.width * k, size = s.size * k, side = s.side?.let { rotateSide(it, e) })
    }
}

/** Lado del texto de un símbolo tras girar / espejar la foto (el texto queda del mismo lado del punto). */
fun rotateSide(side: String, e: ImageEdit): String {
    val order = listOf("top", "right", "bottom", "left")
    var i = order.indexOf(side).takeIf { it >= 0 } ?: return side
    i = (i + e.rot % 4) % 4
    var out = order[i]
    if (e.mirror) out = when (out) { "left" -> "right"; "right" -> "left"; else -> out }
    return out
}

/**
 * Anotaciones guardadas por foto: copia de la app (`files/anotaciones/`) y en el XMP de la imagen
 * (`pm:Ink`, si cabe), como los trazos confirmados. Son una capa aparte: la imagen no cambia.
 */
object Annotations {
    private fun dir(context: Context) = File(context.filesDir, "anotaciones").apply { mkdirs() }
    private fun key(uri: String) = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(context: Context, uri: String) = File(dir(context), key(uri) + ".json")
    const val XMP_MAX = 40_000

    fun write(s: InkSet): String = JSONObject().put("v", 1).put("aspect", s.baseAspect.toDouble()).put("strokes", JSONArray().apply {
        s.strokes.forEach { k ->
            put(JSONObject().put("c", k.color).put("w", (k.width * 100000f).roundToInt() / 100000.0).put("e", k.erase)
                .put("p", JSONArray().apply { k.points.forEach { put((it.x * 10000f).roundToInt() / 10000.0); put((it.y * 10000f).roundToInt() / 10000.0) } })
                .apply {
                    if (k.kind != InkStroke.FREE) put("k", k.kind)
                    k.shape?.let { put("s", it) }; k.text?.let { put("t", it) }; k.side?.let { put("d", it) }
                    if (k.size > 0f) put("z", (k.size * 100000f).roundToInt() / 100000.0)
                })
        }
    }).toString()

    fun read(json: String): InkSet? = runCatching {
        val o = JSONObject(json); val a = o.getJSONArray("strokes")
        InkSet(o.getDouble("aspect").toFloat(), (0 until a.length()).map { i ->
            val k = a.getJSONObject(i); val p = k.getJSONArray("p")
            val pts = (0 until p.length() / 2).map { j -> Pt(p.getDouble(2 * j).toFloat(), p.getDouble(2 * j + 1).toFloat()) }
            val kind = k.optString("k", InkStroke.FREE); val shape = k.optString("s").ifEmpty { null }
            if (kind == InkStroke.LEGACY_SHAPE) {
                // formas de 2.32 → símbolos arrastrados (círculo por diámetro → su recuadro; rectángulo → cuadrado; elipse → círculo)
                val (sym, sp) = when (shape) {
                    "rect" -> "square" to pts
                    "ellipse" -> "circle" to pts
                    "circle" -> "circle" to if (pts.size >= 2) {
                        val cx = (pts.first().x + pts.last().x) / 2; val cy = (pts.first().y + pts.last().y) / 2
                        val r = hypot(pts.last().x - pts.first().x, pts.last().y - pts.first().y) / 2
                        listOf(Pt(cx - r, cy - r), Pt(cx + r, cy + r))
                    } else pts
                    else -> (shape ?: "line") to pts
                }
                InkStroke(k.getInt("c"), k.getDouble("w").toFloat(), sp, k.optBoolean("e"), InkStroke.SYMBOL, sym)
            } else
            InkStroke(k.getInt("c"), k.getDouble("w").toFloat(), pts,
                k.optBoolean("e"), kind, shape, k.optString("t").ifEmpty { null },
                k.optString("d").ifEmpty { null }, k.optDouble("z", 0.0).toFloat())
        })
    }.getOrNull()

    /** Marca de borrado (ver [ConfirmedTraces]): las anotaciones borradas no se recuperan del XMP si no se pudo reescribir. */
    private fun tombstone(context: Context, uri: String) = File(file(context, uri).parentFile, file(context, uri).name + ".removed")

    fun of(context: Context, uri: String): InkSet? = PhotoLocks.on(uri) {
        val f = file(context, uri)
        if (f.exists()) return@on read(f.readText())
        if (tombstone(context, uri).exists()) return@on null
        // otra instalación: del XMP de la foto
        PhotoMetadata.attrOf(XmpFile.read(context, Uri.parse(uri)), "Ink")?.let { j -> read(j)?.also { runCatching { AtomicFile.write(f, j) } } }
    }

    fun tag(context: Context, uri: String) = file(context, uri).let { if (it.exists()) "${it.lastModified()}-${it.length()}" else "-" }

    /** Guarda (vacío = se borran). Devuelve si se pudo escribir en la foto. */
    fun save(context: Context, uri: String, s: InkSet): Boolean = PhotoLocks.on(uri) {
        val f = file(context, uri)
        if (s.strokes.isEmpty()) {
            f.delete()
            val ok = XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withAttr(it, "Ink", null) }
            if (!ok) runCatching { tombstone(context, uri).writeText("1") }
            return@on ok
        }
        val json = write(s)
        val wrote = runCatching { AtomicFile.write(f, json) }
            .onFailure { com.photomed.app.CrashLog.write("No se pudieron guardar las anotaciones", it) }.isSuccess
        if (wrote) tombstone(context, uri).delete()
        wrote && json.length <= XMP_MAX && XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withAttr(it, "Ink", json) }
    }

    /**
     * Borrador por trazo: quita los trazos que toca [path] (en la original; [radius] en fracción del
     * ancho). Se mide contra la forma completa (segmentos del trazo y del contorno del símbolo, y su
     * interior si tiene área), y el recorrido del borrador como segmentos (no solo sus puntos).
     */
    fun eraseStrokes(s: InkSet, path: List<Pt>, radius: Float): InkSet =
        s.copy(strokes = s.strokes.filterNot { k -> !k.erase && touches(k, path, radius, s.baseAspect) })

    /** ¿El recorrido [path] toca el trazo [k]? (coordenadas de la original; [aspect]: alto / ancho). */
    fun touches(k: InkStroke, path: List<Pt>, radius: Float, aspect: Float): Boolean {
        fun iso(p: Pt) = Pt(p.x, p.y * aspect)
        val pts = InkRenderer.outline(k).map(::iso); val qs = path.map(::iso)
        if (pts.isEmpty() || qs.isEmpty()) return false
        val lim = radius + (if (k.kind == InkStroke.SYMBOL && pts.size == 1) maxOf(k.size, k.width) else k.width) / 2
        val a = if (pts.size == 1) listOf(pts[0] to pts[0]) else pts.zipWithNext()
        val b = if (qs.size == 1) listOf(qs[0] to qs[0]) else qs.zipWithNext()
        if (a.any { (p0, p1) -> b.any { (q0, q1) -> Geo.segSeg(p0, p1, q0, q1) < lim } }) return true
        // dentro de un símbolo con área (cuadrado, círculo…)
        return InkRenderer.isArea(k) && pts.size > 2 && qs.any { Geo.inside(it, pts) }
    }

    /** Índice del trazo que toca el punto [q] (el de encima primero), o -1. Coordenadas de [strokes]. */
    fun hit(strokes: List<InkStroke>, q: Pt, radius: Float, aspect: Float = 1f): Int =
        strokes.indices.reversed().firstOrNull { !strokes[it].erase && touches(strokes[it], listOf(q), radius, aspect) } ?: -1
}

/** Geometría de segmentos para el borrador y la selección. */
object Geo {
    fun segPoint(p: Pt, a: Pt, b: Pt): Float {
        val vx = b.x - a.x; val vy = b.y - a.y; val l2 = vx * vx + vy * vy
        val t = if (l2 < 1e-12f) 0f else (((p.x - a.x) * vx + (p.y - a.y) * vy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    /** Distancia mínima entre los segmentos ab y cd (0 si se cruzan). */
    fun segSeg(a: Pt, b: Pt, c: Pt, d: Pt): Float {
        fun cross(o: Pt, p: Pt, q: Pt) = (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
        val d1 = cross(c, d, a); val d2 = cross(c, d, b); val d3 = cross(a, b, c); val d4 = cross(a, b, d)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return 0f
        return minOf(minOf(segPoint(a, c, d), segPoint(b, c, d)), minOf(segPoint(c, a, b), segPoint(d, a, b)))
    }

    fun inside(q: Pt, poly: List<Pt>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            if ((poly[i].y > q.y) != (poly[j].y > q.y) && q.x < (poly[j].x - poly[i].x) * (q.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x) c = !c
            j = i
        }
        return c
    }
}
