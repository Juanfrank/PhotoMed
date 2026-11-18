package com.photomed.app.ui.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nombre de archivo para compartir sin datos del paciente (zona + fecha + índice si hay varias del mismo día). */
fun anonymizedName(photo: Photo, index: Int? = null): String =
    "PhotoMed_${photo.zone.id}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(photo.takenAt))}" +
        (index?.let { "_%02d".format(it) } ?: "") + ".jpg"

/**
 * Copia las fotos a la caché (opcionalmente sin metadatos del paciente) y abre el selector para
 * compartir (una o varias). Devuelve false si alguna no se pudo leer.
 */
suspend fun sharePhotos(context: Context, photos: List<Photo>, anonymize: Boolean): Boolean {
    if (photos.isEmpty()) return false
    val uris: List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        photos.mapIndexedNotNull { i, photo ->
            runCatching {
                val name = if (anonymize) anonymizedName(photo, if (photos.size > 1) i + 1 else null) else photo.displayName
                val f = File(dir, name)
                context.contentResolver.openInputStream(photo.uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                if (anonymize) PhotoMetadata.strip(f)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            }.getOrNull()
        }
    }
    if (uris.isEmpty()) return false
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
    else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    intent.type = "image/jpeg"
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, if (anonymize) "Compartir (anonimizada)" else "Compartir"))
    return uris.size == photos.size
}
