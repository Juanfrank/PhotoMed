package com.photomed.app.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Lectura y reescritura del XMP de una foto ya guardada (galería del teléfono o archivo). Solo se
 * tocan los metadatos; los píxeles no se vuelven a codificar.
 */
object XmpFile {
    fun read(context: Context, uri: Uri): String? = runCatching {
        if (uri.scheme == "file") ExifInterface(uri.path!!).getAttribute(ExifInterface.TAG_XMP)
        else context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_XMP) }
    }.getOrNull()

    /**
     * Reescribe el XMP con [change] (recibe el actual). true si se pudo guardar. De una en una por foto
     * ([PhotoLocks]): dos reescrituras a la vez del mismo JPEG perdían cambios o lo dañaban. Los fallos
     * quedan en el registro de acciones (antes se perdían).
     */
    fun update(context: Context, uri: Uri, change: (String?) -> String): Boolean = PhotoLocks.on(uri.toString()) { runCatching {
        if (uri.scheme == "file") {
            val e = ExifInterface(File(uri.path!!))
            e.setAttribute(ExifInterface.TAG_XMP, change(e.getAttribute(ExifInterface.TAG_XMP)))
            e.saveAttributes()
        } else {
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                val e = ExifInterface(pfd.fileDescriptor)
                e.setAttribute(ExifInterface.TAG_XMP, change(e.getAttribute(ExifInterface.TAG_XMP)))
                e.saveAttributes()
            }
        }
        true
    }.onFailure { com.photomed.app.CrashLog.crumb("XMP no guardado: ${it.javaClass.simpleName} ${it.message.orEmpty().take(80)}") }
        .getOrDefault(false) }
}
