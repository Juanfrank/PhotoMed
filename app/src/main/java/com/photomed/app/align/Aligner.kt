package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photomed.app.data.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
    /** Mirada de cada foto (ver [Gaze]) si se evaluó; null si no hay ojos o está desactivada. */
    val gazeBefore: Pt? = null,
    val gazeAfter: Pt? = null,
) {
    /** null = no evaluada; true/false = miran (o no) en la misma dirección. */
    val gazeMatches: Boolean? get() = if (gazeBefore != null && gazeAfter != null) Gaze.matches(gazeBefore, gazeAfter) else null
    val gazeHint: String? get() = if (gazeBefore != null && gazeAfter != null) Gaze.describe(gazeAfter - gazeBefore) else null

    val landmarkRegions: List<String> get() = landmarks.map(Landmarks::regionOf).distinct()
}

/**
 * Condiciones que usa la alineación automática (todas activas por defecto; se desactivan en el menú).
 * [beforeOutline]/[afterOutline]: contornos elegidos en "Medir contornos" (varita o automáticos), que
 * sustituyen a los detectados para estimar la perspectiva por proporciones.
 */
data class AlignOptions(
    val points: Boolean = true,
    val contours: Boolean = true,
    val gaze: Boolean = true,
    val beforeOutline: List<Pt>? = null,
    val afterOutline: List<Pt>? = null,
) {
    companion object {
        const val PREF_POINTS = "align_points"
        const val PREF_CONTOURS = "align_contours"
        const val PREF_GAZE = "align_gaze"
        fun fromPrefs(p: android.content.SharedPreferences) = AlignOptions(
            p.getBoolean(PREF_POINTS, true), p.getBoolean(PREF_CONTOURS, true), p.getBoolean(PREF_GAZE, true))
    }
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

    suspend fun auto(zone: Zone, before: Uri, after: Uri, manualKeystone: Float? = null,
                     options: AlignOptions = AlignOptions()): AlignmentResult =
        withContext(Dispatchers.Default) {
            val spec = ZoneAlignment.of(zone)
            val b = ImageOps.load34(context, before, DETECT_W)
            val a0 = ImageOps.load34(context, after, DETECT_W)
            val needDetect = options.points || (options.gaze && spec.detector == Detector.FACE)
            val (bDet, aDet0) = if (needDetect) LandmarkDetector(stream = false, context, FaceEngine.get(context)).use {
                it.detectFull(spec.detector, b) to it.detectFull(spec.detector, a0)
            } else Detection.EMPTY to Detection.EMPTY
            ensureActive()
            val bPts = if (options.points) bDet.points else emptyMap()
            val aPts0 = if (options.points) aDet0.points else emptyMap()
            val (phi, src) = manualKeystone?.let { it to "manual" } ?: estimateKeystone(spec, b, a0, bPts, aPts0, options)
            val a = ImageOps.correctPerspective(a0, phi, Perspective.DEFAULT_FOCAL)
            val aPts = aPts0.mapValues { Perspective.mapPt(it.value, phi) }
            val lm = if (options.points) landmarkTransform(spec.landmarks, bPts, aPts) else null
            ensureActive()
            val gb = bDet.gaze.takeIf { options.gaze }
            val ga = aDet0.gaze.takeIf { options.gaze }
            val method = when {
                lm != null && options.contours -> "Puntos anatómicos + contornos"
                lm != null -> "Puntos anatómicos"
                options.contours -> "Contornos anatómicos"
                else -> "Sin alineación geométrica"
            }
            val window = when {
                !options.contours -> EdgeAligner.Window(0f, 0f, 0f)   // solo puntúa, no mueve
                lm != null -> EdgeAligner.Window.NARROW
                else -> EdgeAligner.Window.WIDE
            }
            val r = EdgeAligner(spec.contours).align(ImageOps.gray(b, REFINE_W), ImageOps.gray(a, REFINE_W),
                lm?.first ?: if (options.contours) null else Similarity.IDENTITY, window)
            AlignmentResult(r.transform, method, lm?.second.orEmpty(), r.score, r.regionScores, phi, src, gb, ga)
        }

    /** Medición de contornos para una foto (óvalo facial si hay rostro, si no la silueta del sujeto). */
    suspend fun autoMask(zone: Zone, uri: Uri, width: Int = MEASURE_W): Pair<Bitmap, Mask?> = withContext(Dispatchers.Default) {
        val bmp = ImageOps.load34(context, uri, width)
        bmp to autoMask(ZoneAlignment.of(zone), bmp)
    }

    /** Región de piel para igualar color: óvalo facial reducido (sin pelo ni fondo) o centro de la silueta. */
    suspend fun skinMask(zone: Zone, bmp: Bitmap): BooleanArray? = withContext(Dispatchers.Default) {
        val m = autoMask(ZoneAlignment.of(zone), bmp) ?: return@withContext null
        MaskOps.erode(m, (bmp.width * 0.06f).toInt().coerceAtLeast(2)).data.takeIf { d -> d.count { it } > 50 }
    }

    private fun autoMask(spec: ZoneAlignment, bmp: Bitmap): Mask? {
        if (spec.detector == Detector.FACE) {
            val pts = LandmarkDetector(stream = false, context, FaceEngine.get(context)).use { it.detect(Detector.FACE, bmp) }
            val oval = (0 until 36).mapNotNull { pts["oval_$it"] }
            if (oval.size == 36) return MaskOps.polygon(oval, bmp.width, bmp.height)
        }
        return SubjectSegmenter().use { it.segment(bmp) }
    }

    private fun estimateKeystone(spec: ZoneAlignment, b: Bitmap, a: Bitmap, bPts: Map<String, Pt>, aPts: Map<String, Pt>,
                                 options: AlignOptions): Pair<Float, String> {
        if (options.contours && options.beforeOutline != null && options.afterOutline != null) {
            KeystoneFit.fromOutlines(options.beforeOutline, options.afterOutline)?.let { return it to "contornos seleccionados" }
        }
        val keys = spec.landmarks.filter { it in bPts && it in aPts }
        KeystoneFit.fromLandmarks(keys.map { bPts[it]!! }, keys.map { aPts[it]!! })?.let {
            return it.keystoneDeg to "proporciones de ${keys.size} puntos de contorno"
        }
        if (!options.contours) return 0f to ""
        val mb = autoMask(spec, Bitmap.createScaledBitmap(b, MEASURE_W, MEASURE_W * 4 / 3, true))?.let(ContourMetrics::of)
        val ma = autoMask(spec, Bitmap.createScaledBitmap(a, MEASURE_W, MEASURE_W * 4 / 3, true))?.let(ContourMetrics::of)
        if (mb != null && ma != null) {
            KeystoneFit.fromOutlines(mb.outline, ma.outline)?.let { return it to "proporciones de la silueta" }
        }
        return 0f to ""
    }

    companion object {
        const val DETECT_W = 720
        const val REFINE_W = 192
        const val MEASURE_W = 360
    }
}
