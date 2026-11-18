package com.photomed.app.data

import java.io.File

data class Expediente(
    /** Nombre de la carpeta; identificador estable. */
    val id: String,
    val patientName: String,
    val recordNumber: String,
    val notes: String,
    val createdAt: Long,
    val dir: File,
    val photoCount: Int = 0,
    val lastPhotoAt: Long? = null,
)

data class Photo(
    val file: File,
    val zone: Zone,
    val takenAt: Long,
) {
    val key: String get() = file.absolutePath
}
