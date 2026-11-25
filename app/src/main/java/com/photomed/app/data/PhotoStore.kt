package com.photomed.app.data

import com.photomed.app.i18n.tr
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/** Foto tal como existe en la galería. */
data class StoredPhoto(val uri: Uri, val displayName: String, val dateTaken: Long)

/** Resultado de un borrado: puede requerir confirmación del usuario (fotos de otra instalación). */
sealed interface DeleteResult {
    data object Done : DeleteResult
    data class NeedsConfirmation(val intentSender: IntentSender) : DeleteResult
}

/**
 * Almacén de fotos visible en la galería del teléfono: un álbum por paciente en
 * `Pictures/PhotoMed/<carpeta del expediente>/`.
 */
interface PhotoStore {
    fun list(folder: String): List<StoredPhoto>
    fun folders(): List<String>
    /** Fotos de todas las carpetas (una sola consulta donde se puede; antes una por expediente). */
    fun listAll(): Map<String, List<StoredPhoto>> = folders().associateWith { list(it) }
    fun save(folder: String, displayName: String, source: File, dateTaken: Long): Uri
    fun delete(uris: List<Uri>): DeleteResult
    /** Cambia el nombre y la fecha de toma de una foto; devuelve su URI (la misma en la galería). */
    fun rename(uri: Uri, displayName: String, dateTaken: Long): Uri

    companion object {
        const val ALBUM = "PhotoMed"

        fun create(context: Context): PhotoStore =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStorePhotoStore(context.contentResolver)
            else FilePhotoStore(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM),
                onChanged = { paths -> MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) },
            )
    }
}

/** Android 10+: MediaStore con RELATIVE_PATH; no requiere permisos para escribir. */
@RequiresApi(Build.VERSION_CODES.Q)
class MediaStorePhotoStore(private val resolver: ContentResolver) : PhotoStore {
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val base = "${Environment.DIRECTORY_PICTURES}/${PhotoStore.ALBUM}/"

    override fun list(folder: String): List<StoredPhoto> = query(
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf("$base$folder/")
    ).map { it.second }

    override fun folders(): List<String> = query(
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$base%")
    ).map { it.first.removePrefix(base).trimEnd('/') }.filter { it.isNotBlank() && '/' !in it }.distinct()

    override fun listAll(): Map<String, List<StoredPhoto>> = query(
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$base%")
    ).mapNotNull { (path, sp) -> path.removePrefix(base).trimEnd('/').takeIf { it.isNotBlank() && '/' !in it }?.let { it to sp } }
        .groupBy({ it.first }, { it.second })

    private fun query(selection: String, args: Array<String>): List<Pair<String, StoredPhoto>> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val out = mutableListOf<Pair<String, StoredPhoto>>()
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val taken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val path = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (c.moveToNext()) {
                out += c.getString(path) to StoredPhoto(
                    ContentUris.withAppendedId(collection, c.getLong(id)), c.getString(name), c.getLong(taken),
                )
            }
        }
        return out
    }

    override fun save(folder: String, displayName: String, source: File, dateTaken: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (displayName.endsWith(".gif", true)) "image/gif" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$base$folder/")
            put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        // los vídeos (MP4 exportados) van en la colección de vídeo, en la misma carpeta del álbum
        val video = displayName.endsWith(".mp4", true)
        if (video) values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        val target = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else collection
        val uri = resolver.insert(target, values) ?: error(tr("No se pudo crear el archivo en la galería"))
        try {
            resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    override fun rename(uri: Uri, displayName: String, dateTaken: Long): Uri {
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
        }, null, null)
        return uri
    }

    override fun delete(uris: List<Uri>): DeleteResult {
        val notOwned = mutableListOf<Uri>()
        for (uri in uris) {
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return DeleteResult.NeedsConfirmation(e.userAction.actionIntent.intentSender)
                }
                notOwned += uri
            }
        }
        if (notOwned.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return DeleteResult.NeedsConfirmation(MediaStore.createDeleteRequest(resolver, notOwned).intentSender)
        }
        return DeleteResult.Done
    }
}

/** Android 8–9 (y pruebas): archivos en la carpeta pública + escaneo de medios. */
class FilePhotoStore(
    private val root: File,
    private val onChanged: (List<String>) -> Unit = {},
) : PhotoStore {
    override fun list(folder: String): List<StoredPhoto> =
        File(root, folder).listFiles { f -> f.isFile && f.extension.equals("jpg", true) }.orEmpty()
            .map { StoredPhoto(Uri.fromFile(it), it.name, it.lastModified()) }

    override fun folders(): List<String> =
        root.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }

    override fun save(folder: String, displayName: String, source: File, dateTaken: Long): Uri {
        val dir = File(root, folder).apply { mkdirs() }
        val target = File(dir, displayName)
        source.copyTo(target, overwrite = true)
        target.setLastModified(dateTaken)
        onChanged(listOf(target.absolutePath))
        return Uri.fromFile(target)
    }

    override fun rename(uri: Uri, displayName: String, dateTaken: Long): Uri {
        val src = File(uri.path!!)
        val dst = File(src.parentFile, displayName)
        if (dst != src && !src.renameTo(dst)) error(tr("No se pudo renombrar la foto"))
        dst.setLastModified(dateTaken)
        onChanged(listOf(src.absolutePath, dst.absolutePath))
        return Uri.fromFile(dst)
    }

    override fun delete(uris: List<Uri>): DeleteResult {
        val paths = uris.mapNotNull { it.path }
        paths.forEach { File(it).delete() }
        onChanged(paths)
        return DeleteResult.Done
    }
}
