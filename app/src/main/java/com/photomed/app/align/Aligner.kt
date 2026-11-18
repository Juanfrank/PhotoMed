package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photomed.app.data.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foto a alinear y su corrección de perspectiva vertical (grados de cámara; 0 = sin corrección). */
data class AlignInput(val uri: Uri, val pitchDeg: Float = 0f, val focalNorm: Float = Perspective.DEFAULT_FOCAL)

/** Resultado de la alineación de "después" sobre "antes". */
data class AlignmentResult(
    /** Semejanza después → antes en coordenadas normalizadas (unidad = ancho). */
    val transform: Similarity,
    val method: String,
    /** Puntos anatómicos usados. */
    val landmarks: List<String>,
    /** Correlación global de contornos tras alinear (−1..1). */
    val score: Float,
    /** Coincidencia por región anatómica de contorno (mandíbula, frente, orejas…). */
    val regionScores: List<Pair<String, Float>> = emptyList(),
) {
    /** Regiones anatómicas cubiertas por los puntos usados (cejas, ojos, mandíbula…). */
    val landmarkRegions: List<String> get() = landmarks.map(Landmarks::regionOf).distinct()
}

/**
 * Alineación por zona:
 * 1. Corrección de perspectiva vertical de cada foto (inclinación registrada al capturar).
 * 2. Puntos anatómicos automáticos (ML Kit en el dispositivo; hasta 140 en el rostro).
 * 3. Semejanza por mínimos cuadrados (Umeyama) con rechazo iterativo de atípicos.
 * 4. Afinado por coincidencia de contornos anatómicos (orientación de bordes multiescala,
 *    ponderada por las regiones de la zona). Sin puntos, búsqueda amplia solo por contornos.
 */
class Aligner(private val context: Context) {

    suspend fun auto(zone: Zone, before: AlignInput, after: AlignInput): AlignmentResult = withContext(Dispatchers.Default) {
        val spec = ZoneAlignment.of(zone)
        val b = prepare(before); val a = prepare(after)
        val (bPts, aPts) = LandmarkDetector(stream = false).use { it.detect(spec.detector, b) to it.detect(spec.detector, a) }
        val lm = landmarkTransform(spec.landmarks, bPts, aPts)
        refine(spec, b, a, lm?.first, lm?.second.orEmpty(),
            if (lm != null) "Puntos anatómicos + contornos" else "Contornos anatómicos")
    }

    /** Alineación con los dos puntos marcados por el usuario en cada foto, afinada por contornos. */
    suspend fun manual(zone: Zone, before: AlignInput, after: AlignInput, beforePts: List<Pt>, afterPts: List<Pt>): AlignmentResult =
        withContext(Dispatchers.Default) {
            val spec = ZoneAlignment.of(zone)
            val t = Similarity.estimate(afterPts, beforePts) ?: Similarity.IDENTITY
            refine(spec, prepare(before), prepare(after), t, emptyList(), "Puntos manuales + contornos")
        }

    private suspend fun prepare(input: AlignInput): Bitmap =
        ImageOps.correctPerspective(ImageOps.load34(context, input.uri, DETECT_W), input.pitchDeg, input.focalNorm)

    private fun refine(spec: ZoneAlignment, b: Bitmap, a: Bitmap, init: Similarity?, keys: List<String>, method: String): AlignmentResult {
        val r = EdgeAligner(spec.contours).align(ImageOps.gray(b, REFINE_W), ImageOps.gray(a, REFINE_W), init,
            if (init != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.WIDE)
        return AlignmentResult(r.transform, method, keys, r.score, r.regionScores)
    }

    companion object {
        const val DETECT_W = 720
        const val REFINE_W = 192
    }
}
