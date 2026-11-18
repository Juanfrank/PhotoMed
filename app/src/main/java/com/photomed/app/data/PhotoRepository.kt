package com.photomed.app.data

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Almacenamiento basado en carpetas:
 *
 *   Pictures/PhotoMed/<expediente>/expediente.json
 *   Pictures/PhotoMed/<expediente>/<zona>/<zona>_<yyyyMMdd_HHmmss_SSS>.jpg
 *
 * Los archivos viven en el almacenamiento privado de la app (no aparecen en la
 * galería del teléfono ni requieren permisos de almacenamiento).
 */
class PhotoRepository(val root: File) {

    init {
        root.mkdirs()
    }

    constructor(context: Context) : this(
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "PhotoMed")
    )

    private val stampFormat get() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    // ---------- Expedientes ----------

    fun listExpedientes(): List<Expediente> =
        root.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { readExpediente(it) }
            .sortedByDescending { it.lastPhotoAt ?: it.createdAt }

    fun getExpediente(id: String): Expediente? = readExpediente(File(root, id))

    fun createExpediente(patientName: String, recordNumber: String, notes: String): Expediente {
        val base = sanitize(listOf(recordNumber, patientName).filter { it.isNotBlank() }.joinToString("_"))
            .ifBlank { "expediente" }
        var name = base
        var i = 2
        while (File(root, name).exists()) name = "${base}_${i++}"
        val dir = File(root, name).apply { mkdirs() }
        val now = System.currentTimeMillis()
        writeMeta(dir, patientName.trim(), recordNumber.trim(), notes.trim(), now)
        return readExpediente(dir)!!
    }

    fun updateExpediente(exp: Expediente, patientName: String, recordNumber: String, notes: String) {
        writeMeta(exp.dir, patientName.trim(), recordNumber.trim(), notes.trim(), exp.createdAt)
    }

    fun deleteExpediente(exp: Expediente) {
        exp.dir.deleteRecursively()
    }

    private fun writeMeta(dir: File, name: String, record: String, notes: String, createdAt: Long) {
        val json = JSONObject()
            .put("patientName", name)
            .put("recordNumber", record)
            .put("notes", notes)
            .put("createdAt", createdAt)
        File(dir, META).writeText(json.toString(2))
    }

    private fun readExpediente(dir: File): Expediente? {
        val meta = File(dir, META)
        if (!dir.isDirectory || !meta.exists()) return null
        return try {
            val json = JSONObject(meta.readText())
            val photos = photos(dir)
            Expediente(
                id = dir.name,
                patientName = json.optString("patientName"),
                recordNumber = json.optString("recordNumber"),
                notes = json.optString("notes"),
                createdAt = json.optLong("createdAt", dir.lastModified()),
                dir = dir,
                photoCount = photos.size,
                lastPhotoAt = photos.maxOfOrNull { it.takenAt },
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Fotos ----------

    fun photos(exp: Expediente, zone: Zone? = null): List<Photo> = photos(exp.dir, zone)

    private fun photos(dir: File, zone: Zone? = null): List<Photo> {
        val zones = zone?.let { listOf(it) } ?: Zone.entries
        return zones.flatMap { z ->
            File(dir, z.id).listFiles { f -> f.isFile && f.extension.equals("jpg", true) }.orEmpty()
                .map { Photo(it, z, timestampOf(it)) }
        }.sortedBy { it.takenAt }
    }

    fun newPhotoFile(exp: Expediente, zone: Zone): File {
        val dir = File(exp.dir, zone.id).apply { mkdirs() }
        return File(dir, "${zone.id}_${stampFormat.format(Date())}.jpg")
    }

    fun deletePhoto(photo: Photo) {
        photo.file.delete()
    }

    private fun timestampOf(file: File): Long {
        val stamp = file.nameWithoutExtension.takeLast(19) // yyyyMMdd_HHmmss_SSS
        return try {
            stampFormat.parse(stamp)?.time ?: file.lastModified()
        } catch (e: Exception) {
            file.lastModified()
        }
    }

    companion object {
        private const val META = "expediente.json"

        fun sanitize(s: String): String =
            Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
                .replace("[^A-Za-z0-9_-]+".toRegex(), "_")
                .trim('_')
                .take(60)
    }
}
