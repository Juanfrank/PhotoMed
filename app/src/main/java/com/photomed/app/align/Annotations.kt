package com.photomed.app.align

import android.content.Context
import android.net.Uri
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
data class InkStroke(val color: Int, val width: Float, val points: List<Pt>, val erase: Boolean = false)

/** Anotaciones de una foto; [baseAspect]: alto / ancho de la original. */
data class InkSet(val baseAspect: Float, val strokes: List<InkStroke> = emptyList()) {
    /** Trazos llevados a [frame] con la edición [e]: puntos y grosor en unidades del ancho del encuadre. */
    fun inView(e: ImageEdit, frame: TraceFrame): List<InkStroke> = strokes.map { s ->
        val pts = TraceMap.toView(e, baseAspect, frame, s.points)
        // grosor: un segmento horizontal de ese ancho en la original, medido en el encuadre
        val p0 = s.points.firstOrNull() ?: Pt(0.5f, 0.5f)
        val (a, b) = TraceMap.toView(e, baseAspect, frame, listOf(p0, Pt(p0.x + s.width, p0.y)))
        s.copy(points = pts, width = hypot(b.x - a.x, b.y - a.y))
    }
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
                .put("p", JSONArray().apply { k.points.forEach { put((it.x * 10000f).roundToInt() / 10000.0); put((it.y * 10000f).roundToInt() / 10000.0) } }))
        }
    }).toString()

    fun read(json: String): InkSet? = runCatching {
        val o = JSONObject(json); val a = o.getJSONArray("strokes")
        InkSet(o.getDouble("aspect").toFloat(), (0 until a.length()).map { i ->
            val k = a.getJSONObject(i); val p = k.getJSONArray("p")
            InkStroke(k.getInt("c"), k.getDouble("w").toFloat(), (0 until p.length() / 2).map { j -> Pt(p.getDouble(2 * j).toFloat(), p.getDouble(2 * j + 1).toFloat()) },
                k.optBoolean("e"))
        })
    }.getOrNull()

    fun of(context: Context, uri: String): InkSet? {
        val f = file(context, uri)
        if (f.exists()) return read(f.readText())
        // otra instalación: del XMP de la foto
        return PhotoMetadata.attrOf(XmpFile.read(context, Uri.parse(uri)), "Ink")?.let { j -> read(j)?.also { f.writeText(j) } }
    }

    fun tag(context: Context, uri: String) = file(context, uri).let { if (it.exists()) "${it.lastModified()}-${it.length()}" else "-" }

    /** Guarda (vacío = se borran). Devuelve si se pudo escribir en la foto. */
    fun save(context: Context, uri: String, s: InkSet): Boolean {
        val f = file(context, uri)
        if (s.strokes.isEmpty()) {
            f.delete()
            return XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withAttr(it, "Ink", null) }
        }
        val json = write(s)
        f.writeText(json)
        return json.length <= XMP_MAX && XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withAttr(it, "Ink", json) }
    }

    /** Borrador por trazo: quita los trazos de lápiz que toca [path] (en la original; [radius] en fracción del ancho). */
    fun eraseStrokes(s: InkSet, path: List<Pt>, radius: Float): InkSet {
        // distancias en unidades del ancho de la original (y × alto/ancho), a los segmentos del trazo
        fun iso(p: Pt) = Pt(p.x, p.y * s.baseAspect)
        fun segDist(q: Pt, a: Pt, b: Pt): Float {
            val vx = b.x - a.x; val vy = b.y - a.y; val l2 = vx * vx + vy * vy
            val t = if (l2 < 1e-12f) 0f else (((q.x - a.x) * vx + (q.y - a.y) * vy) / l2).coerceIn(0f, 1f)
            return hypot(q.x - (a.x + vx * t), q.y - (a.y + vy * t))
        }
        fun touches(k: InkStroke): Boolean {
            val pts = k.points.map(::iso); val qs = path.map(::iso); val lim = radius + k.width / 2
            return qs.any { q -> if (pts.size == 1) hypot(q.x - pts[0].x, q.y - pts[0].y) < lim
                else (1 until pts.size).any { segDist(q, pts[it - 1], pts[it]) < lim } }
        }
        return s.copy(strokes = s.strokes.filterNot { k -> !k.erase && touches(k) })
    }
}
