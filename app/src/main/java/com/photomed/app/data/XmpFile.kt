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

    /** Reescribe el XMP con [change] (recibe el actual). true si se pudo guardar. */
    fun update(context: Context, uri: Uri, change: (String?) -> String): Boolean = runCatching {
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
    }.getOrDefault(false)
}
