package com.photomed.app.align

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Modelo opcional de segmentación pelo / piel (MediaPipe "selfie multiclass 256×256", Apache 2.0,
 * 16 MB). No va dentro de la app (la APK pesaría 16 MB más): se descarga solo cuando la persona lo
 * pide en Ajustes, desde el servidor público de modelos de MediaPipe. La descarga no envía ningún
 * dato del usuario ni de los pacientes; el archivo se comprueba con su huella SHA-256.
 */
object ModelStore {
    const val URL_HAIR_SKIN = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite"
    const val SHA256 = "c6748b1253a99067ef71f7e26ca71096cd449baefa8f101900ea23016507e0e0"
    const val SIZE = 16_371_837L
    const val FILE = "selfie_multiclass.tflite"

    fun file(context: Context) = File(context.filesDir, "models/$FILE")

    fun available(context: Context): Boolean = file(context).let { it.isFile && it.length() == SIZE }

    /** Mapeo del archivo en memoria (lo que pide MediaPipe); null si no está descargado. */
    fun buffer(context: Context): ByteBuffer? {
        val f = file(context).takeIf { it.isFile && it.length() == SIZE } ?: return null
        return RandomAccessFile(f, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
    }

    fun delete(context: Context) { file(context).delete() }

    /** Descarga y verifica; [progress] recibe 0..1. Lanza una excepción con un mensaje legible si falla. */
    suspend fun download(context: Context, url: String = URL_HAIR_SKIN, sha256: String = SHA256,
                         dst: File = file(context), progress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, dst.name + ".part")
        val md = MessageDigest.getInstance("SHA-256")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
        try {
            if (conn.responseCode != 200) error("El servidor respondió ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: SIZE
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var n: Int; var read = 0L
                    while (input.read(buf).also { n = it } >= 0) {
                        coroutineContext.ensureActive()
                        out.write(buf, 0, n); md.update(buf, 0, n); read += n
                        progress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            if (hash != sha256) { tmp.delete(); error("El archivo descargado no es el esperado (huella distinta)") }
            if (!tmp.renameTo(dst)) { tmp.delete(); error("No se pudo guardar el modelo") }
        } finally {
            conn.disconnect(); if (tmp.exists() && !dst.exists()) tmp.delete()
        }
    }
}
