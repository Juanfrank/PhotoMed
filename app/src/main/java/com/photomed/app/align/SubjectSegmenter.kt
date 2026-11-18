package com.photomed.app.align

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.Closeable
import java.nio.ByteOrder

/**
 * Selección automática del sujeto (como al crear stickers): ML Kit Selfie Segmentation con el
 * modelo incluido, en el dispositivo. Devuelve la silueta de la persona (umbral de confianza 0,5),
 * limpia y con el mayor componente conexo. Bloqueante: usar fuera del hilo principal.
 */
class SubjectSegmenter : Closeable {
    private val client = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
    )

    fun segment(bmp: Bitmap): Mask? = try {
        val r = Tasks.await(client.process(InputImage.fromBitmap(bmp, 0)))
        val buf = r.buffer.order(ByteOrder.nativeOrder())
        val w = r.width; val h = r.height
        val m = Mask(w, h)
        for (i in 0 until w * h) m.data[i] = buf.getFloat() >= 0.5f
        MaskOps.clean(m, radius = 2)
    } catch (e: Exception) {
        null
    }

    override fun close() = client.close()
}

/** Pixeles RGB de un bitmap (para la varita mágica). */
fun Bitmap.rgb(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
