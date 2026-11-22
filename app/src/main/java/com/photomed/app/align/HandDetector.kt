package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable

/**
 * Manos con MediaPipe Hand Landmarker (21 puntos por mano; modelo oficial de Google, Apache 2.0,
 * incluido en assets/hand_landmarker.task): para comprobar en las zonas de manos que se vean las dos,
 * completas, con los cinco dedos extendidos y la cara pedida (ver [ZoneCriteria]).
 */
class HandDetector(context: Context) : Closeable {
    private val lm: HandLandmarker = HandLandmarker.createFromOptions(context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .build())

    /** Manos del cuadro (puntos en unidades del ancho) con la etiqueta de MediaPipe. */
    fun detect(bmp: Bitmap): List<ZoneCriteria.Hand> {
        val src = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val r = lm.detect(BitmapImageBuilder(src).build())
        val k = bmp.height / bmp.width.toFloat()
        return r.landmarks().mapIndexed { i, pts ->
            ZoneCriteria.Hand(pts.map { Pt(it.x(), it.y() * k) }, r.handednesses().getOrNull(i)?.firstOrNull()?.categoryName() ?: "")
        }
    }

    override fun close() = lm.close()

    companion object {
        const val MODEL = "hand_landmarker.task"
        fun createOrNull(context: Context): HandDetector? = runCatching { HandDetector(context) }.getOrNull()
    }
}
