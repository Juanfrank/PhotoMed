package com.photomed.app.data

import android.net.Uri
import java.io.File

data class Expediente(
    /** Nombre de la carpeta (y del álbum en la galería); identificador estable. */
    val id: String,
    val patientName: String,
    val recordNumber: String,
    val notes: String,
    val consent: Boolean,
    val createdAt: Long,
    val metaDir: File,
    val photoCount: Int = 0,
    val lastPhotoAt: Long? = null,
)

data class Photo(
    val uri: Uri,
    val displayName: String,
    val zone: Zone,
    val takenAt: Long,
) {
    val key: String get() = uri.toString()
}
