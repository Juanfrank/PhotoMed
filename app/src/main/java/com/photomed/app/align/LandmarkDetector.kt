package com.photomed.app.align

import com.photomed.app.i18n.tr
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
                     val gaze: Pt? = null,
                     /** Pose por ajuste geométrico al modelo 3D ([HeadPose]); null si no es un rostro. */
                     val pose: HeadPose.Fit? = null) {
    companion object { val EMPTY = Detection(emptyMap()) }
}

/**
 * Detector de puntos: rostro con MediaPipe y ML Kit de respaldo ([FaceEngine]) y cuerpo con
 * ML Kit Pose. Ambos motores devuelven las mismas claves de puntos.
 */
class LandmarkDetector(stream: Boolean, context: Context? = null, val engine: FaceEngine = FaceEngine.MEDIAPIPE) : Closeable {
    /** Motivo por el que MediaPipe no se pudo iniciar (si se pidió). */
    var initError: String? = null
        private set
    private val mediaPipe: MediaPipeFace? =
        if (engine == FaceEngine.MEDIAPIPE && context != null) runCatching { MediaPipeFace(context.applicationContext) }
            .onFailure { initError = tr("MediaPipe no se pudo iniciar (%1\$s); se usa ML Kit", it.javaClass.simpleName) }.getOrNull()
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
            // orden canónico de los contornos: el mismo para ML Kit y MediaPipe (ver FaceKeys)
            Detector.FACE -> detectFaceAny(bmp).let { it.copy(points = FaceKeys.canonical(it.points)) }
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
            if (mp != null && mp.points.isNotEmpty()) return withPoseAndGaze(mp)
        }
        val d = detectFace(bmp)
        if (d.points.isEmpty()) return d
        // Mirada y pose geométrica son datos adicionales: un error al estimarlos no anula la detección.
        val pose = runCatching { HeadPose.fit(FaceKeys.canonical(d.points)) }.getOrNull()
        // ML Kit (modo rápido): el cabeceo de Euler es ruidoso; se promedia con el geométrico.
        val pitch = when {
            pose == null -> d.pitch
            d.pitch == null -> pose.pitch
            else -> (d.pitch + pose.pitch) / 2f
        }
        return d.copy(gaze = runCatching { Gaze.fromImage(d.points, bmp.rgb(), bmp.width, bmp.height) }.getOrNull(),
            pose = pose, pitch = pitch)
    }

    /** true si se pidió MediaPipe pero no se pudo iniciar o se desactivó por fallos. */
    val mediaPipeUnavailable: Boolean get() = engine == FaceEngine.MEDIAPIPE && (mediaPipe == null || mediaPipeFailures >= 5)

    /** Texto de diagnóstico para mostrar cuando no hay puntos (null si no hay nada que reportar). */
    val diagnostic: String? get() = lastError ?: initError ?: if (mediaPipeUnavailable) tr("MediaPipe desactivado por errores; se usa ML Kit") else null

    private fun detectFace(bmp: Bitmap): Detection {
        val faces = Tasks.await(face.process(InputImage.fromBitmap(bmp, 0)), ML_KIT_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
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
        val p = Tasks.await(pose.process(InputImage.fromBitmap(bmp, 0)), ML_KIT_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
        val w = bmp.width.toFloat()
        // los tipos de PoseLandmark de ML Kit son los índices de BlazePose (los mismos de MediaPipe Pose)
        return poseKeys(POSE_KEYS.keys.mapNotNull { t -> p.getPoseLandmark(t)?.let { t to Triple(it.position.x / w, it.position.y / w, it.inFrameLikelihood) } }.toMap())
    }

    companion object {
        /** Índice de BlazePose (= tipo de PoseLandmark de ML Kit) → nombre del punto en la app. */
        val POSE_KEYS: Map<Int, String> = linkedMapOf(
            0 to Landmarks.NOSE, 7 to Landmarks.EAR_L, 8 to Landmarks.EAR_R, 9 to Landmarks.MOUTH_L, 10 to Landmarks.MOUTH_R,
            11 to Landmarks.SHOULDER_L, 12 to Landmarks.SHOULDER_R, 13 to Landmarks.ELBOW_L, 14 to Landmarks.ELBOW_R,
            15 to Landmarks.WRIST_L, 16 to Landmarks.WRIST_R, 17 to Landmarks.PINKY_L, 18 to Landmarks.PINKY_R,
            19 to Landmarks.INDEX_L, 20 to Landmarks.INDEX_R, 21 to Landmarks.THUMB_L, 22 to Landmarks.THUMB_R,
            23 to Landmarks.HIP_L, 24 to Landmarks.HIP_R, 25 to Landmarks.KNEE_L, 26 to Landmarks.KNEE_R,
            27 to Landmarks.ANKLE_L, 28 to Landmarks.ANKLE_R, 29 to Landmarks.HEEL_L, 30 to Landmarks.HEEL_R,
            31 to Landmarks.FOOT_INDEX_L, 32 to Landmarks.FOOT_INDEX_R,
        )

        /** Probabilidad mínima de que el punto esté dentro del cuadro para usarlo. */
        const val POSE_MIN_IN_FRAME = 0.8f

        /** Puntos del cuerpo: índice de BlazePose → (x, y en unidades del ancho, probabilidad en el cuadro). */
        fun poseKeys(byIndex: Map<Int, Triple<Float, Float, Float>>): Map<String, Pt> =
            POSE_KEYS.mapNotNull { (i, key) -> byIndex[i]?.takeIf { it.third >= POSE_MIN_IN_FRAME }?.let { key to Pt(it.first, it.second) } }.toMap()

        /** Rostro de MediaPipe listo para usar: contornos en orden canónico, pose geométrica y mirada. */
        fun withPoseAndGaze(mp: Detection): Detection {
            val pose = runCatching { HeadPose.fit(FaceKeys.canonical(mp.points)) }.getOrNull()
            return mp.copy(gaze = runCatching { Gaze.fromIris(mp.points) }.getOrNull(), pose = pose)
        }

        /** Como [detectFull] para el rostro con MediaPipe (pruebas): pose, mirada y contornos canónicos. */
        fun finishFace(mp: Detection): Detection = if (mp.points.isEmpty()) mp
            else withPoseAndGaze(mp).let { it.copy(points = FaceKeys.canonical(it.points)) }
    }

    override fun close() {
        face.close(); pose.close(); mediaPipe?.close()
    }
}

/**
 * Límite de espera de una llamada a ML Kit (s). Sin él, una tarea que no termina bloquea su hilo
 * para siempre y, con varias, se agotan los hilos de fondo y la app deja de cargar imágenes.
 */
const val ML_KIT_TIMEOUT_S = 10L
