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
        private const val KEY = "face_engine"
        fun get(context: Context): FaceEngine =
            runCatching { valueOf(context.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString(KEY, ML_KIT.name)!!) }
                .getOrDefault(ML_KIT)
        fun set(context: Context, e: FaceEngine) =
            context.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString(KEY, e.name).apply()
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
        "brow_l_top_" to intArrayOf(336, 296, 334, 293, 300), "brow_l_bottom_" to intArrayOf(285, 295, 282, 283, 276),
        "brow_r_top_" to intArrayOf(70, 63, 105, 66, 107), "brow_r_bottom_" to intArrayOf(46, 53, 52, 65, 55),
        "eye_l_" to intArrayOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398),
        "eye_r_" to intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246),
        "lip_upper_top_" to intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291),
        "lip_upper_bottom_" to intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310),
        "lip_lower_top_" to intArrayOf(78, 95, 88, 178, 87, 14, 317, 402, 318),
        "lip_lower_bottom_" to intArrayOf(146, 91, 181, 84, 17, 314, 405, 321, 375),
        "nose_bridge_" to intArrayOf(168, 6), "nose_bottom_" to intArrayOf(98, 2, 327),
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
        return Detection(pts, e?.first, e?.second, e?.third)
    }

    override fun close() = lm.close()
}
