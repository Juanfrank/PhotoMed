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
    /** Limpieza de los datos privados de una foto ya borrada (relieve, trazos, anotaciones, ediciones). */
    private val forgetPhoto: (Uri) -> Unit = {},
) {
    init {
        metaRoot.mkdirs()
    }

    private val stampFormat get() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    // ---------- Expedientes ----------

    /** Expedientes con sus fotos de una sola consulta a la galería (antes una por expediente). */
    fun listExpedientes(): List<Expediente> {
        val all = runCatching { store.listAll() }.getOrNull()
        return metaRoot.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { readExpediente(it, all?.let { m -> toPhotos(m[it.name].orEmpty()) }) }
            .sortedByDescending { it.lastPhotoAt ?: it.createdAt }
    }

    /** Fotos de todos los expedientes (carpeta → fotos), de una sola consulta. */
    fun photosByExpediente(): Map<String, List<Photo>> = store.listAll().mapValues { (_, v) -> toPhotos(v) }

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

    private val pendingForget = HashMap<String, List<Uri>>()

    /** Borra el expediente y sus fotos de la galería. */
    fun deleteExpediente(exp: Expediente): DeleteResult {
        // todas las imágenes del álbum (también las de nombre no reconocido) y sus exportaciones (antes quedaban)
        val uris = (store.list(exp.id) + runCatching { store.list("${exp.id}/$EXPORT_DIR") }.getOrDefault(emptyList())).map { it.uri }.distinct()
        val result = store.delete(uris)
        // con confirmación del sistema, al volver a llamar las fotos ya no aparecen: se recuerdan para limpiar sus datos
        if (result !is DeleteResult.Done) synchronized(pendingForget) { pendingForget[exp.id] = uris }
        if (result is DeleteResult.Done) {
            // datos privados de cada foto (antes quedaban huérfanos)
            (uris + synchronized(pendingForget) { pendingForget.remove(exp.id).orEmpty() }).distinct().forEach { runCatching { forgetPhoto(it) } }
            exp.metaDir.deleteRecursively()
        }
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
        // atómico (temporal + fsync + renombrado): un corte a medias ya no deja el expediente ilegible
        com.photomed.app.align.AtomicFile.write(File(dir, META), json.toString(2))
    }

    private fun readExpediente(dir: File, prefetched: List<Photo>? = null): Expediente? {
        val meta = File(dir, META)
        if (!dir.isDirectory || !meta.exists()) return null
        return try {
            val json = JSONObject(meta.readText())
            val photos = prefetched ?: photos(dir.name)
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
            // expediente.json ilegible (corte a medias en versiones anteriores): se reconstruye con los datos
            // incrustados en sus fotos, como al recuperar desde la galería, en lugar de hacer desaparecer el expediente
            com.photomed.app.CrashLog.crumb("expediente ilegible: ${dir.name} (${e.javaClass.simpleName})")
            rebuildMeta(dir)
        }
    }

    /** Reescribe expediente.json desde el XMP de la primera foto del álbum; null si no hay fotos. */
    private fun rebuildMeta(dir: File): Expediente? = runCatching {
        val photos = photos(dir.name)
        val first = photos.minByOrNull { it.takenAt } ?: return null
        val info = openStream(first.uri)?.use { PhotoMetadata.read(it) }
        val broken = File(dir, META)
        broken.renameTo(File(dir, "$META.danado-${System.currentTimeMillis()}"))
        writeMeta(dir, info?.patientName?.ifBlank { null } ?: dir.name.replace('_', ' '), info?.recordNumber.orEmpty(),
            "Recuperado (archivo del expediente dañado)", info?.consent ?: false, first.takenAt)
        val json = JSONObject(File(dir, META).readText())
        Expediente(id = dir.name, patientName = json.optString("patientName"), recordNumber = json.optString("recordNumber"),
            notes = json.optString("notes"), consent = json.optBoolean("consent", false), createdAt = json.optLong("createdAt"),
            metaDir = dir, photoCount = photos.size, lastPhotoAt = photos.maxOfOrNull { it.takenAt })
    }.getOrNull()

    // ---------- Fotos ----------

    fun photos(exp: Expediente, zone: Zone? = null): List<Photo> = photos(exp.id, zone)

    private fun photos(folder: String, zone: Zone? = null): List<Photo> = toPhotos(store.list(folder), zone)

    private fun toPhotos(stored: List<StoredPhoto>, zone: Zone? = null): List<Photo> =
        stored.mapNotNull { sp ->
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
        conditions: Map<String, String> = emptyMap(),
        /** Variante (p. ej. lado "der"/"izq" en el modo individual de extremidades): va en el nombre. */
        variant: String? = null,
    ): Photo {
        PhotoMetadata.write(
            capture,
            PhotoInfo(exp.patientName, exp.recordNumber, exp.id, zone.id, zone.labelEs, takenAt, exp.consent,
                cameraPitch, roll, focalNorm, conditions),
        )
        val name = "${zone.id}_${variant?.let { "${it}_" } ?: ""}${stampFormat.format(Date(takenAt))}.jpg"
        val uri = store.save(exp.id, name, capture, takenAt)
        return Photo(uri, name, zone, takenAt)
    }

    /**
     * Guarda una imagen exportada (ajustada o composición) en el subálbum `<expediente>/Exportadas`:
     * visible en la galería, pero fuera de las fotos clínicas del expediente.
     */
    fun saveExport(exp: Expediente, file: File, displayName: String, takenAt: Long = System.currentTimeMillis()): Uri =
        store.save("${exp.id}/$EXPORT_DIR", displayName, file, takenAt)

    /**
     * Borra las fotos; sus datos privados solo cuando el borrado está hecho. Si pide confirmación al usuario,
     * se vuelve a llamar tras aceptarla (en Android 10 eso completa el borrado; en 11+ ya está hecho y solo limpia).
     */
    fun deletePhotos(photos: List<Photo>): DeleteResult = store.delete(photos.map { it.uri }).also { r ->
        if (r is DeleteResult.Done) photos.forEach { runCatching { forgetPhoto(it.uri) } }
    }

    /**
     * Cambia la fecha de captura de una foto: nombre del archivo (lleva la fecha) y fecha de toma de la
     * galería. Devuelve la foto con su nuevo nombre (la URI es la misma en la galería; en archivo cambia).
     * Los metadatos EXIF/XMP los actualiza quien llama (ver CaptureDate).
     */
    fun setCaptureDate(exp: Expediente, photo: Photo, takenAt: Long): Photo {
        val prefix = photo.displayName.substringBeforeLast('.').dropLast(19)
        val used = store.list(exp.id).map { it.displayName }.toSet() - photo.displayName
        var t = takenAt
        var name = "$prefix${stampFormat.format(Date(t))}.jpg"
        while (name in used) { t++; name = "$prefix${stampFormat.format(Date(t))}.jpg" }
        val uri = store.rename(photo.uri, name, t)
        return Photo(uri, name, photo.zone, t)
    }

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
                    // una foto que falla no detiene la migración de las demás (se queda donde estaba)
                    runCatching { savePhoto(exp, zone, f, t) }
                        .onSuccess { f.delete() }
                        .onFailure { com.photomed.app.CrashLog.write("Migrar una foto de la versión 1", it) }
                }
            }
            // solo si ya no queda ninguna foto sin migrar
            if (oldDir.walkTopDown().none { it.isFile && it.extension.equals("jpg", true) }) oldDir.deleteRecursively()
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
        const val EXPORT_DIR = "Exportadas"
        private const val META = "expediente.json"

        fun create(context: Context): PhotoRepository {
            val repo = PhotoRepository(
                File(context.filesDir, "expedientes"),
                PhotoStore.create(context),
                openStream = { uri -> context.contentResolver.openInputStream(uri) },
                forgetPhoto = { uri -> com.photomed.app.align.PhotoKeys.forget(context.applicationContext, uri.toString()) },
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
