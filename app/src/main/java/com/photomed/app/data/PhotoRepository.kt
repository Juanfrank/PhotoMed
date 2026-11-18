package com.photomed.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Expedientes y fotografías.
 *
 * - Metadatos del expediente: almacenamiento privado `files/expedientes/<id>/expediente.json`.
 * - Fotografías: galería del teléfono, álbum `Pictures/PhotoMed/<id>/`, nombre
 *   `<zona>_<yyyyMMdd_HHmmss_SSS>.jpg`, con los datos del paciente en EXIF/XMP.
 */
class PhotoRepository(
    private val metaRoot: File,
    private val store: PhotoStore,
    private val openStream: (Uri) -> InputStream? = { null },
) {
    init {
        metaRoot.mkdirs()
    }

    private val stampFormat get() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    // ---------- Expedientes ----------

    fun listExpedientes(): List<Expediente> =
        metaRoot.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { readExpediente(it) }
            .sortedByDescending { it.lastPhotoAt ?: it.createdAt }

    fun getExpediente(id: String): Expediente? = readExpediente(File(metaRoot, id))

    fun createExpediente(patientName: String, recordNumber: String, notes: String, consent: Boolean): Expediente {
        val base = sanitize(listOf(recordNumber, patientName).filter { it.isNotBlank() }.joinToString("_"))
            .ifBlank { "expediente" }
        val taken = (metaRoot.listFiles()?.map { it.name }.orEmpty() + store.folders()).toSet()
        var name = base
        var i = 2
        while (name in taken) name = "${base}_${i++}"
        val dir = File(metaRoot, name).apply { mkdirs() }
        writeMeta(dir, patientName.trim(), recordNumber.trim(), notes.trim(), consent, System.currentTimeMillis())
        return readExpediente(dir)!!
    }

    fun updateExpediente(exp: Expediente, patientName: String, recordNumber: String, notes: String, consent: Boolean) {
        writeMeta(exp.metaDir, patientName.trim(), recordNumber.trim(), notes.trim(), consent, exp.createdAt)
    }

    /** Borra el expediente y sus fotos de la galería. */
    fun deleteExpediente(exp: Expediente): DeleteResult {
        val result = store.delete(photos(exp).map { it.uri })
        if (result is DeleteResult.Done) exp.metaDir.deleteRecursively()
        return result
    }

    /**
     * Reconstruye expedientes a partir de álbumes PhotoMed de la galería que no tienen
     * metadatos locales (p. ej. tras reinstalar), leyendo los datos incrustados en las fotos.
     * Devuelve cuántos expedientes se recuperaron.
     */
    fun recoverFromGallery(): Int {
        var count = 0
        for (folder in store.folders()) {
            if (File(metaRoot, folder).exists()) continue
            val first = store.list(folder).firstOrNull() ?: continue
            val info = openStream(first.uri)?.use { PhotoMetadata.read(it) }
            val dir = File(metaRoot, folder).apply { mkdirs() }
            writeMeta(
                dir, info?.patientName?.ifBlank { null } ?: folder.replace('_', ' '),
                info?.recordNumber.orEmpty(), "Recuperado desde la galería", info?.consent ?: false,
                first.dateTaken,
            )
            count++
        }
        return count
    }

    private fun writeMeta(dir: File, name: String, record: String, notes: String, consent: Boolean, createdAt: Long) {
        val json = JSONObject()
            .put("patientName", name)
            .put("recordNumber", record)
            .put("notes", notes)
            .put("consent", consent)
            .put("createdAt", createdAt)
        File(dir, META).writeText(json.toString(2))
    }

    private fun readExpediente(dir: File): Expediente? {
        val meta = File(dir, META)
        if (!dir.isDirectory || !meta.exists()) return null
        return try {
            val json = JSONObject(meta.readText())
            val photos = photos(dir.name)
            Expediente(
                id = dir.name,
                patientName = json.optString("patientName"),
                recordNumber = json.optString("recordNumber"),
                notes = json.optString("notes"),
                consent = json.optBoolean("consent", false),
                createdAt = json.optLong("createdAt", dir.lastModified()),
                metaDir = dir,
                photoCount = photos.size,
                lastPhotoAt = photos.maxOfOrNull { it.takenAt },
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Fotos ----------

    fun photos(exp: Expediente, zone: Zone? = null): List<Photo> = photos(exp.id, zone)

    private fun photos(folder: String, zone: Zone? = null): List<Photo> =
        store.list(folder).mapNotNull { sp ->
            val z = zoneOf(sp.displayName) ?: return@mapNotNull null
            if (zone != null && z != zone) return@mapNotNull null
            Photo(sp.uri, sp.displayName, z, timestampOf(sp.displayName) ?: sp.dateTaken)
        }.sortedBy { it.takenAt }

    /**
     * Guarda en la galería una captura (archivo temporal), incrustando los datos del paciente.
     */
    fun savePhoto(
        exp: Expediente, zone: Zone, capture: File, takenAt: Long = System.currentTimeMillis(),
        cameraPitch: Float? = null, roll: Float? = null, focalNorm: Float? = null,
    ): Photo {
        PhotoMetadata.write(
            capture,
            PhotoInfo(exp.patientName, exp.recordNumber, exp.id, zone.id, zone.label, takenAt, exp.consent,
                cameraPitch, roll, focalNorm),
        )
        val name = "${zone.id}_${stampFormat.format(Date(takenAt))}.jpg"
        val uri = store.save(exp.id, name, capture, takenAt)
        return Photo(uri, name, zone, takenAt)
    }

    fun deletePhotos(photos: List<Photo>): DeleteResult = store.delete(photos.map { it.uri })

    fun readInfo(photo: Photo): PhotoInfo? = openStream(photo.uri)?.use { PhotoMetadata.read(it) }

    /**
     * Migra la versión 1 (fotos en almacenamiento privado) a la galería.
     */
    fun migrateLegacy(legacyRoot: File) {
        if (!legacyRoot.isDirectory) return
        legacyRoot.listFiles { f -> f.isDirectory }.orEmpty().forEach { oldDir ->
            val oldMeta = File(oldDir, META)
            if (!oldMeta.exists()) return@forEach
            val newDir = File(metaRoot, oldDir.name).apply { mkdirs() }
            if (!File(newDir, META).exists()) oldMeta.copyTo(File(newDir, META))
            val exp = readExpediente(newDir) ?: return@forEach
            Zone.entries.forEach { zone ->
                File(oldDir, zone.id).listFiles { f -> f.extension.equals("jpg", true) }.orEmpty().forEach { f ->
                    val t = timestampOf(f.name) ?: f.lastModified()
                    savePhoto(exp, zone, f, t)
                    f.delete()
                }
            }
            oldDir.deleteRecursively()
        }
    }

    private fun timestampOf(name: String): Long? {
        val stamp = name.substringBeforeLast('.').takeLast(19) // yyyyMMdd_HHmmss_SSS
        return try {
            stampFormat.parse(stamp)?.time
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val META = "expediente.json"

        fun create(context: Context): PhotoRepository {
            val repo = PhotoRepository(
                File(context.filesDir, "expedientes"),
                PhotoStore.create(context),
                openStream = { uri -> context.contentResolver.openInputStream(uri) },
            )
            return repo
        }

        fun legacyRoot(context: Context): File? =
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "PhotoMed") }

        /** Zona a partir del nombre de archivo (`<zona>_<fecha>.jpg`); gana el prefijo más largo. */
        fun zoneOf(displayName: String): Zone? =
            Zone.entries.filter { displayName.startsWith(it.id + "_") }.maxByOrNull { it.id.length }

        fun sanitize(s: String): String =
            Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
                .replace("[^A-Za-z0-9_-]+".toRegex(), "_")
                .trim('_')
                .take(60)
    }
}
