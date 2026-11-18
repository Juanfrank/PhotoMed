package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photomed.app.data.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de la alineación de "después" sobre "antes". */
data class AlignmentResult(
    /** Semejanza después (ya corregida en perspectiva) → antes, en coordenadas normalizadas. */
    val transform: Similarity,
    val method: String,
    /** Puntos anatómicos usados. */
    val landmarks: List<String>,
    /** Correlación global de contornos tras alinear (−1..1). */
    val score: Float,
    /** Coincidencia por región anatómica de contorno (mandíbula, frente, orejas…). */
    val regionScores: List<Pair<String, Float>> = emptyList(),
    /** Corrección de perspectiva vertical aplicada a "después" (°), estimada por proporciones. */
    val keystoneDeg: Float = 0f,
    /** Origen de la estimación de perspectiva. */
    val keystoneSource: String = "",
) {
    val landmarkRegions: List<String> get() = landmarks.map(Landmarks::regionOf).distinct()
}

/**
 * Alineación por zona, sin sensores:
 * 1. Puntos anatómicos (ML Kit en el dispositivo; hasta 140 en el rostro, 33 en el cuerpo).
 * 2. Perspectiva vertical por **proporciones de los contornos**: ajuste conjunto keystone +
 *    semejanza sobre los puntos de contorno; si no hay puntos suficientes, pendiente del cociente de
 *    anchos de la silueta (óvalo facial o segmentación del sujeto) a lo largo de su altura.
 * 3. Semejanza por mínimos cuadrados (Umeyama) con rechazo iterativo de atípicos.
 * 4. Afinado por coincidencia de contornos anatómicos ponderada por regiones.
 */
class Aligner(private val context: Context) {

    suspend fun auto(zone: Zone, before: Uri, after: Uri, manualKeystone: Float? = null): AlignmentResult =
        withContext(Dispatchers.Default) {
            val spec = ZoneAlignment.of(zone)
            val b = ImageOps.load34(context, before, DETECT_W)
            val a0 = ImageOps.load34(context, after, DETECT_W)
            val (bPts, aPts0) = LandmarkDetector(stream = false).use { it.detect(spec.detector, b) to it.detect(spec.detector, a0) }
            val (phi, src) = manualKeystone?.let { it to "manual" } ?: estimateKeystone(spec, b, a0, bPts, aPts0)
            val a = ImageOps.correctPerspective(a0, phi, Perspective.DEFAULT_FOCAL)
            val aPts = aPts0.mapValues { Perspective.mapPt(it.value, phi) }
            val lm = landmarkTransform(spec.landmarks, bPts, aPts)
            refine(spec, b, a, lm?.first, lm?.second.orEmpty(),
                if (lm != null) "Puntos anatómicos + contornos" else "Contornos anatómicos", phi, src)
        }

    /** Alineación con dos puntos marcados por el usuario en cada foto (y perspectiva por proporciones). */
    suspend fun manual(zone: Zone, before: Uri, after: Uri, beforePts: List<Pt>, afterPts: List<Pt>,
                       manualKeystone: Float? = null): AlignmentResult = withContext(Dispatchers.Default) {
        val spec = ZoneAlignment.of(zone)
        val b = ImageOps.load34(context, before, DETECT_W)
        val a0 = ImageOps.load34(context, after, DETECT_W)
        val (phi, src) = manualKeystone?.let { it to "manual" } ?: run {
            val (bp, ap) = LandmarkDetector(stream = false).use { it.detect(spec.detector, b) to it.detect(spec.detector, a0) }
            estimateKeystone(spec, b, a0, bp, ap)
        }
        val a = ImageOps.correctPerspective(a0, phi, Perspective.DEFAULT_FOCAL)
        val t = Similarity.estimate(afterPts.map { Perspective.mapPt(it, phi) }, beforePts) ?: Similarity.IDENTITY
        refine(spec, b, a, t, emptyList(), "Puntos manuales + contornos", phi, src)
    }

    /** Medición de contornos para una foto (óvalo facial si hay rostro, si no la silueta del sujeto). */
    suspend fun autoMask(zone: Zone, uri: Uri, width: Int = MEASURE_W): Pair<Bitmap, Mask?> = withContext(Dispatchers.Default) {
        val bmp = ImageOps.load34(context, uri, width)
        bmp to autoMask(ZoneAlignment.of(zone), bmp)
    }

    private fun autoMask(spec: ZoneAlignment, bmp: Bitmap): Mask? {
        if (spec.detector == Detector.FACE) {
            val pts = LandmarkDetector(stream = false).use { it.detect(Detector.FACE, bmp) }
            val oval = (0 until 36).mapNotNull { pts["oval_$it"] }
            if (oval.size == 36) return MaskOps.polygon(oval, bmp.width, bmp.height)
        }
        return SubjectSegmenter().use { it.segment(bmp) }
    }

    private fun estimateKeystone(spec: ZoneAlignment, b: Bitmap, a: Bitmap, bPts: Map<String, Pt>, aPts: Map<String, Pt>): Pair<Float, String> {
        val keys = spec.landmarks.filter { it in bPts && it in aPts }
        KeystoneFit.fromLandmarks(keys.map { bPts[it]!! }, keys.map { aPts[it]!! })?.let {
            return it.keystoneDeg to "proporciones de ${keys.size} puntos de contorno"
        }
        val mb = autoMask(spec, Bitmap.createScaledBitmap(b, MEASURE_W, MEASURE_W * 4 / 3, true))?.let(ContourMetrics::of)
        val ma = autoMask(spec, Bitmap.createScaledBitmap(a, MEASURE_W, MEASURE_W * 4 / 3, true))?.let(ContourMetrics::of)
        if (mb != null && ma != null) {
            KeystoneFit.fromOutlines(mb.outline, ma.outline)?.let { return it to "proporciones de la silueta" }
        }
        return 0f to ""
    }

    private fun refine(spec: ZoneAlignment, b: Bitmap, a: Bitmap, init: Similarity?, keys: List<String>, method: String,
                       phi: Float, src: String): AlignmentResult {
        val r = EdgeAligner(spec.contours).align(ImageOps.gray(b, REFINE_W), ImageOps.gray(a, REFINE_W), init,
            if (init != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.WIDE)
        return AlignmentResult(r.transform, method, keys, r.score, r.regionScores, phi, src)
    }

    companion object {
        const val DETECT_W = 720
        const val REFINE_W = 192
        const val MEASURE_W = 360
    }
}
