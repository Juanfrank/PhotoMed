package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.Closeable
import kotlin.math.asin
import kotlin.math.atan2

/** Motor de puntos faciales. */
enum class FaceEngine(val label: String, val description: String) {
    ML_KIT("Google ML Kit", "133 puntos de contorno + 7 puntos; ángulos de la cabeza. Rápido."),
    MEDIAPIPE("MediaPipe Face Landmarker", "478 puntos 3D (malla facial) agrupados como ML Kit; pose 3D de la cabeza; modelo de 3,7 MB.");

    companion object {
        /**
         * Motor de la app: MediaPipe (malla de 478 puntos) siempre; ML Kit queda como respaldo en el
         * mismo cuadro si MediaPipe falla o no encuentra la cara ([LandmarkDetector]) y como
         * comprobación de la malla en las fotos ([PhotoAnnotator]). Ya no se elige en los ajustes.
         */
        @Suppress("UNUSED_PARAMETER")
        fun get(context: Context): FaceEngine = MEDIAPIPE
    }
}

/**
 * Correspondencia de la malla de MediaPipe (478 puntos) con los grupos de contorno de ML Kit
 * (óvalo 36, cejas 4×5, ojos 2×16, labios 11+9+9+9, nariz 2+3) y los puntos sueltos, para que
 * ambos motores alimenten la misma lógica de alineación y coincidencia. Validada con LFW en
 * `tools/eval/extract.py`.
 */
object FaceMeshMap {
    val GROUPS: Map<String, IntArray> = linkedMapOf(
        "oval_" to intArrayOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152,
            148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109),
        "brow_l_top_" to intArrayOf(300, 293, 334, 296, 336), "brow_l_bottom_" to intArrayOf(276, 283, 282, 295, 285),
        "brow_r_top_" to intArrayOf(70, 63, 105, 66, 107), "brow_r_bottom_" to intArrayOf(46, 53, 52, 65, 55),
        "eye_l_" to intArrayOf(362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382),
        "eye_r_" to intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7),
        "lip_upper_top_" to intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291),
        "lip_upper_bottom_" to intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310),
        "lip_lower_top_" to intArrayOf(318, 402, 317, 14, 87, 178, 88, 95, 78),
        "lip_lower_bottom_" to intArrayOf(375, 321, 405, 314, 17, 84, 181, 91, 146),
        "nose_bridge_" to intArrayOf(168, 4), "nose_bottom_" to intArrayOf(98, 2, 327),
    )
    /** Iris (468–477) para pupilas; base nasal y comisuras. */
    val SINGLE: Map<String, Int> = linkedMapOf(Landmarks.EYE_L to 473, Landmarks.EYE_R to 468, Landmarks.NOSE_BASE to 2,
        Landmarks.MOUTH_L to 291, Landmarks.MOUTH_R to 61)

    /** [xy]: coordenadas normalizadas (0..1) de la malla; salida normalizada por el ancho (y ∈ [0, h/w]). */
    fun toKeys(xy: (Int) -> Pair<Float, Float>, count: Int, w: Int, h: Int): Map<String, Pt> {
        val out = LinkedHashMap<String, Pt>()
        fun p(i: Int): Pt? = if (i < count) xy(i).let { (x, y) -> Pt(x, y * h / w) } else null
        for ((g, idx) in GROUPS) idx.forEachIndexed { k, i -> p(i)?.let { out["$g$k"] = it } }
        for ((k, i) in SINGLE) p(i)?.let { out[k] = it }
        // rasgos de la malla completa (ojos, cejas, nariz, labios) para alinear y validar con más precisión
        for (k in Landmarks.MESH_FEATURES) p(k.removePrefix("m").toInt())?.let { out[k] = it }
        return out
    }

    /** Guiñada, cabeceo y alabeo (°) de la matriz 4×4 de transformación facial (column-major). */
    fun euler(m: FloatArray): Triple<Float, Float, Float> {
        fun r(i: Int, j: Int) = m[j * 4 + i]
        val yaw = Math.toDegrees(asin((-r(2, 0)).coerceIn(-1f, 1f).toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2(r(2, 1).toDouble(), r(2, 2).toDouble())).toFloat()
        val roll = Math.toDegrees(atan2(r(1, 0).toDouble(), r(0, 0).toDouble())).toFloat()
        return Triple(yaw, pitch, roll)
    }
}

/** MediaPipe Face Landmarker en el dispositivo (modelo incluido en assets/face_landmarker.task). */
class MediaPipeFace(context: Context) : Closeable {
    private val lm: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setOutputFacialTransformationMatrixes(true)
            .build()
    )

    fun detect(bmp: Bitmap): Detection {
        val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val r = lm.detect(BitmapImageBuilder(argb).build())
        val face = r.faceLandmarks().firstOrNull() ?: return Detection.EMPTY
        val pts = FaceMeshMap.toKeys({ i -> face[i].x() to face[i].y() }, face.size, bmp.width, bmp.height)
        val m = r.facialTransformationMatrixes().orElse(null)?.firstOrNull()
        val e = m?.let { FaceMeshMap.euler(it) }
        // Cabeceo en la convención de ML Kit (positivo = barbilla arriba); la matriz de MediaPipe usa la opuesta.
        return Detection(pts, e?.first, e?.second?.let { -it }, e?.third)
    }

    /** Malla completa (478 puntos; x e y divididas por el ancho de la imagen) o null sin rostro. */
    fun mesh(bmp: Bitmap): List<Pt>? {
        val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val face = lm.detect(BitmapImageBuilder(argb).build()).faceLandmarks().firstOrNull() ?: return null
        val k = bmp.height / bmp.width.toFloat()
        return face.map { Pt(it.x(), it.y() * k) }
    }

    override fun close() = lm.close()

    companion object {
        private var shared: MediaPipeFace? = null
        private val lock = kotlinx.coroutines.sync.Mutex()
        /** Motivo del último fallo de la malla (para mostrarlo al usuario y diagnosticar). */
        @Volatile var lastError: String? = null

        /**
         * Una sola instancia para el análisis de fotos, usada de una en una (varias a la vez, con
         * fotos grandes, pueden agotar la memoria). null si no se puede crear.
         */
        suspend fun <T> withShared(context: Context, block: (MediaPipeFace) -> T): T? = lock.withLockCompat {
            val m = shared ?: runCatching { MediaPipeFace(context.applicationContext) }
                // se conserva el primer error (los siguientes de una clase que no se inició solo repiten su nombre)
                .onFailure { if (lastError == null) lastError = "no se pudo cargar el modelo: ${it.javaClass.simpleName}: ${it.message}" + (it.cause?.let { c -> " (${c.javaClass.simpleName}: ${c.message})" } ?: "") }
                .getOrNull()?.also { shared = it }
            m?.let { mp -> runCatching { block(mp) }.onFailure { if (lastError == null) lastError = "${it.javaClass.simpleName}: ${it.message}" }.getOrNull() }
        }

        private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(action: suspend () -> T): T {
            lock()
            try { return action() } finally { unlock() }
        }
    }
}
