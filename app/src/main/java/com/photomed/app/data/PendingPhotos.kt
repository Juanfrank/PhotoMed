package com.photomed.app.data

import android.content.Context
import java.io.File

/**
 * Tomas que no se pudieron guardar en la galería (sin espacio, permiso revocado, MediaStore ocupado):
 * antes el archivo temporal se borraba y la foto clínica se perdía. Se conservan en la app
 * (`files/pendientes/<expediente>/<zona>@<instante>.jpg`) y se reintentan al volver a abrir la cámara
 * de ese expediente.
 */
object PendingPhotos {
    private fun dir(context: Context, expId: String) = File(File(context.filesDir, "pendientes"), expId)

    /** Conserva [capture] (se mueve). Devuelve el archivo conservado o null si no se pudo. */
    fun keep(context: Context, expId: String, zone: Zone, takenAt: Long, capture: File): File? = runCatching {
        val d = dir(context, expId).apply { mkdirs() }
        File(d, "${zone.id}@$takenAt.jpg").also { dst ->
            if (!capture.renameTo(dst)) { capture.copyTo(dst, overwrite = true); capture.delete() }
        }
    }.getOrNull()

    fun count(context: Context, expId: String): Int = dir(context, expId).listFiles()?.count { it.name.endsWith(".jpg") } ?: 0

    /** Reintenta guardar las pendientes del expediente; devuelve cuántas se guardaron. */
    fun retry(context: Context, repo: PhotoRepository, exp: Expediente): Int {
        var n = 0
        dir(context, exp.id).listFiles()?.filter { it.name.endsWith(".jpg") }?.forEach { f ->
            val zone = Zone.fromId(f.name.substringBefore('@')) ?: return@forEach
            val at = f.name.substringAfter('@').removeSuffix(".jpg").toLongOrNull() ?: f.lastModified()
            runCatching { repo.savePhoto(exp, zone, f, takenAt = at, conditions = mapOf("Status" to "RECUPERADA")) }
                .onSuccess { f.delete(); n++ }
                .onFailure { com.photomed.app.CrashLog.crumb("pendiente sin guardar aún: ${f.name}") }
        }
        return n
    }
}
