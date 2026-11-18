package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.photomed.app.data.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Resultado de la alineación de "después" sobre "antes". */
data class AlignmentResult(
    /** Semejanza después → antes en coordenadas normalizadas (unidad = ancho). */
    val transform: Similarity,
    val method: String,
    val landmarks: List<String>,
    /** Correlación de bordes tras alinear (−1..1). */
    val score: Float,
)

/**
 * Alineación por zona:
 * 1. Puntos anatómicos automáticos (ML Kit en el dispositivo: rostro o pose corporal), según la zona.
 * 2. Estimación de semejanza por mínimos cuadrados (Umeyama) con rechazo de atípicos.
 * 3. Afinado por coincidencia de contornos (bordes Sobel + NCC, multiescala).
 * Sin puntos anatómicos, la coincidencia de contornos busca en una ventana amplia.
 */
class Aligner(private val context: Context) {

    suspend fun auto(zone: Zone, before: Uri, after: Uri): AlignmentResult {
        val spec = ZoneAlignment.of(zone)
        val bBmp = load(before, DETECT_W)
        val aBmp = load(after, DETECT_W)
        val bPts = detect(spec.detector, bBmp)
        val aPts = detect(spec.detector, aBmp)
        val lm = landmarkTransform(spec.landmarks, bPts, aPts)
        return refine(spec, bBmp, aBmp, lm?.first, lm?.second.orEmpty(),
            if (lm != null) "Puntos anatómicos (${spec.description}) + contornos" else "Coincidencia de contornos")
    }

    /** Alineación con los dos puntos marcados por el usuario en cada foto (y afinado opcional). */
    suspend fun manual(zone: Zone, before: Uri, after: Uri, beforePts: List<Pt>, afterPts: List<Pt>, refine: Boolean): AlignmentResult {
        val spec = ZoneAlignment.of(zone)
        val t = Similarity.estimate(afterPts, beforePts) ?: Similarity.IDENTITY
        if (!refine) return AlignmentResult(t, "Puntos manuales", spec.manual, Float.NaN)
        return refine(spec, load(before, DETECT_W), load(after, DETECT_W), t, spec.manual, "Puntos manuales + contornos")
    }

    private suspend fun refine(
        spec: ZoneAlignment, b: Bitmap, a: Bitmap, init: Similarity?, keys: List<String>, method: String,
    ): AlignmentResult = withContext(Dispatchers.Default) {
        val aligner = EdgeAligner(spec.roiCenter, spec.roiRadius)
        val r = aligner.align(gray(b), gray(a), init, if (init != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.WIDE)
        AlignmentResult(r.transform, method, keys, r.score)
    }

    private suspend fun load(uri: Uri, width: Int): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(width, width * 4 / 3)
            .scale(Scale.FILL)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val bmp = (result as? SuccessResult)?.drawable?.let { (it as android.graphics.drawable.BitmapDrawable).bitmap }
            ?: error("No se pudo abrir la imagen")
        return centerCrop34(bmp, width)
    }

    private fun centerCrop34(src: Bitmap, width: Int): Bitmap {
        val targetH = width * 4 / 3
        val scale = maxOf(width.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaled = Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(width),
            (src.height * scale).toInt().coerceAtLeast(targetH), true)
        return Bitmap.createBitmap(scaled, (scaled.width - width) / 2, (scaled.height - targetH) / 2, width, targetH)
    }

    private fun gray(bmp: Bitmap): GrayImage {
        val small = Bitmap.createScaledBitmap(bmp, REFINE_W, REFINE_W * 4 / 3, true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val g = GrayImage(small.width, small.height)
        for (i in px.indices) {
            val c = px[i]
            g.data[i] = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        return g.standardized()
    }

    private suspend fun detect(detector: Detector, bmp: Bitmap): Map<String, Pt> = try {
        when (detector) {
            Detector.FACE -> detectFace(bmp)
            Detector.POSE -> detectPose(bmp)
            Detector.NONE -> emptyMap()
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private suspend fun detectFace(bmp: Bitmap): Map<String, Pt> {
        val client = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.2f)
                .build()
        )
        val faces = try { client.process(InputImage.fromBitmap(bmp, 0)).await() } finally { client.close() }
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return emptyMap()
        val w = bmp.width.toFloat()
        val out = mutableMapOf<String, Pt>()
        mapOf(
            FaceLandmark.LEFT_EYE to Landmarks.EYE_L, FaceLandmark.RIGHT_EYE to Landmarks.EYE_R,
            FaceLandmark.LEFT_EAR to Landmarks.EAR_L, FaceLandmark.RIGHT_EAR to Landmarks.EAR_R,
            FaceLandmark.NOSE_BASE to Landmarks.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT to Landmarks.MOUTH_L, FaceLandmark.MOUTH_RIGHT to Landmarks.MOUTH_R,
        ).forEach { (type, key) -> face.getLandmark(type)?.position?.let { out[key] = Pt(it.x / w, it.y / w) } }
        face.getContour(FaceContour.FACE)?.points?.forEachIndexed { i, p -> out["face_$i"] = Pt(p.x / w, p.y / w) }
        return out
    }

    private suspend fun detectPose(bmp: Bitmap): Map<String, Pt> {
        val client = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build()
        )
        val pose = try { client.process(InputImage.fromBitmap(bmp, 0)).await() } finally { client.close() }
        val w = bmp.width.toFloat()
        val out = mutableMapOf<String, Pt>()
        mapOf(
            PoseLandmark.LEFT_SHOULDER to Landmarks.SHOULDER_L, PoseLandmark.RIGHT_SHOULDER to Landmarks.SHOULDER_R,
            PoseLandmark.LEFT_ELBOW to Landmarks.ELBOW_L, PoseLandmark.RIGHT_ELBOW to Landmarks.ELBOW_R,
            PoseLandmark.LEFT_WRIST to Landmarks.WRIST_L, PoseLandmark.RIGHT_WRIST to Landmarks.WRIST_R,
            PoseLandmark.LEFT_HIP to Landmarks.HIP_L, PoseLandmark.RIGHT_HIP to Landmarks.HIP_R,
            PoseLandmark.LEFT_KNEE to Landmarks.KNEE_L, PoseLandmark.RIGHT_KNEE to Landmarks.KNEE_R,
            PoseLandmark.LEFT_ANKLE to Landmarks.ANKLE_L, PoseLandmark.RIGHT_ANKLE to Landmarks.ANKLE_R,
            PoseLandmark.LEFT_MOUTH to Landmarks.MOUTH_L, PoseLandmark.RIGHT_MOUTH to Landmarks.MOUTH_R,
            PoseLandmark.LEFT_INDEX to Landmarks.INDEX_L, PoseLandmark.RIGHT_INDEX to Landmarks.INDEX_R,
            PoseLandmark.LEFT_PINKY to Landmarks.PINKY_L, PoseLandmark.RIGHT_PINKY to Landmarks.PINKY_R,
            PoseLandmark.LEFT_THUMB to Landmarks.THUMB_L, PoseLandmark.RIGHT_THUMB to Landmarks.THUMB_R,
            PoseLandmark.LEFT_HEEL to Landmarks.HEEL_L, PoseLandmark.RIGHT_HEEL to Landmarks.HEEL_R,
            PoseLandmark.LEFT_FOOT_INDEX to Landmarks.FOOT_INDEX_L, PoseLandmark.RIGHT_FOOT_INDEX to Landmarks.FOOT_INDEX_R,
        ).forEach { (type, key) ->
            pose.getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= 0.8f }?.position?.let { out[key] = Pt(it.x / w, it.y / w) }
        }
        return out
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.cancel(it) }
    }

    companion object {
        private const val DETECT_W = 720
        private const val REFINE_W = 192
    }
}
