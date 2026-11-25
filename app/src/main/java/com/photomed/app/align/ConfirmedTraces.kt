package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.XmpFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Sistema de coordenadas de las líneas que se muestran sobre una foto:
 * - [FULL]: la foto completa con su edición (visor), en unidades de su ancho;
 * - [C34]: el encuadre 3:4 centrado de la foto editada (Comparar), en unidades de su ancho.
 */
enum class TraceFrame { FULL, C34 }

/**
 * Lleva puntos entre la **foto original** (sin editar; x, y normalizados 0..1 por su ancho y su alto)
 * y la foto con su edición (giro de 90°, espejo, recorte) en cada [TraceFrame]. Así los trazos
 * guardados siguen en su sitio aunque después se gire, espeje o recorte la foto, y el zoom del visor
 * se deshace al convertir el toque (ver screenToPhoto).
 */
object TraceMap {
    /** Alto / ancho de la foto editada a partir del de la original [a0]. */
    fun editedAspect(e: ImageEdit, a0: Float): Float {
        var a = if (e.rot % 2 == 1) 1f / a0 else a0
        e.crop?.let { c -> a *= (c[3] - c[1]) / (c[2] - c[0]).coerceAtLeast(1e-4f) }
        return a
    }

    /** Alto / ancho de la original a partir del de la editada [aE]. */
    fun baseAspect(e: ImageEdit, aE: Float): Float {
        var a = aE
        e.crop?.let { c -> a /= ((c[3] - c[1]) / (c[2] - c[0]).coerceAtLeast(1e-4f)).coerceAtLeast(1e-4f) }
        return if (e.rot % 2 == 1) 1f / a else a
    }

    /** Original normalizada → editada normalizada. */
    fun forward(e: ImageEdit, p: Pt): Pt {
        var x = p.x; var y = p.y
        repeat(e.rot % 4) { val nx = 1f - y; y = x; x = nx }       // giro horario: (x, y) → (1 − y, x)
        if (e.mirror) x = 1f - x
        e.crop?.let { c -> x = (x - c[0]) / (c[2] - c[0]); y = (y - c[1]) / (c[3] - c[1]) }
        return Pt(x, y)
    }

    /** Editada normalizada → original normalizada. */
    fun inverse(e: ImageEdit, p: Pt): Pt {
        var x = p.x; var y = p.y
        e.crop?.let { c -> x = c[0] + x * (c[2] - c[0]); y = c[1] + y * (c[3] - c[1]) }
        if (e.mirror) x = 1f - x
        repeat(e.rot % 4) { val nx = y; y = 1f - x; x = nx }       // inverso: (x', y') → (y', 1 − x')
        return Pt(x, y)
    }

    /** Editada normalizada → [frame] (unidades del ancho). [aE]: alto / ancho de la editada. */
    fun toFrame(p: Pt, aE: Float, frame: TraceFrame): Pt {
        val q = Pt(p.x, p.y * aE)
        if (frame == TraceFrame.FULL) return q
        return if (aE >= 4f / 3f) Pt(q.x, q.y - (aE - 4f / 3f) / 2)         // se recorta arriba y abajo
        else { val cw = aE * 0.75f; Pt((q.x - (1 - cw) / 2) / cw, q.y / cw) } // se recorta a los lados
    }

    fun fromFrame(q: Pt, aE: Float, frame: TraceFrame): Pt {
        val p = if (frame == TraceFrame.FULL) q
            else if (aE >= 4f / 3f) Pt(q.x, q.y + (aE - 4f / 3f) / 2)
            else { val cw = aE * 0.75f; Pt(q.x * cw + (1 - cw) / 2, q.y * cw) }
        return Pt(p.x, p.y / aE)
    }

    /** Original → [frame] de la foto con la edición [e]. */
    fun toView(e: ImageEdit, a0: Float, frame: TraceFrame, pts: List<Pt>): List<Pt> {
        val aE = editedAspect(e, a0)
        return pts.map { toFrame(forward(e, it), aE, frame) }
    }

    /** [frame] de la foto con la edición [e] → original. */
    fun fromView(e: ImageEdit, a0: Float, frame: TraceFrame, pts: List<Pt>): List<Pt> {
        val aE = editedAspect(e, a0)
        return pts.map { inverse(e, fromFrame(it, aE, frame)) }
    }
}

/**
 * Trazo confirmado, en coordenadas de la foto original ([TraceMap]). [origin]: "auto" (detectado),
 * "añadido" (trazado a mano) o "editado" (vuelto a trazar); [previous]: la versión anterior a la
 * edición (para revertirla).
 */
data class StoredTrace(val layer: AnnotLayer, val points: List<Pt>, val closed: Boolean, val filled: Boolean,
                       val estimated: Boolean, val metrics: LineMetrics?, val origin: String = "auto",
                       val previous: StoredTrace? = null,
                       /** Segmentos medidos (coordenadas de la original). */
                       val guides: List<MeasureGuide> = emptyList()) {
    val name: String? get() = metrics?.name
}

/**
 * Conjunto de trazos confirmados de una foto: lo que se muestra y se mide sin volver a analizarla
 * (reproducible). [baseAspect]: alto / ancho de la foto original; [removed]: eliminados (falsos
 * positivos), para poder restaurarlos.
 */
data class TraceSet(val baseAspect: Float, val traces: List<StoredTrace>, val removed: List<StoredTrace> = emptyList(),
                    val confirmedAt: Long = System.currentTimeMillis()) {
    fun lines(e: ImageEdit, frame: TraceFrame): List<AnnotLine> = traces.map { t ->
        AnnotLine(t.layer, TraceMap.toView(e, baseAspect, frame, t.points), t.closed, t.estimated, t.filled, t.metrics,
            t.guides.map { g -> TraceMap.toView(e, baseAspect, frame, listOf(g.a, g.b)).let { (a, b) -> g.copy(a = a, b = b) } })
    }
    fun byName(name: String) = traces.firstOrNull { it.name == name }
    val editedNames: Set<String> get() = traces.filter { it.previous != null }.mapNotNull { it.name }.toSet()
}

/** JSON compacto de los trazos (4 decimales) y de sus medidas. */
object TraceJson {
    // valores no finitos (NaN de una medida degenerada): no se guardan (antes cerraban la app al redondear)
    private fun r(v: Float) = if (v.isFinite()) (v * 10000f).roundToInt() / 10000.0 else 0.0
    private fun pts(p: List<Pt>) = JSONArray().apply { p.filter { it.x.isFinite() && it.y.isFinite() }.forEach { put(r(it.x)); put(r(it.y)) } }
    private fun ptsOf(a: JSONArray) = (0 until a.length() / 2).map { Pt(a.getDouble(2 * it).toFloat(), a.getDouble(2 * it + 1).toFloat()) }

    fun metrics(m: LineMetrics): JSONObject = JSONObject().apply {
        put("name", m.name); put("scale", m.scale); put("depthSource", m.depthSource)
        fun f(k: String, v: Float?) { if (v != null && v.isFinite()) put(k, (v * 1000f).roundToInt() / 1000.0) }
        f("len", m.lengthMm); f("width", m.widthMm); f("contrast", m.contrastL); f("depth", m.depthMm)
        m.grade?.let { put("grade", it) }; f("area", m.areaMm2); f("dL", m.deltaL); f("dE", m.deltaE); f("ita", m.ita); f("itaRef", m.itaRef)
        m.type?.let { put("type", it) }; m.note?.let { put("note", it) }
        if (m.features.isNotEmpty()) put("features", JSONArray().apply { m.features.filter { it.second.isFinite() }.forEach { (k, v) -> put(JSONArray().put(k).put((v * 1000f).roundToInt() / 1000.0)) } })
    }

    fun metricsOf(o: JSONObject): LineMetrics {
        fun f(k: String) = if (o.has(k)) o.getDouble(k).toFloat() else null
        return LineMetrics(o.getString("name"), f("len"), f("width"), f("contrast"), f("depth"),
            if (o.has("grade")) o.getInt("grade") else null, f("area"), f("dL"), f("dE"), f("ita"), f("itaRef"),
            o.optString("type").ifEmpty { null }, o.optString("scale", "iris"), o.optString("depthSource", "sombreado"),
            o.optString("note").ifEmpty { null },
            o.optJSONArray("features")?.let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { it.getString(0) to it.getDouble(1).toFloat() } } }.orEmpty())
    }

    private fun trace(t: StoredTrace): JSONObject = JSONObject().put("layer", t.layer.name).put("pts", pts(t.points))
        .put("closed", t.closed).put("filled", t.filled).put("est", t.estimated).put("origin", t.origin).apply {
            t.metrics?.let { put("m", metrics(it)) }
            t.previous?.let { put("prev", trace(it.copy(previous = null))) }
            val fin = { q: Pt -> q.x.isFinite() && q.y.isFinite() }
            if (t.guides.isNotEmpty()) put("guides", JSONArray().apply { t.guides.filter { fin(it.a) && fin(it.b) }.forEach { g -> put(JSONObject().put("l", g.label).put("p", pts(listOf(g.a, g.b))).put("k", g.key)) } })
        }

    private fun traceOf(o: JSONObject): StoredTrace = StoredTrace(AnnotLayer.valueOf(o.getString("layer")), ptsOf(o.getJSONArray("pts")),
        o.optBoolean("closed"), o.optBoolean("filled"), o.optBoolean("est"), o.optJSONObject("m")?.let(::metricsOf),
        o.optString("origin", "auto"), o.optJSONObject("prev")?.let(::traceOf),
        o.optJSONArray("guides")?.let { a -> (0 until a.length()).map { i -> val g = a.getJSONObject(i); val p = ptsOf(g.getJSONArray("p")); MeasureGuide(g.getString("l"), p[0], p[1], g.optString("k")) } }.orEmpty())

    fun write(s: TraceSet): String = JSONObject().put("v", 1).put("aspect", r(s.baseAspect)).put("at", s.confirmedAt)
        .put("traces", JSONArray().apply { s.traces.forEach { put(trace(it)) } })
        .put("removed", JSONArray().apply { s.removed.forEach { put(trace(it)) } }).toString()

    fun read(json: String): TraceSet? = runCatching {
        val o = JSONObject(json)
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { traceOf(a.getJSONObject(it)) } }.orEmpty()
        // versiones anteriores: tramos enlazados a su fila y los que salen del propio trazo
        GuideMigration.migrate(TraceSet(o.getDouble("aspect").toFloat(), list("traces"), list("removed"), o.optLong("at")))
    }.getOrNull()
}

/**
 * Trazos confirmados de cada foto. Se guardan en el XMP de la propia imagen (`pm:Traces`), para que
 * viajen con ella y se puedan reutilizar, y en una copia privada de la app (lectura rápida; y por si
 * la galería no deja reescribir la foto). Mientras existan, la foto no se vuelve a analizar: solo se
 * regeneran si el usuario lo pide.
 */
object ConfirmedTraces {
    private val mem = HashMap<String, Pair<Long, TraceSet?>>()
    private fun dir(context: Context) = File(context.filesDir, "trazos").apply { mkdirs() }
    private fun key(uri: String) = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(context: Context, uri: String) = File(dir(context), key(uri) + ".json")

    /** Tamaño máximo del JSON en el XMP (un bloque APP1 de JPEG admite ~64 kB). */
    const val XMP_MAX = 48_000

    /** Conjunto confirmado de la foto, o null. Si solo está en el XMP (otra instalación), se recupera. */
    fun of(context: Context, uri: String): TraceSet? {
        val f = file(context, uri)
        synchronized(mem) { mem[uri]?.let { (stamp, s) -> if (stamp == f.lastModified()) return s } }
        val s = if (f.exists()) TraceJson.read(f.readText())
            else PhotoMetadata.tracesOf(XmpFile.read(context, Uri.parse(uri)))?.let { j -> TraceJson.read(j)?.also { AtomicFile.write(f, j) } }
        synchronized(mem) { mem[uri] = f.lastModified() to s }
        return s
    }

    /** Etiqueta para las claves de caché (cambia al confirmar, editar o regenerar). */
    fun tag(context: Context, uri: String): String = file(context, uri).let { if (it.exists()) "${it.lastModified()}-${it.length()}" else "-" }

    /** Guarda el conjunto (copia privada y XMP de la foto). Devuelve si se pudo escribir en la foto. */
    /** Guarda los trazos; false (sin cerrar la app) si no se pudieron guardar, con la causa en el registro. */
    fun save(context: Context, uri: String, s: TraceSet): Boolean = runCatching { saveOrThrow(context, uri, s) }
        .onFailure { com.photomed.app.CrashLog.write("No se pudieron guardar los trazos", it) }.getOrDefault(false)

    private fun saveOrThrow(context: Context, uri: String, s: TraceSet): Boolean {
        val json = TraceJson.write(s)
        val f = file(context, uri)
        AtomicFile.write(f, json)
        // marca de tiempo distinta aunque se guarde dos veces en el mismo milisegundo (claves de caché)
        f.setLastModified(maxOf(System.currentTimeMillis(), (mem[uri]?.first ?: 0L) + 1000))
        synchronized(mem) { mem[uri] = f.lastModified() to s }
        return json.length <= XMP_MAX && XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withTraces(it, json) }
    }

    /** Al eliminar la foto: solo la copia privada (la foto y su XMP se borran aparte). */
    fun forget(context: Context, uri: String) { file(context, uri).delete(); synchronized(mem) { mem.remove(uri) } }

    /** Regenerar: se borran los trazos confirmados (y las correcciones antiguas) de la foto. */
    fun clear(context: Context, uri: String) {
        // primero la foto: si se borrara antes la copia de la app, una lectura intermedia la recuperaría del XMP
        XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withTraces(it, null) }
        synchronized(mem) { file(context, uri).delete(); mem.remove(uri) }
        AnnotUserEdits.delete(context, uri)
    }

    /** Confirma las líneas [lines] (en [frame] con la edición [e]; [aE]: alto / ancho de la foto editada). */
    fun fromLines(lines: List<AnnotLine>, e: ImageEdit, aE: Float, frame: TraceFrame): TraceSet {
        val a0 = TraceMap.baseAspect(e, aE)
        return TraceSet(a0, lines.map { l ->
            StoredTrace(l.layer, TraceMap.fromView(e, a0, frame, l.points), l.closed, l.filled, l.estimated, l.metrics,
                if (l.metrics?.name?.startsWith("Surco añadido") == true) "añadido" else "auto",
                guides = l.guides.map { g -> TraceMap.fromView(e, a0, frame, listOf(g.a, g.b)).let { (a, b) -> g.copy(a = a, b = b) } })
        })
    }

    /** Alto / ancho de la foto original, con su orientación EXIF (sin decodificar los píxeles). */
    fun originalAspect(context: Context, uri: Uri): Float {
        fun open() = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)!!
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
        val rot = runCatching { open().use { androidx.exifinterface.media.ExifInterface(it).rotationDegrees } }.getOrDefault(0)
        val w = o.outWidth.coerceAtLeast(1); val h = o.outHeight.coerceAtLeast(1)
        return if (rot % 180 == 90) w / h.toFloat() else h / w.toFloat()
    }

    /** Alto / ancho de la foto con su edición. */
    fun editedAspect(context: Context, uri: Uri, e: ImageEdit): Float = TraceMap.editedAspect(e, originalAspect(context, uri))

    /** Conjunto actual o, si no hay, el que forman las líneas mostradas ([lines], en [frame]). */
    fun ensure(context: Context, uri: Uri, lines: List<AnnotLine>, frame: TraceFrame): TraceSet {
        of(context, uri.toString())?.let { return it }
        val e = PhotoEdits.of(context, uri.toString())
        return fromLines(lines, e, editedAspect(context, uri, e), frame)
    }

    // --- operaciones del usuario sobre el conjunto (devuelven el nuevo) ---

    fun remove(s: TraceSet, name: String): TraceSet {
        val t = s.byName(name) ?: return s
        return s.copy(traces = s.traces - t, removed = s.removed + t)
    }

    fun restore(s: TraceSet) = s.copy(traces = s.traces + s.removed, removed = emptyList())

    /** Nombre libre para un trazo añadido («Surco añadido n»). */
    fun nextAddedName(s: TraceSet): String {
        val used = (s.traces + s.removed).mapNotNull { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { "Surco añadido $it" }.first { it !in used }
    }

    /** Añade un trazo (puntos en coordenadas de la original). */
    fun add(s: TraceSet, t: StoredTrace) = s.copy(traces = s.traces + t)

    /** Vuelve a trazar la línea [name]: conserva la versión anterior para revertir. */
    fun replace(s: TraceSet, name: String, points: List<Pt>, metrics: LineMetrics?, guides: List<MeasureGuide> = emptyList()): TraceSet {
        val old = s.byName(name) ?: return s
        val first = old.previous ?: old
        val m = (metrics ?: old.metrics)?.copy(name = name, note = "Trazado de nuevo a mano")
        val n = old.copy(points = points, metrics = m, origin = "editado", previous = first.copy(previous = null), closed = false, filled = false,
            guides = guides)
        return s.copy(traces = s.traces.map { if (it === old) n else it })
    }

    /** Revierte la edición de [name] (vuelve a la versión anterior). */
    fun revert(s: TraceSet, name: String): TraceSet {
        val t = s.byName(name) ?: return s
        val p = t.previous ?: return s
        return s.copy(traces = s.traces.map { if (it === t) p else it })
    }
}

/**
 * Trazado a mano alzada de un surco: el usuario dibuja sobre la foto una **línea** por donde lo ve o
 * rodea un **área**. Con una línea se busca el valle de sombra a lo largo de ella (pasillo estrecho,
 * umbral concesivo); si no se encuentra, se usa el propio trazo suavizado. Con un área se busca dentro
 * de ella ([RegionTrace]) y se conserva lo que queda dentro.
 */
object FreehandTrace {
    class Result(val points: List<Pt>, /** true si se ajustó a la sombra de la piel; false = el trazo del usuario. */ val snapped: Boolean)

    fun length(p: List<Pt>) = p.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat()

    /** ¿El trazo es un área cerrada (vuelve cerca de donde empezó)? */
    fun isArea(p: List<Pt>): Boolean {
        if (p.size < 8) return false
        val l = length(p)
        if (l <= 0f) return false
        val gap = hypot(p.last().x - p.first().x, p.last().y - p.first().y)
        return gap < 0.2f * l && abs(area(p)) > (0.06f * l) * (0.06f * l)
    }

    private fun area(p: List<Pt>): Float {
        var s = 0f; var j = p.size - 1
        for (i in p.indices) { s += (p[j].x + p[i].x) * (p[j].y - p[i].y); j = i }
        return s / 2
    }

    /** Remuestreo uniforme en [n] puntos. */
    fun resample(p: List<Pt>, n: Int): List<Pt> {
        val l = length(p); if (l <= 0f || p.size < 2) return p
        val out = ArrayList<Pt>(); var acc = 0f; var i = 0
        for (k in 0 until n) {
            val target = l * k / (n - 1)
            while (i < p.size - 2 && acc + hypot(p[i + 1].x - p[i].x, p[i + 1].y - p[i].y) < target) { acc += hypot(p[i + 1].x - p[i].x, p[i + 1].y - p[i].y); i++ }
            val seg = hypot(p[i + 1].x - p[i].x, p[i + 1].y - p[i].y).coerceAtLeast(1e-9f)
            val t = ((target - acc) / seg).coerceIn(0f, 1f)
            out += Pt(p[i].x + (p[i + 1].x - p[i].x) * t, p[i].y + (p[i + 1].y - p[i].y) * t)
        }
        return out
    }

    /** Suavizado (media móvil de 5) conservando los extremos. */
    private fun smooth(p: List<Pt>): List<Pt> = p.indices.map { i ->
        if (i == 0 || i == p.lastIndex) p[i] else {
            val r = (maxOf(0, i - 2)..minOf(p.lastIndex, i + 2)).map { p[it] }
            Pt(r.map { it.x }.average().toFloat(), r.map { it.y }.average().toFloat())
        }
    }

    private fun inside(q: Pt, poly: List<Pt>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            if ((poly[i].y > q.y) != (poly[j].y > q.y) && q.x < (poly[j].x - poly[i].x) * (q.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x) c = !c
            j = i
        }
        return c
    }

    /** [path]: trazo del usuario en unidades del ancho de [bmp]. null si no hay nada que trazar. */
    fun trace(bmp: Bitmap, path: List<Pt>): Result? {
        val w = bmp.width.toFloat()
        if (path.size < 2 || length(path) * w < 8f) return null
        if (isArea(path)) {
            val xs = path.map { it.x }; val ys = path.map { it.y }
            val found = RegionTrace.trace(bmp, floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())) ?: return null
            val mg = 0.03f * maxOf(xs.max() - xs.min(), ys.max() - ys.min())
            val kept = found.filter { q -> inside(q, path) || path.any { hypot(it.x - q.x, it.y - q.y) < mg } }
            return if (kept.size >= 3) Result(kept, true) else null
        }
        val line = smooth(resample(path, 40))
        // recorte alrededor del trazo, a ~400 px de lado
        val l = length(line)
        val m = maxOf(0.3f * l, 24f / w)
        val x0 = ((line.minOf { it.x } - m) * w).coerceAtLeast(0f); val y0 = ((line.minOf { it.y } - m) * w).coerceAtLeast(0f)
        val x1 = ((line.maxOf { it.x } + m) * w).coerceAtMost(w); val y1 = ((line.maxOf { it.y } + m) * w).coerceAtMost(bmp.height.toFloat())
        val cw = (x1 - x0).toInt().coerceAtLeast(2); val ch = (y1 - y0).toInt().coerceAtLeast(2)
        val sc = (400f / maxOf(cw, ch)).coerceIn(0.25f, 4f)
        val crop = Bitmap.createBitmap(bmp, x0.toInt(), y0.toInt(), cw.coerceAtMost(bmp.width - x0.toInt()), ch.coerceAtMost(bmp.height - y0.toInt()),
            android.graphics.Matrix().apply { postScale(sc, sc) }, true)
        val lum = FaceCrop.lum(crop)
        val cwF = crop.width.toFloat()
        fun toC(p: Pt) = Pt((p.x * w - x0.toInt()) * sc / cwF, (p.y * w - y0.toInt()) * sc / cwF)
        fun fromC(p: Pt) = Pt((p.x * cwF / sc + x0.toInt()) / w, (p.y * cwF / sc + y0.toInt()) / w)
        val axis = resample(line, 9).map(::toC)
        val lenC = length(axis)
        // pasillo alrededor del trazo (±15 % de su longitud): el surco está donde el usuario lo dibujó
        // o cerca; se busca también en los alrededores
        val half = (0.15f * lenC).coerceIn(8f / cwF, 40f / cwF)
        val r = RidgeTracker.track(lum, axis, half, stations = 30, offsets = 16,
            minStrength = RegionTrace.MIN_STRENGTH, minContrast = RegionTrace.MIN_CONTRAST)
        crop.recycle()
        val snapped = r?.points?.map(::fromC)?.let { RegionTrace.trimHooks(it) }?.takeIf { it.size >= 3 && length(it) >= 0.5f * l }
        return if (snapped != null) Result(snapped, true) else Result(line, false)
    }
}
