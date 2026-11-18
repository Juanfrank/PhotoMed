package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.Closeable

/**
 * Puntos anatómicos en el dispositivo (ML Kit, modelos incluidos): rostro (7 puntos + 133 puntos
 * de contorno: óvalo, cejas, ojos, labios, nariz) o pose corporal (33 puntos). Coordenadas
 * normalizadas por el ancho. Llamadas bloqueantes: usar fuera del hilo principal.
 */
/** Puntos detectados + pose de la cabeza (Euler, °) si es un rostro. */
data class Detection(val points: Map<String, Pt>, val yaw: Float? = null, val pitch: Float? = null, val roll: Float? = null,
                     /** Dirección de la mirada (ver [Gaze]); null si no hay ojos. */
                     val gaze: Pt? = null) {
    companion object { val EMPTY = Detection(emptyMap()) }
}

/**
 * Detector de puntos: rostro con el motor elegido ([FaceEngine]: ML Kit o MediaPipe) y cuerpo con
 * ML Kit Pose. Ambos motores devuelven las mismas claves de puntos.
 */
class LandmarkDetector(stream: Boolean, context: Context? = null, val engine: FaceEngine = FaceEngine.ML_KIT) : Closeable {
    /** Motivo por el que MediaPipe no se pudo iniciar (si se pidió). */
    var initError: String? = null
        private set
    private val mediaPipe: MediaPipeFace? =
        if (engine == FaceEngine.MEDIAPIPE && context != null) runCatching { MediaPipeFace(context.applicationContext) }
            .onFailure { initError = "MediaPipe no se pudo iniciar (${it.javaClass.simpleName}); se usa ML Kit" }.getOrNull()
        else null
    private val face: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(if (stream) FaceDetectorOptions.PERFORMANCE_MODE_FAST else FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )
    private val pose: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(if (stream) PoseDetectorOptions.STREAM_MODE else PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
    )

    fun detect(detector: Detector, bmp: Bitmap): Map<String, Pt> = detectFull(detector, bmp).points

    /** Último error del detector (para diagnóstico en pantalla); null si el último cuadro fue bien. */
    @Volatile var lastError: String? = null
        private set
    private var mediaPipeFailures = 0

    fun detectFull(detector: Detector, bmp: Bitmap): Detection = try {
        val d = when (detector) {
            Detector.FACE -> detectFaceAny(bmp)
            Detector.POSE -> Detection(detectPose(bmp))
            Detector.NONE -> Detection.EMPTY
        }
        lastError = null
        d
    } catch (e: Exception) {
        lastError = e.javaClass.simpleName + (e.message?.let { ": " + it.take(80) } ?: "")
        Detection.EMPTY
    }

    /**
     * Rostro con el motor elegido. Si MediaPipe falla (excepción) o no encuentra rostro, se intenta
     * con ML Kit en ese mismo cuadro: un fallo de un motor nunca deja la app sin detección. Tras 5
     * fallos seguidos de MediaPipe se deja de intentar con él en este detector.
     */
    private fun detectFaceAny(bmp: Bitmap): Detection {
        if (mediaPipe != null && mediaPipeFailures < 5) {
            val mp = try { mediaPipe.detect(bmp).also { mediaPipeFailures = 0 } } catch (e: Exception) {
                mediaPipeFailures++
                lastError = "MediaPipe: " + e.javaClass.simpleName
                null
            }
            if (mp != null && mp.points.isNotEmpty()) return mp.copy(gaze = runCatching { Gaze.fromIris(mp.points) }.getOrNull())
        }
        val d = detectFace(bmp)
        if (d.points.isEmpty()) return d
        // La mirada es un dato adicional: un error al estimarla no debe anular la detección.
        return d.copy(gaze = runCatching { Gaze.fromImage(d.points, bmp.rgb(), bmp.width, bmp.height) }.getOrNull())
    }

    /** true si se pidió MediaPipe pero no se pudo iniciar o se desactivó por fallos. */
    val mediaPipeUnavailable: Boolean get() = engine == FaceEngine.MEDIAPIPE && (mediaPipe == null || mediaPipeFailures >= 5)

    /** Texto de diagnóstico para mostrar cuando no hay puntos (null si no hay nada que reportar). */
    val diagnostic: String? get() = lastError ?: initError ?: if (mediaPipeUnavailable) "MediaPipe desactivado por errores; se usa ML Kit" else null

    private fun detectFace(bmp: Bitmap): Detection {
        val faces = Tasks.await(face.process(InputImage.fromBitmap(bmp, 0)))
        val f = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return Detection.EMPTY
        val w = bmp.width.toFloat()
        val out = mutableMapOf<String, Pt>()
        mapOf(
            FaceLandmark.LEFT_EYE to Landmarks.EYE_L, FaceLandmark.RIGHT_EYE to Landmarks.EYE_R,
            FaceLandmark.LEFT_EAR to Landmarks.EAR_L, FaceLandmark.RIGHT_EAR to Landmarks.EAR_R,
            FaceLandmark.NOSE_BASE to Landmarks.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT to Landmarks.MOUTH_L, FaceLandmark.MOUTH_RIGHT to Landmarks.MOUTH_R,
        ).forEach { (type, key) -> f.getLandmark(type)?.position?.let { out[key] = Pt(it.x / w, it.y / w) } }
        mapOf(
            FaceContour.FACE to "oval_",
            FaceContour.LEFT_EYEBROW_TOP to "brow_l_top_", FaceContour.LEFT_EYEBROW_BOTTOM to "brow_l_bottom_",
            FaceContour.RIGHT_EYEBROW_TOP to "brow_r_top_", FaceContour.RIGHT_EYEBROW_BOTTOM to "brow_r_bottom_",
            FaceContour.LEFT_EYE to "eye_l_", FaceContour.RIGHT_EYE to "eye_r_",
            FaceContour.UPPER_LIP_TOP to "lip_upper_top_", FaceContour.UPPER_LIP_BOTTOM to "lip_upper_bottom_",
            FaceContour.LOWER_LIP_TOP to "lip_lower_top_", FaceContour.LOWER_LIP_BOTTOM to "lip_lower_bottom_",
            FaceContour.NOSE_BRIDGE to "nose_bridge_", FaceContour.NOSE_BOTTOM to "nose_bottom_",
        ).forEach { (type, prefix) ->
            f.getContour(type)?.points?.forEachIndexed { i, p -> out["$prefix$i"] = Pt(p.x / w, p.y / w) }
        }
        return Detection(out, f.headEulerAngleY, f.headEulerAngleX, f.headEulerAngleZ)
    }

    private fun detectPose(bmp: Bitmap): Map<String, Pt> {
        val p = Tasks.await(pose.process(InputImage.fromBitmap(bmp, 0)))
        val w = bmp.width.toFloat()
        val out = mutableMapOf<String, Pt>()
        mapOf(
            PoseLandmark.NOSE to Landmarks.NOSE,
            PoseLandmark.LEFT_EAR to Landmarks.EAR_L, PoseLandmark.RIGHT_EAR to Landmarks.EAR_R,
            PoseLandmark.LEFT_MOUTH to Landmarks.MOUTH_L, PoseLandmark.RIGHT_MOUTH to Landmarks.MOUTH_R,
            PoseLandmark.LEFT_SHOULDER to Landmarks.SHOULDER_L, PoseLandmark.RIGHT_SHOULDER to Landmarks.SHOULDER_R,
            PoseLandmark.LEFT_ELBOW to Landmarks.ELBOW_L, PoseLandmark.RIGHT_ELBOW to Landmarks.ELBOW_R,
            PoseLandmark.LEFT_WRIST to Landmarks.WRIST_L, PoseLandmark.RIGHT_WRIST to Landmarks.WRIST_R,
            PoseLandmark.LEFT_INDEX to Landmarks.INDEX_L, PoseLandmark.RIGHT_INDEX to Landmarks.INDEX_R,
            PoseLandmark.LEFT_PINKY to Landmarks.PINKY_L, PoseLandmark.RIGHT_PINKY to Landmarks.PINKY_R,
            PoseLandmark.LEFT_THUMB to Landmarks.THUMB_L, PoseLandmark.RIGHT_THUMB to Landmarks.THUMB_R,
            PoseLandmark.LEFT_HIP to Landmarks.HIP_L, PoseLandmark.RIGHT_HIP to Landmarks.HIP_R,
            PoseLandmark.LEFT_KNEE to Landmarks.KNEE_L, PoseLandmark.RIGHT_KNEE to Landmarks.KNEE_R,
            PoseLandmark.LEFT_ANKLE to Landmarks.ANKLE_L, PoseLandmark.RIGHT_ANKLE to Landmarks.ANKLE_R,
            PoseLandmark.LEFT_HEEL to Landmarks.HEEL_L, PoseLandmark.RIGHT_HEEL to Landmarks.HEEL_R,
            PoseLandmark.LEFT_FOOT_INDEX to Landmarks.FOOT_INDEX_L, PoseLandmark.RIGHT_FOOT_INDEX to Landmarks.FOOT_INDEX_R,
        ).forEach { (type, key) ->
            p.getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= 0.8f }?.position?.let { out[key] = Pt(it.x / w, it.y / w) }
        }
        return out
    }

    override fun close() {
        face.close(); pose.close(); mediaPipe?.close()
    }
}
