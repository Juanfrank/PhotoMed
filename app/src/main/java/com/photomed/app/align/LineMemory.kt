package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Sistema de coordenadas de la cara (independiente del encuadre, del recorte y del tamaño de la foto):
 * origen en el punto medio entre las pupilas, eje x por las pupilas, unidad = distancia interpupilar.
 * Sirve para llevar las líneas de una foto a otra del mismo paciente.
 */
object FaceFrame {
    private class F(val o: Pt, val ex: Pt, val ey: Pt, val s: Float)

    private fun frame(mesh: List<Pt>): F? {
        if (mesh.size < 468) return null
        val r = if (mesh.size > 473) mesh[468] else Pt((mesh[33].x + mesh[133].x) / 2, (mesh[33].y + mesh[133].y) / 2)
        val l = if (mesh.size > 473) mesh[473] else Pt((mesh[263].x + mesh[362].x) / 2, (mesh[263].y + mesh[362].y) / 2)
        val s = hypot(l.x - r.x, l.y - r.y).takeIf { it > 1e-5f } ?: return null
        val ex = Pt((l.x - r.x) / s, (l.y - r.y) / s)
        return F(Pt((r.x + l.x) / 2, (r.y + l.y) / 2), ex, Pt(-ex.y, ex.x), s)
    }

    fun toFace(mesh: List<Pt>, pts: List<Pt>): List<Pt>? = frame(mesh)?.let { f ->
        pts.map { p -> val dx = p.x - f.o.x; val dy = p.y - f.o.y; Pt((dx * f.ex.x + dy * f.ex.y) / f.s, (dx * f.ey.x + dy * f.ey.y) / f.s) }
    }

    fun fromFace(mesh: List<Pt>, pts: List<Pt>): List<Pt>? = frame(mesh)?.let { f ->
        pts.map { q -> Pt(f.o.x + (q.x * f.ex.x + q.y * f.ey.x) * f.s, f.o.y + (q.x * f.ex.y + q.y * f.ey.y) * f.s) }
    }
}

/** Línea de una foto anterior, en coordenadas de la cara ([FaceFrame]). */
data class PriorLine(val name: String, val layer: AnnotLayer, val points: List<Pt>)

/**
 * Memoria de las arrugas y surcos de cada foto (en coordenadas de la cara), para que el análisis de
 * una foto posterior del mismo paciente los busque donde estaban.
 */
object LineMemory {
    /** Capas que se recuerdan (arrugas y surcos). */
    val LAYERS = setOf(AnnotLayer.FOLDS, AnnotLayer.FOREHEAD, AnnotLayer.GLABELLA, AnnotLayer.CROWS_FEET)

    private fun dir(context: Context) = File(context.filesDir, "lineas").apply { mkdirs() }
    private fun key(uri: String) = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(context: Context, uri: String) = File(dir(context), key(uri) + ".json")

    fun save(context: Context, uri: String, mesh: List<Pt>, lines: List<AnnotLine>) {
        val a = JSONArray()
        for (l in lines) {
            if (l.layer !in LAYERS || l.estimated) continue
            val name = l.metrics?.name ?: continue
            // puntos no finitos: JSONArray no los admite y la escritura fallaba en silencio
            val f = FaceFrame.toFace(mesh, l.points)?.filter { it.x.isFinite() && it.y.isFinite() }?.takeIf { it.size >= 2 } ?: continue
            a.put(JSONObject().put("name", name).put("layer", l.layer.name)
                .put("pts", JSONArray(f.map { JSONArray(listOf(it.x.toDouble(), it.y.toDouble())) })))
        }
        runCatching { AtomicFile.write(file(context, uri), a.toString()) }
    }

    fun of(context: Context, uri: String): List<PriorLine> = runCatching {
        val f = file(context, uri); if (!f.exists()) return emptyList()
        val a = JSONArray(f.readText())
        (0 until a.length()).map { i -> val o = a.getJSONObject(i); val p = o.getJSONArray("pts")
            PriorLine(o.getString("name"), AnnotLayer.valueOf(o.getString("layer")),
                (0 until p.length()).map { j -> p.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }) }
    }.getOrDefault(emptyList())

    /** Líneas de las fotos [previous] (la más reciente primero manda por nombre). */
    fun priors(context: Context, previous: List<String>): List<PriorLine> {
        val byName = LinkedHashMap<String, PriorLine>()
        for (u in previous) for (p in of(context, u)) byName.putIfAbsent(p.name, p)
        return byName.values.toList()
    }

    /** Etiqueta para la clave de caché (cambia si cambian las referencias disponibles). */
    fun tag(context: Context, previous: List<String>): String =
        previous.joinToString(",") { u -> val f = file(context, u); if (f.exists()) "${f.length()}" else "-" }.hashCode().toString()

    fun delete(context: Context, uri: String) { file(context, uri).delete() }
}

/** Correcciones del usuario sobre las líneas de una foto: eliminadas (falsos positivos) y añadidas a mano. */
data class AnnotUserEdit(val dismissed: Set<String> = emptySet(), val added: List<List<Pt>> = emptyList(), val editHash: Int = 0,
                         /** Alto / ancho de la foto completa editada donde se trazaron los añadidos. */
                         val aspect: Float = 4f / 3f) {
    /**
     * Los trazos añadidos llevados al encuadre 3:4 centrado (el de Comparar), en unidades de su ancho.
     */
    fun addedIn34(): List<List<Pt>> {
        val a = aspect
        return added.map { l -> l.map { p ->
            if (a >= 4f / 3f) Pt(p.x, p.y - (a - 4f / 3f) / 2)                 // se recorta arriba y abajo
            else { val cw = a * 0.75f; Pt((p.x - (1 - cw) / 2) / cw, p.y / cw) } // se recorta a los lados
        } }
    }
    val isEmpty get() = dismissed.isEmpty() && added.isEmpty()
}

/**
 * Almacén de [AnnotUserEdit] por foto. Los trazos añadidos van en coordenadas de la foto completa con su
 * edición (giro, espejo, recorte) en el momento de añadirlos; si la edición cambia, se descartan.
 */
object AnnotUserEdits {
    private const val PREFS = "annot_user"

    fun of(context: Context, uri: String): AnnotUserEdit = runCatching {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(uri, null) ?: return AnnotUserEdit()
        val o = JSONObject(s)
        val d = o.optJSONArray("dismissed")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val ad = o.optJSONArray("added")?.let { a -> (0 until a.length()).map { i -> val p = a.getJSONArray(i)
            (0 until p.length()).map { j -> p.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } } } } ?: emptyList()
        AnnotUserEdit(d, ad, o.optInt("edit"), o.optDouble("aspect", 4.0 / 3).toFloat())
    }.getOrDefault(AnnotUserEdit())

    private fun save(context: Context, uri: String, e: AnnotUserEdit) {
        val o = JSONObject().put("dismissed", JSONArray(e.dismissed.sorted()))
            .put("added", JSONArray(e.added.map { l -> JSONArray(l.map { JSONArray(listOf(it.x.toDouble(), it.y.toDouble())) }) }))
            .put("edit", e.editHash).put("aspect", e.aspect.toDouble())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(uri, o.toString()).apply()
    }

    /** Elimina la línea [name] (falso positivo). Idempotente. */
    fun dismiss(context: Context, uri: String, name: String) { val e = of(context, uri); save(context, uri, e.copy(dismissed = e.dismissed + name)) }

    /** Restaura las líneas eliminadas. */
    fun restore(context: Context, uri: String) { val e = of(context, uri); save(context, uri, e.copy(dismissed = emptySet())) }

    /** Añade un trazo hecho a mano (coordenadas de la foto completa con la edición [editHash]). */
    fun add(context: Context, uri: String, pts: List<Pt>, editHash: Int, aspect: Float) {
        val e = of(context, uri).let { if (it.editHash != editHash) it.copy(added = emptyList()) else it }
        save(context, uri, e.copy(added = e.added + listOf(pts), editHash = editHash, aspect = aspect))
    }

    /** Quita el último trazo añadido. */
    fun removeAdded(context: Context, uri: String, index: Int) {
        val e = of(context, uri); if (index !in e.added.indices) return
        save(context, uri, e.copy(added = e.added.filterIndexed { i, _ -> i != index }))
    }

    /** Etiqueta para la clave de caché. */
    fun tag(context: Context, uri: String): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(uri, "")!!.hashCode().toString()

    fun delete(context: Context, uri: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(uri).apply()
}

/**
 * Trazado de un surco en una zona elegida por el usuario (reintento con umbral más concesivo): se busca
 * la cresta de valle más fuerte dentro del rectángulo en 12 orientaciones (cada 15°), con
 * [RidgeTracker] y umbrales bajos.
 */
object RegionTrace {
    /** Quita los extremos que se doblan bruscamente (> 35°) respecto al tramo siguiente. */
    fun trimHooks(p: List<Pt>): List<Pt> {
        var a = 0; var b = p.size - 1
        fun bend(i: Int, j: Int, k: Int): Double {
            val ux = p[j].x - p[i].x; val uy = p[j].y - p[i].y; val vx = p[k].x - p[j].x; val vy = p[k].y - p[j].y
            val n = hypot(ux, uy) * hypot(vx, vy); if (n < 1e-12f) return 0.0
            return Math.toDegrees(kotlin.math.acos(((ux * vx + uy * vy) / n).coerceIn(-1f, 1f).toDouble()))
        }
        while (b - a >= 4 && bend(a, a + 1, a + 2) > 35.0) a++
        while (b - a >= 4 && bend(b - 2, b - 1, b) > 35.0) b--
        return p.subList(a, b + 1)
    }

    // la zona ya la eligió el usuario: basta un valle claramente más marcado que su entorno, aunque tenue
    const val MIN_STRENGTH = 0.003f
    const val MIN_CONTRAST = 1.6f

    /** [rect]: x0, y0, x1, y1 en unidades del ancho de [bmp]. Devuelve los puntos (mismas unidades) o null. */
    fun trace(bmp: Bitmap, rect: FloatArray): List<Pt>? {
        val w = bmp.width.toFloat()
        val x0 = minOf(rect[0], rect[2]) * w; val x1 = maxOf(rect[0], rect[2]) * w
        val y0 = minOf(rect[1], rect[3]) * w; val y1 = maxOf(rect[1], rect[3]) * w
        val side = maxOf(x1 - x0, y1 - y0)
        if (side < 6f) return null
        // recorte con margen, a ~320 px de lado para los filtros
        val m = side * 0.25f
        val cx0 = (x0 - m).coerceAtLeast(0f); val cy0 = (y0 - m).coerceAtLeast(0f)
        val cx1 = (x1 + m).coerceAtMost(w); val cy1 = (y1 + m).coerceAtMost(bmp.height.toFloat())
        val cw = (cx1 - cx0).toInt().coerceAtLeast(2); val ch = (cy1 - cy0).toInt().coerceAtLeast(2)
        val sc = (320f / maxOf(cw, ch)).coerceIn(0.25f, 4f)
        val crop = Bitmap.createBitmap(bmp, cx0.toInt(), cy0.toInt(), cw, ch, android.graphics.Matrix().apply { postScale(sc, sc) }, true)
        val lum = FaceCrop.lum(crop)
        val cwF = crop.width.toFloat()
        fun toC(x: Float, y: Float) = Pt((x - cx0) * sc / cwF, (y - cy0) * sc / cwF)
        val c = toC((x0 + x1) / 2, (y0 + y1) / 2)
        val halfW = (x1 - x0) / 2 * sc / cwF; val halfH = (y1 - y0) / 2 * sc / cwF
        var best: RidgeTracker.Result? = null; var bestScore = 0f
        // 12 direcciones, una por núcleo; se elige como en serie (la primera con la mejor puntuación)
        Par.map((0 until 12).toList()) { k -> run dirOne@{
            val a = Math.toRadians(k * 15.0)
            val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
            // semilongitud dentro del rectángulo en esa dirección y semiancho del pasillo perpendicular
            val len = minOf(if (kotlin.math.abs(dx) > 1e-3f) halfW / kotlin.math.abs(dx) else Float.MAX_VALUE,
                if (kotlin.math.abs(dy) > 1e-3f) halfH / kotlin.math.abs(dy) else Float.MAX_VALUE)
            val across = minOf(if (kotlin.math.abs(dy) > 1e-3f) halfW / kotlin.math.abs(dy) else Float.MAX_VALUE,
                if (kotlin.math.abs(dx) > 1e-3f) halfH / kotlin.math.abs(dx) else Float.MAX_VALUE).coerceAtMost(len)
            if (len < 3f / cwF) return@dirOne null
            val axis = listOf(Pt(c.x - dx * len, c.y - dy * len), c, Pt(c.x + dx * len, c.y + dy * len))
            // resolución lateral fina (~2,5 px por posición): con un pasillo tan ancho como la zona, pocas
            // posiciones permitían saltos laterales grandes de una estación a otra
            val acrossPx = across * cwF
            val r = RidgeTracker.track(lum, axis, across.coerceAtLeast(2f / cwF), stations = 30,
                offsets = (acrossPx / 2.5f).toInt().coerceIn(8, 40),
                minStrength = MIN_STRENGTH, minContrast = MIN_CONTRAST) ?: return@dirOne null
            r
        } }.forEach { r ->
            if (r == null) return@forEach
            val score = r.strength * LineMeasure.length(r.points)
            if (score > bestScore) { bestScore = score; best = r }
        }
        crop.recycle()
        val r = best ?: return null
        // dentro de la zona elegida (margen del 5 % de su lado): lo de fuera no es lo que se pidió
        val mg = side * 0.05f
        val pts = r.points.map { p -> Pt((p.x * cwF / sc + cx0) / w, (p.y * cwF / sc + cy0) / w) }
            .filter { p -> p.x * w in (x0 - mg)..(x1 + mg) && p.y * w in (y0 - mg)..(y1 + mg) }
        return trimHooks(pts).takeIf { it.size >= 3 }
    }
}
