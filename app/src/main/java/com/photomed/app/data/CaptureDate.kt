package com.photomed.app.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fecha de captura en los metadatos de la foto: EXIF (DateTimeOriginal, DateTime) y XMP (pm:CapturedAt, xmp:CreateDate). */
object CaptureDate {
    fun write(context: Context, uri: Uri, takenAt: Long): Boolean = runCatching {
        fun edit(e: ExifInterface) {
            val exif = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(takenAt))
            e.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exif)
            e.setAttribute(ExifInterface.TAG_DATETIME, exif)
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(takenAt))
            e.getAttribute(ExifInterface.TAG_XMP)?.let { x ->
                e.setAttribute(ExifInterface.TAG_XMP, x
                    .replace(Regex("""pm:CapturedAt="[^"]*""""), "pm:CapturedAt=\"$takenAt\"")
                    .replace(Regex("""xmp:CreateDate="[^"]*""""), "xmp:CreateDate=\"$iso\""))
            }
            e.saveAttributes()
        }
        if (uri.scheme == "file") edit(ExifInterface(File(uri.path!!)))
        else context.contentResolver.openFileDescriptor(uri, "rw")!!.use { edit(ExifInterface(it.fileDescriptor)) }
        true
    }.getOrDefault(false)
}
