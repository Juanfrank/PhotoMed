package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Una toma de la secuencia de estéreo fotométrico (fotograma vertical sin espejar). */
class PsFrame(val pattern: PhotometricStereo.Pattern, val bmp: Bitmap)

/** Relieve fino de la cara (mm, hacia la cámara; NaN fuera) en un recuadro cuadrado del fotograma. */
class ReliefResult(
    val size: Int, val relief: FloatArray,
    /** Recuadro en px del fotograma: x0, y0, lado. */
    val boxX: Float, val boxY: Float, val boxSide: Float,
    val frameW: Int, val frameH: Int,
    /** mm por px del fotograma en la cara. */
    val mmPerPx: Float,
    val distanceMm: Float, val distanceSource: String,
    /** Luz de la pantalla / luz ambiente en la cara (≥ 0,5 recomendable). */
    val signalRatio: Float,
    val validFraction: Float,
)

/**
 * Cálculo del relieve con la secuencia de la pantalla (ver [PhotometricStereo]) y su guardado
 * junto a la foto, en las coordenadas de la foto, para medir la profundidad real de las líneas.
 */
object Relief {
    const val SIZE = 512
    /** Rango guardado del relieve (± mm) en la imagen de 8 bits (≈ 12 µm por nivel). */
    const val RANGE_MM = 1.5f
    /** Pasa-altos del relieve: estructuras de hasta unos mm (arrugas y surcos). */
    const val HIGHPASS_MM = 4f

    /**
     * [mesh]: malla de MediaPipe del fotograma FULL (x, y / ancho); [focalPx]: focal en px del
     * fotograma (o null); [sensorDistMm]: distancia medida por el sensor de profundidad (o null).
     */
    fun compute(frames: List<PsFrame>, mesh: List<Pt>, screen: PhotometricStereo.Screen, focalPx: Float?, sensorDistMm: Float?): ReliefResult? {

        val full = frames.firstOrNull { it.pattern == PhotometricStereo.Pattern.FULL } ?: return null
        val fw = full.bmp.width; val fh = full.bmp.height
        if (mesh.size < 478) return null
        val faceW = hypot(mesh[234].x - mesh[454].x, mesh[234].y - mesh[454].y) * fw
        val cx = mesh.map { it.x }.average().toFloat() * fw; val cy = mesh.map { it.y }.average().toFloat() * fw
        val side = (faceW * 1.3f).coerceAtMost(minOf(fw, fh).toFloat())
        val bx = (cx - side / 2).coerceIn(0f, fw - side); val by = (cy - side / 2).coerceIn(0f, fh - side)
        val n = SIZE
        fun crop(b: Bitmap): FloatArray {
            val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
            val lut = FloatArray(256) { PhotometricStereo.linear(it / 255f) }
            fun lum(x: Int, y: Int): Float { val c = px[y.coerceIn(0, b.height - 1) * b.width + x.coerceIn(0, b.width - 1)]
                return 0.2126f * lut[(c shr 16) and 0xFF] + 0.7152f * lut[(c shr 8) and 0xFF] + 0.0722f * lut[c and 0xFF] }
            return FloatArray(n * n) { k ->
                val fx = bx + (k % n + 0.5f) * side / n - 0.5f; val fy = by + (k / n + 0.5f) * side / n - 0.5f
                val x0 = fx.toInt(); val y0 = fy.toInt(); val ax = fx - x0; val ay = fy - y0
                (lum(x0, y0) * (1 - ax) + lum(x0 + 1, y0) * ax) * (1 - ay) + (lum(x0, y0 + 1) * (1 - ax) + lum(x0 + 1, y0 + 1) * ax) * ay
            }
        }
        val ref = crop(full.bmp)
        // cada toma alineada con la de referencia (movimientos pequeños durante la secuencia)
        val aligned = frames.filter { it.pattern != PhotometricStereo.Pattern.FULL }.map { f ->
            val c = crop(f.bmp)
            val (dx, dy) = PhotometricStereo.align(ref, c, n, n, maxShift = 24)
            f.pattern to PhotometricStereo.shift(c, n, n, dx, dy)
        }
        val darks = aligned.filter { it.first == PhotometricStereo.Pattern.DARK }.map { it.second }
        val dark = if (darks.isEmpty()) FloatArray(n * n) else FloatArray(n * n) { i -> darks.sumOf { it[i].toDouble() }.toFloat() / darks.size }
        val lit = PhotometricStereo.LIT.mapNotNull { p ->
            val v = aligned.filter { it.first == p }.map { it.second }
            if (v.isEmpty()) null else p to FloatArray(n * n) { i -> (v.sumOf { it[i].toDouble() }.toFloat() / v.size - dark[i]).coerceAtLeast(0f) }
        }
        if (lit.size < 3) return null
        // máscara: óvalo de la cara
        val oval = FaceAnnotations.keysFromMesh(mesh).let { k -> (0 until 36).mapNotNull { k["oval_$it"] } }
            .map { Pt((it.x * fw - bx) * n / side, (it.y * fw - by) * n / side) }
        val mask = BooleanArray(n * n) { k -> inside(Pt(k % n + 0.5f, k / n + 0.5f), oval) }
        // escala y distancia
        val irisPx = maxOf(hypot(mesh[469].x - mesh[471].x, mesh[469].y - mesh[471].y), hypot(mesh[474].x - mesh[476].x, mesh[474].y - mesh[476].y)) * fw
        val mmPerPxIris = LineMeasure.HVID_MM / irisPx.coerceAtLeast(1f)
        val (dist, src) = when {
            sensorDistMm != null && sensorDistMm in 100f..1500f -> sensorDistMm to "sensor de profundidad"
            focalPx != null -> focalPx * mmPerPxIris to "iris"
            else -> 300f to "estimada (30 cm)"
        }
        val mmPerPx = if (src == "sensor de profundidad" && focalPx != null) dist / focalPx else mmPerPxIris
        val lights = lit.map { PhotometricStereo.light(it.first, screen, dist) }
        val nm = PhotometricStereo.solve(lit.map { it.second }, lights, n, n, mask)
        val fb = oval.let { o -> floatArrayOf(o.minOf { it.x }, o.minOf { it.y }, o.maxOf { it.x }, o.maxOf { it.y }) }
        PhotometricStereo.fixSigns(nm, fb)
        val t = PhotometricStereo.heights(nm)
        val mmPerCrop = mmPerPx * side / n
        val rel = PhotometricStereo.relief(t, n, n, HIGHPASS_MM / mmPerCrop, nm.valid)
        for (i in rel.indices) if (!rel[i].isNaN()) rel[i] = (rel[i] * mmPerCrop).coerceIn(-RANGE_MM, RANGE_MM)
        var sig = 0.0; var amb = 0.0
        for (i in 0 until n * n) if (mask[i]) { sig += lit.sumOf { it.second[i].toDouble() } / lit.size; amb += dark[i] }
        val valid = nm.valid.count { it } / mask.count { it }.coerceAtLeast(1).toFloat()
        return ReliefResult(n, rel, bx, by, side, fw, fh, mmPerPx, dist, src, (sig / amb.coerceAtLeast(1e-6)).toFloat(), valid)
    }

    /**
     * Lleva el relieve a las coordenadas de la foto guardada (puede estar espejada respecto al
     * fotograma): semejanza entre las mallas de ambas (con y sin espejo; la de menor error).
     * Devuelve la imagen de 8 bits ([outW] de ancho) y el factor de escala frente al iris.
     */
    fun toPhoto(r: ReliefResult, frameMesh: List<Pt>, photoMesh: List<Pt>, photoW: Int, photoH: Int, outW: Int = 720): Pair<Bitmap, Float?>? {
        val idx = (0 until 468)
        val dst = idx.map { photoMesh[it] }
        val plain = idx.map { frameMesh[it] }; val mir = plain.map { Pt(1f - it.x, it.y) }
        val tp = Similarity.estimate(plain, dst) ?: return null; val tm = Similarity.estimate(mir, dst) ?: return null
        val mirrored = Similarity.rms(tm, mir, dst) < Similarity.rms(tp, plain, dst)
        val t = if (mirrored) tm else tp
        if (Similarity.rms(t, if (mirrored) mir else plain, dst) > 0.02f) return null
        val inv = t.inverse()
        val outH = (outW * photoH / photoW.toFloat()).roundToInt()
        val n = r.size
        val px = IntArray(outW * outH)
        for (y in 0 until outH) for (x in 0 until outW) {
            val p = inv.apply(Pt((x + 0.5f) / outW, (y + 0.5f) / outW))
            val fx = (if (mirrored) 1f - p.x else p.x) * r.frameW; val fy = p.y * r.frameW
            val u = ((fx - r.boxX) * n / r.boxSide).toInt(); val v = ((fy - r.boxY) * n / r.boxSide).toInt()
            val mm = if (u in 0 until n && v in 0 until n) r.relief[v * n + u] else Float.NaN
            val g = if (mm.isNaN()) 0 else (128 + mm / RANGE_MM * 126).roundToInt().coerceIn(2, 254)
            px[y * outW + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val bmp = Bitmap.createBitmap(px, outW, outH, Bitmap.Config.ARGB_8888)
        // mm por unidad de ancho de la foto medidos / según el iris de la foto
        val measured = r.mmPerPx * r.frameW / t.scale
        val iris = LineMeasure.scale(photoMesh)?.takeIf { it.second == "iris" }?.first
        return bmp to iris?.let { measured / it }
    }

    /** Relieve en mm (NaN fuera) desde la imagen de 8 bits ya cargada con la edición de la foto. */
    fun decode(bmp: Bitmap): GrayImage {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        return GrayImage(w, h, FloatArray(w * h) { i -> val g = (px[i] shr 8) and 0xFF; if (g < 12) Float.NaN else (g - 128) / 126f * RANGE_MM })
    }

    private fun inside(q: Pt, poly: List<Pt>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            if ((poly[i].y > q.y) != (poly[j].y > q.y) && q.x < (poly[j].x - poly[i].x) * (q.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x) c = !c
            j = i
        }
        return c
    }
}

/** Datos de medida guardados junto a una foto: relieve (imagen) y escala del sensor de profundidad. */
data class MeasureMeta(
    val hasRelief: Boolean,
    /** Factor sobre la escala del iris (mm medidos con el sensor de profundidad / mm según el iris). */
    val irisFactor: Float? = null,
    val distanceMm: Float? = null,
    val distanceSource: String? = null,
    val signalRatio: Float? = null,
    val validFraction: Float? = null,
)

/** Medidas extra de una foto para [LineMeasure]: relieve en sus coordenadas y factor de escala. */
class MeasureExtras(val relief: GrayImage?, val meta: MeasureMeta?)

object ReliefStore {
    private fun dir(context: Context) = File(context.filesDir, "relieve").apply { mkdirs() }
    private fun key(uri: String) = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
    fun reliefFile(context: Context, uri: String) = File(dir(context), key(uri) + ".png")
    private fun metaFile(context: Context, uri: String) = File(dir(context), key(uri) + ".json")

    fun save(context: Context, uri: String, relief: Bitmap?, meta: MeasureMeta) {
        relief?.let { b -> reliefFile(context, uri).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        metaFile(context, uri).writeText(JSONObject().apply {
            put("hasRelief", meta.hasRelief && relief != null)
            meta.irisFactor?.let { put("irisFactor", it.toDouble()) }
            meta.distanceMm?.let { put("distanceMm", it.toDouble()) }
            meta.distanceSource?.let { put("distanceSource", it) }
            meta.signalRatio?.let { put("signalRatio", it.toDouble()) }
            meta.validFraction?.let { put("validFraction", it.toDouble()) }
        }.toString())
    }

    fun meta(context: Context, uri: String): MeasureMeta? = runCatching {
        val f = metaFile(context, uri); if (!f.exists()) return null
        val o = JSONObject(f.readText())
        MeasureMeta(o.optBoolean("hasRelief") && reliefFile(context, uri).exists(),
            o.optDouble("irisFactor").takeIf { !it.isNaN() }?.toFloat(), o.optDouble("distanceMm").takeIf { !it.isNaN() }?.toFloat(),
            o.optString("distanceSource").ifBlank { null }, o.optDouble("signalRatio").takeIf { !it.isNaN() }?.toFloat(),
            o.optDouble("validFraction").takeIf { !it.isNaN() }?.toFloat())
    }.getOrNull()

    /** Borra el relieve y la escala de una foto (al eliminarla). */
    fun delete(context: Context, uri: String) {
        reliefFile(context, uri).delete(); metaFile(context, uri).delete()
        // y las líneas recordadas y las correcciones del usuario de esa foto
        LineMemory.delete(context, uri); AnnotUserEdits.delete(context, uri); ConfirmedTraces.forget(context, uri)
    }

    /** Uri del relieve (para cargarlo con la misma edición y recorte que la foto) o null. */
    fun reliefUri(context: Context, uri: String): Uri? = reliefFile(context, uri).takeIf { it.exists() }?.let { Uri.fromFile(it) }

    /** Etiqueta corta para la clave de caché de las anotaciones (cambia si hay relieve o escala). */
    fun tag(context: Context, uri: String): String = meta(context, uri)?.let { "${if (it.hasRelief) "r" else ""}${it.irisFactor ?: ""}" } ?: ""
}
