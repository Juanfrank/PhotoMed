package com.photomed.app.align

import android.content.Context
import android.net.Uri
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.XmpFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Altura de la frente (cejas → línea de implantación) / interpupilar de una foto guardada, para el
 * seguimiento tricológico. Se mide después de guardar, en segundo plano y esperando su turno de los
 * modelos (nunca se omite), y se escribe en los datos de la foto (`pm:Cond_HairlineIpd`).
 */
object HairlineMeasure {
    suspend fun measure(context: Context, uri: Uri): Float? = NativeGate.run("línea del pelo de la foto") {
        withContext(Dispatchers.Default) {
            runCatching {
                val bmp = ImageOps.load34(context, uri, 360)
                val d = LandmarkDetector(stream = false, context, FaceEngine.MEDIAPIPE).use { it.detectFull(Detector.FACE, bmp) }
                if (d.points.size < LiveDecision.MIN_POINTS) return@runCatching null
                HairSkinSegmenter.createOrNull(context)?.use { seg ->
                    val labels = seg.labels(bmp) ?: return@use null
                    val pts = runCatching { BrowRefine.refineKeys(d.points, FaceCrop.lum(bmp), labels) }.getOrDefault(d.points)
                    HairSkin.hairline(labels, pts)?.heightIpd
                }
            }.getOrNull()
        }
    }

    /** Mide y guarda en la foto; devuelve el valor (o null si no hay línea del pelo visible). */
    suspend fun measureAndStore(context: Context, uri: Uri): Float? {
        val v = measure(context, uri) ?: return null
        withContext(Dispatchers.IO) {
            XmpFile.update(context, uri) { PhotoMetadata.withAttr(it, "Cond_HairlineIpd", "%.3f".format(java.util.Locale.US, v)) }
        }
        return v
    }
}
