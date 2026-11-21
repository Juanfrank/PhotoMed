package com.photomed.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.ImageOps
import com.photomed.app.align.MediaPipeFace
import com.photomed.app.align.Standardization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/** Foto elegida para importar: fecha (EXIF o la de importación) y ángulo (zona) asignado. */
data class ImportItem(val uri: Uri, val takenAt: Long, val dateFromPhoto: Boolean, val zone: Zone?, val suggested: Boolean = false,
                      /** Expresión facial (arrugas dinámicas); reposo por defecto. */
                      val expression: FacialExpression = FacialExpression.NEUTRAL)

/**
 * Importación de fotos (de la galería, de otra cámara, de otro programa) a un expediente: cada foto
 * se asigna a un ángulo (zona) y se guarda como las tomadas con la app (álbum del paciente, datos
 * del paciente en EXIF/XMP). Se vuelve a codificar en JPEG con la orientación aplicada, sin los
 * metadatos originales (ubicación, dispositivo…), y conserva la fecha de toma si la tiene.
 */
object PhotoImport {
    /** Fecha de toma EXIF (DateTimeOriginal, si no DateTime) en milisegundos, o null. */
    fun exifDate(input: InputStream): Long? = runCatching {
        val e = ExifInterface(input)
        val s = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(s.trim())?.time
    }.getOrNull()

    /**
     * Ángulo sugerido por el giro de la cabeza: < 15° frontal, < 65° oblicuo, si no perfil; el lado,
     * por la punta de la nariz frente al centro del óvalo ([side] > 0: lado derecho del paciente, como
     * en la cámara). null sin rostro.
     */
    fun suggest(yawDeg: Float?, side: Float?): Zone? {
        val y = abs(yawDeg ?: return null)
        val right = (side ?: 0f) >= 0f
        return when {
            y < 15f -> Zone.FACE_FRONTAL
            y < 65f -> if (right) Zone.FACE_OBLIQUE_R else Zone.FACE_OBLIQUE_L
            else -> if (right) Zone.FACE_PROFILE_R else Zone.FACE_PROFILE_L
        }
    }

    /** Fechas y ángulo sugerido (rostro) de las fotos elegidas. [default]: zona si no hay sugerencia. */
    suspend fun prepare(context: Context, uris: List<Uri>, default: Zone?, detectFaces: Boolean = true): List<ImportItem> =
        com.photomed.app.align.NativeGate.run("importar: ángulo sugerido") { prepareGated(context, uris, default, detectFaces) }

    private suspend fun prepareGated(context: Context, uris: List<Uri>, default: Zone?, detectFaces: Boolean): List<ImportItem> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val mp = if (detectFaces) runCatching { MediaPipeFace(context) }.getOrNull() else null
        try {
            uris.map { u ->
                val date = runCatching { context.contentResolver.openInputStream(u)?.use { exifDate(it) } }.getOrNull()
                val sug = mp?.let { m ->
                    runCatching {
                        val b = ImageOps.loadFull(context, u, 720, ImageEdit.NONE)
                        val d = m.detect(b)
                        if (d.points.isEmpty()) null else suggest(d.yaw, Standardization.facingSide(d.points))
                    }.getOrNull()
                }
                ImportItem(u, date ?: now, date != null, sug ?: default, suggested = sug != null)
            }
        } finally { mp?.close() }
    }

    /**
     * Guarda las fotos en el expediente. [decode]: imagen con su orientación aplicada. Devuelve las
     * fotos guardadas (las que no se pudieron leer se omiten).
     */
    /** Resultado: fotos guardadas y las omitidas por estar ya importadas (misma foto de origen). */
    class Result(val saved: List<Photo>, val duplicates: Int)

    /** SHA-1 del archivo de origen (para no importar dos veces la misma foto). */
    fun sha1(input: InputStream): String {
        val md = java.security.MessageDigest.getInstance("SHA-1"); val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Idempotente: una foto ya importada en el expediente (misma huella SHA-1 del archivo de origen,
     * guardada en sus datos) no se vuelve a importar, ni dos veces en la misma tanda.
     */
    suspend fun importAll(
        context: Context, repo: PhotoRepository, exp: Expediente, items: List<ImportItem>,
        decode: suspend (Uri) -> Bitmap = { ImageOps.loadFull(context, it, 4096, ImageEdit.NONE) },
        hashOf: (Uri) -> String? = { u -> runCatching { context.contentResolver.openInputStream(u)?.use { sha1(it) } }.getOrNull() },
    ): Result {
        val known = HashSet<String>()
        withContext(Dispatchers.IO) { repo.photos(exp).forEach { p -> repo.readInfo(p)?.conditions?.get("SourceHash")?.let { known += it } } }
        val todo = ArrayList<ImportItem>(); val hashes = HashMap<Uri, String>(); var dup = 0
        for (it in items) {
            val h = withContext(Dispatchers.IO) { hashOf(it.uri) }
            if (h != null && !known.add(h)) { dup++; continue }
            if (h != null) hashes[it.uri] = h
            todo += it
        }
        return Result(import(context, repo, exp, todo, decode, hashes), dup)
    }

    suspend fun import(
        context: Context, repo: PhotoRepository, exp: Expediente, items: List<ImportItem>,
        decode: suspend (Uri) -> Bitmap = { ImageOps.loadFull(context, it, 4096, ImageEdit.NONE) },
        hashes: Map<Uri, String> = emptyMap(),
    ): List<Photo> {
        val used = HashSet<Long>()
        // nombres únicos por zona y fecha (el nombre lleva la fecha al milisegundo)
        withContext(Dispatchers.IO) { repo.photos(exp).forEach { used += it.takenAt } }
        val out = ArrayList<Photo>()
        for (it in items) {
            val zone = it.zone ?: continue
            val bmp = runCatching { decode(it.uri) }.getOrNull() ?: continue
            var t = it.takenAt
            while (!used.add(t)) t++
            val f = File(context.cacheDir, "import_${System.nanoTime()}.jpg")
            try {
                withContext(Dispatchers.IO) {
                    f.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.JPEG, 95, o) }
                    out += repo.savePhoto(exp, zone, f, takenAt = t,
                        variant = FacialExpression.variantTag(it.expression.takeIf { e -> zone.guide in e.guides } ?: FacialExpression.NEUTRAL),
                        conditions = mapOf("Source" to "importada", "DateSource" to if (it.dateFromPhoto) "EXIF" else "importación",
                            "Expression" to it.expression.label) +
                            (hashes[it.uri]?.let { h -> mapOf("SourceHash" to h) } ?: emptyMap()))
                }
            } finally { f.delete() }
        }
        return out
    }
}
