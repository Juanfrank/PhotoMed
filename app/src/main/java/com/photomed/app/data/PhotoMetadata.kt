package com.photomed.app.data

import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Datos clínicos incrustados en cada fotografía (EXIF + XMP). */
data class PhotoInfo(
    val patientName: String,
    val recordNumber: String,
    val expedienteId: String,
    val zoneId: String,
    val zoneLabel: String,
    val capturedAt: Long,
    val consent: Boolean,
    /** Ángulo de la cámara respecto de la horizontal al capturar (°; + = hacia arriba). */
    val cameraPitch: Float? = null,
    /** Giro del teléfono al capturar (°). */
    val roll: Float? = null,
    /** Focal en unidades de alto de imagen (para la corrección de perspectiva). */
    val focalNorm: Float? = null,
    /** Condiciones de captura (motor, pose de la cabeza, coincidencia, zoom, distancia…). */
    val conditions: Map<String, String> = emptyMap(),
)

/**
 * Escribe/lee metadatos:
 * - EXIF ImageDescription: texto legible ("Paciente · Exp. · Zona").
 * - EXIF UserComment: JSON con todos los campos (lectura robusta).
 * - EXIF Software / Make del flujo: "PhotoMed".
 * - XMP: dc:title, dc:description, dc:subject y espacio propio `pm:` (UTF-8 completo),
 *   que muestran Google Fotos, Lightroom, digiKam, exiftool, etc.
 */
object PhotoMetadata {
    const val NS = "https://photomed.app/ns/1.0/"
    const val SOFTWARE = "PhotoMed"

    fun write(file: File, info: PhotoInfo) {
        val exif = ExifInterface(file)
        val description = describe(info)
        // Los campos EXIF de texto son ASCII: descripción transliterada y JSON con escapes \\uXXXX.
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, toAscii(description))
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, escapeJson(toJson(info).toString()))
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, SOFTWARE)
        exif.setAttribute(ExifInterface.TAG_XMP, xmp(info, description))
        exif.saveAttributes()
    }

    fun read(input: InputStream): PhotoInfo? = try {
        val exif = ExifInterface(input)
        exif.getAttribute(ExifInterface.TAG_XMP)?.let(::parseXmp)
            ?: exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { fromJson(JSONObject(it.substring(it.indexOf('{')))) }
    } catch (e: Exception) {
        null
    }

    /**
     * Copia anonimizada de verdad: la foto se vuelve a codificar solo con sus píxeles (girados según su orientación),
     * sin ningún metadato: ni XMP ni comentario, pero tampoco la miniatura EXIF (que conserva la imagen original), la
     * marca y el modelo de la cámara, las fechas originales ni el GPS. Si no se puede decodificar, se quitan los
     * metadatos conocidos ([strip]).
     */
    fun anonymize(file: File) {
        val ok = runCatching {
            val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val bmp = android.graphics.BitmapFactory.decodeFile(file.path) ?: return@runCatching false
            val m = android.graphics.Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            val out = if (m.isIdentity) bmp else android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            if (out !== bmp) out.recycle(); bmp.recycle()
            tmp.renameTo(file) || run { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }.getOrDefault(false)
        if (!ok) strip(file)
    }

    /** Elimina los datos del paciente conocidos (respaldo de [anonymize]). */
    fun strip(file: File) {
        val exif = ExifInterface(file)
        listOf(
            ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_XMP,
            ExifInterface.TAG_ARTIST, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_SOFTWARE,
        ).forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
    }

    fun describe(info: PhotoInfo): String = buildList {
        add("Paciente: ${info.patientName}")
        if (info.recordNumber.isNotBlank()) add("Exp. ${info.recordNumber}")
        add("Zona: ${info.zoneLabel}")
    }.joinToString(" · ")

    private fun toJson(i: PhotoInfo) = JSONObject()
        .put("app", SOFTWARE)
        .put("patientName", i.patientName)
        .put("recordNumber", i.recordNumber)
        .put("expedienteId", i.expedienteId)
        .put("zone", i.zoneId)
        .put("zoneLabel", i.zoneLabel)
        .put("capturedAt", i.capturedAt)
        .put("consent", i.consent)
        .apply {
            i.cameraPitch?.let { put("cameraPitch", it.toDouble()) }
            i.roll?.let { put("roll", it.toDouble()) }
            i.focalNorm?.let { put("focalNorm", it.toDouble()) }
            if (i.conditions.isNotEmpty()) put("conditions", JSONObject(i.conditions))
        }

    private fun fromJson(j: JSONObject) = PhotoInfo(
        patientName = j.optString("patientName"),
        recordNumber = j.optString("recordNumber"),
        expedienteId = j.optString("expedienteId"),
        zoneId = j.optString("zone"),
        zoneLabel = j.optString("zoneLabel"),
        capturedAt = j.optLong("capturedAt"),
        consent = j.optBoolean("consent"),
        cameraPitch = j.optDouble("cameraPitch").takeUnless { it.isNaN() }?.toFloat(),
        roll = j.optDouble("roll").takeUnless { it.isNaN() }?.toFloat(),
        focalNorm = j.optDouble("focalNorm").takeUnless { it.isNaN() }?.toFloat(),
        conditions = j.optJSONObject("conditions")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty(),
    )

    /** Escapa para XML y codifica lo no-ASCII como referencias de carácter (&#NNN;): XMP 100 % ASCII. */
    private fun esc(s: String) = buildString {
        s.codePoints().forEach { cp ->
            when {
                cp == '&'.code -> append("&amp;")
                cp == '<'.code -> append("&lt;")
                cp == '>'.code -> append("&gt;")
                cp == '"'.code -> append("&quot;")
                cp > 0x7E -> append("&#").append(cp).append(';')
                else -> appendCodePoint(cp)
            }
        }
    }

    private fun unesc(s: String) = Regex("&#(x?)([0-9a-fA-F]+);").replace(s) {
        String(Character.toChars(it.groupValues[2].toInt(if (it.groupValues[1].isEmpty()) 10 else 16)))
    }.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun escapeJson(json: String) = buildString {
        json.forEach { ch -> if (ch.code > 0x7E) append("\\u%04x".format(ch.code)) else append(ch) }
    }

    fun toAscii(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("·", "-")
            .map { if (it.code in 0x20..0x7E) it else '?' }.joinToString("")

    private fun iso(millis: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    fun xmp(i: PhotoInfo, description: String = describe(i)): String = """<?xpacket begin="${'\uFEFF'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
    xmlns:pm="$NS"
    xmp:CreatorTool="$SOFTWARE"
    xmp:CreateDate="${iso(i.capturedAt)}"
    pm:PatientName="${esc(i.patientName)}"
    pm:RecordNumber="${esc(i.recordNumber)}"
    pm:ExpedienteId="${esc(i.expedienteId)}"
    pm:Zone="${esc(i.zoneId)}"
    pm:ZoneLabel="${esc(i.zoneLabel)}"
    pm:CapturedAt="${i.capturedAt}"
    pm:Consent="${i.consent}"${i.cameraPitch?.let { "\n    pm:CameraPitch=\"%.2f\"".format(java.util.Locale.US, it) } ?: ""}${i.roll?.let { "\n    pm:Roll=\"%.2f\"".format(java.util.Locale.US, it) } ?: ""}${i.focalNorm?.let { "\n    pm:FocalNorm=\"%.4f\"".format(java.util.Locale.US, it) } ?: ""}${i.conditions.entries.joinToString("") { (k, v) -> "\n    pm:Cond_${k.filter { c -> c.isLetterOrDigit() }}=\"${esc(v)}\"" }}>
   <dc:title><rdf:Alt><rdf:li xml:lang="x-default">${esc(i.patientName)} — ${esc(i.zoneLabel)}</rdf:li></rdf:Alt></dc:title>
   <dc:description><rdf:Alt><rdf:li xml:lang="x-default">${esc(description)}</rdf:li></rdf:Alt></dc:description>
   <dc:subject><rdf:Bag><rdf:li>PhotoMed</rdf:li><rdf:li>${esc(i.zoneLabel)}</rdf:li><rdf:li>${esc(i.expedienteId)}</rdf:li></rdf:Bag></dc:subject>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""

    /**
     * XMP con los trazos confirmados ([json], ver ConfirmedTraces) en `pm:Traces`; null los quita. Si
     * no hay XMP, se crea uno mínimo con el espacio `pm:`.
     */
    fun withTraces(xmp: String?, json: String?): String = withAttr(xmp, "Traces", json)

    /** Valor JSON de un atributo `pm:<name>` del XMP, o null. */
    fun attrOf(xmp: String?, name: String): String? = xmp?.let { Regex("""pm:$name="([^"]*)"""").find(it)?.groupValues?.get(1)?.let(::unesc) }

    /** XMP con el atributo `pm:<name>` = [json] (null lo quita). */
    fun withAttr(xmp: String?, name: String, json: String?): String {
        val base = xmp?.takeIf { it.contains("rdf:Description") } ?: """<?xpacket begin="${'\uFEFF'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:pm="$NS"/>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        var out = base.replace(Regex("""\s*pm:$name="[^"]*""""), "")
        if (json != null) {
            val attr = "\n    pm:$name=\"${esc(json)}\""
            out = if (out.contains("xmlns:pm=")) Regex("""xmlns:pm="[^"]*"""").replace(out) { it.value + attr }
            else out.replaceFirst("rdf:about=\"\"", "rdf:about=\"\"\n    xmlns:pm=\"$NS\"$attr")
        }
        return out
    }

    /** Trazos confirmados (JSON) del XMP, o null. */
    fun tracesOf(xmp: String?): String? = xmp?.let { Regex("""pm:Traces="([^"]*)"""").find(it)?.groupValues?.get(1)?.let(::unesc) }

    fun parseXmp(xmp: String): PhotoInfo? {
        fun attr(name: String): String? = Regex("""pm:$name="([^"]*)"""").find(xmp)?.groupValues?.get(1)?.let(::unesc)
        val zone = attr("Zone") ?: return null
        return PhotoInfo(
            patientName = attr("PatientName").orEmpty(),
            recordNumber = attr("RecordNumber").orEmpty(),
            expedienteId = attr("ExpedienteId").orEmpty(),
            zoneId = zone,
            zoneLabel = attr("ZoneLabel").orEmpty(),
            capturedAt = attr("CapturedAt")?.toLongOrNull() ?: 0L,
            consent = attr("Consent") == "true",
            cameraPitch = attr("CameraPitch")?.toFloatOrNull(),
            roll = attr("Roll")?.toFloatOrNull(),
            focalNorm = attr("FocalNorm")?.toFloatOrNull(),
            conditions = Regex("""pm:Cond_(\w+)="([^"]*)"""").findAll(xmp).associate { it.groupValues[1] to unesc(it.groupValues[2]) },
        )
    }
}
