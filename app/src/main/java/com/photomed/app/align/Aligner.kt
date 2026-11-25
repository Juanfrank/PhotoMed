package com.photomed.app.align

import com.photomed.app.i18n.trn
import com.photomed.app.i18n.tr
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
    /** Método en español (se compara por su texto): mostrarlo con `tr(method)`. */
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
    /**
     * Validación por rasgo tras alinear: coincidencia (0..1) de ojos, cejas, nariz, labios y contorno
     * (ver [FeatureMatch]); vacía si no hay puntos del rostro en ambas fotos.
     */
    val featureScores: List<Pair<String, Float>> = emptyList(),
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
    /**
     * Surcos y arrugas ya analizados de cada foto (encuadre 3:4, sin perspectiva): los del mismo nombre en
     * las dos se suman como puntos de correspondencia (más precisión en la zona media e inferior).
     */
    val beforeLines: List<AnnotLine>? = null,
    val afterLines: List<AnnotLine>? = null,
    /**
     * Giro (pasos de 90° horarios) del contenido de la foto de después respecto a la de antes: se tomó con la
     * referencia girada ([RefOrientation]). Se deshace antes de alinear (la alineación solo busca giros
     * pequeños) y se incluye en la semejanza final.
     */
    val afterRot: Int = 0,
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
                     options: AlignOptions = AlignOptions()): AlignmentResult = NativeGate.run("alineación automática (${zone.id})") {
        withContext(Dispatchers.Default) {
            val spec = ZoneAlignment.of(zone)
            val b = ImageOps.load34(context, before, DETECT_W)
            val a00 = ImageOps.load34(context, after, DETECT_W)
            // foto tomada con la referencia girada: se lleva a la orientación de la de antes
            val undo = ((4 - options.afterRot % 4) % 4)
            // (encajada entera: la referencia girada se mostró recortada ×4/3 y así se recupera la escala)
            val a0 = if (undo == 0) a00 else ImageOps.fit34(ImageEdit(rot = undo).apply(a00), a00.width)
            val needDetect = options.points || (options.gaze && spec.detector == Detector.FACE)
            // rostro: MediaPipe (malla completa: ojos, cejas, nariz y labios finos); ML Kit si no está disponible
            val engine = if (spec.detector == Detector.FACE) FaceEngine.MEDIAPIPE else FaceEngine.get(context)
            val (bDet, aDet0) = if (needDetect) LandmarkDetector(stream = false, context, engine).use {
                it.detectFull(spec.detector, b) to it.detectFull(spec.detector, a0)
            } else Detection.EMPTY to Detection.EMPTY
            ensureActive()
            // Puntos tapados (flequillo, gafas oscuras, mano…) en cualquiera de las dos fotos: fuera
            val (bD0, aD0) = if (spec.detector == Detector.FACE && options.points) withoutOccluded(bDet, aDet0, b, a0) else bDet to aDet0
            // surcos analizados en las dos fotos: puntos de correspondencia adicionales
            val (bD, aD) = foldPairs(options.beforeLines, options.afterLines).let { (fb, fa) ->
                if (fb.isEmpty()) bD0 to aD0 else bD0.copy(points = bD0.points + fb) to aD0.copy(points = aD0.points + fa)
            }
            val r = SubjectSegmenter().use { seg ->
                alignCore(spec, b, a0, bD, aD, if (undo == 0) manualKeystone else 0f, options, { bmp -> autoMask(spec, bmp) },
                    bodyOf = { bmp -> BodyRegion.of(zone, bmp) { seg.segment(it) } })
            }
            // semejanza de la foto de después tal cual (sin girar) → antes
            if (undo == 0) r else r.copy(transform = r.transform.compose(RefOrientation.fitPrior(undo)), keystoneDeg = 0f)
        }
    }

    /**
     * Quita de ambas detecciones los rasgos interiores tapados en alguna de las dos fotos (según la
     * segmentación pelo / piel). Si quedarían muy pocos puntos (p. ej. gafas de sol en las dos), no
     * se quita ninguno: la oclusión es la misma y los puntos siguen siendo comparables.
     */
    private fun withoutOccluded(b: Detection, a: Detection, bb: Bitmap, ab: Bitmap): Pair<Detection, Detection> {
        val occ = HairSkinSegmenter.sharedOrNull(context)?.let { s ->
            val lb = s.labels(bb); val la = s.labels(ab)
            if (lb == null || la == null) null else HairSkin.occluded(lb, b.points) + HairSkin.occluded(la, a.points)
        } ?: return b to a
        val common = b.points.keys.intersect(a.points.keys)
        if (occ.isEmpty() || (common - occ).size < common.size * 0.6f) return b to a
        return b.copy(points = b.points - occ) to a.copy(points = a.points - occ)
    }

    /** Medición de contornos para una foto (óvalo facial si hay rostro, si no la silueta del sujeto). */
    suspend fun autoMask(zone: Zone, uri: Uri, width: Int = MEASURE_W): Pair<Bitmap, Mask?> = NativeGate.run("contorno de una foto") {
        withContext(Dispatchers.Default) {
            val bmp = ImageOps.load34(context, uri, width)
            bmp to autoMask(ZoneAlignment.of(zone), bmp)
        }
    }

    /** Región de piel para igualar color: óvalo facial reducido (sin pelo ni fondo) o centro de la silueta. */
    suspend fun skinMask(zone: Zone, bmp: Bitmap): BooleanArray? = NativeGate.run("piel para igualar color") { skinMaskGated(zone, bmp) }

    private suspend fun skinMaskGated(zone: Zone, bmp: Bitmap): BooleanArray? = withContext(Dispatchers.Default) {
        val spec = ZoneAlignment.of(zone)
        if (spec.detector == Detector.FACE) {
            val pts = LandmarkDetector(stream = false, context, FaceEngine.get(context)).use { it.detect(Detector.FACE, bmp) }
            faceSkin(pts, bmp.width, bmp.height)?.let { poly ->
                // solo piel del rostro según la segmentación (sin mechones, cejas pobladas ni gafas)
                val skin = HairSkinSegmenter.sharedOrNull(context)?.let { s -> s.labels(bmp) }?.let { HairSkin.faceSkin(it, bmp.width, bmp.height) }
                val both = if (skin == null) null else BooleanArray(poly.size) { poly[it] && skin[it] }
                return@withContext both?.takeIf { b -> b.count { it } > 200 } ?: poly
            }
        }
        val m = when (zone.guide) {
            com.photomed.app.data.GuideType.HANDS, com.photomed.app.data.GuideType.FEET -> ForegroundOnPlain.mask(bmp.rgb(), bmp.width, bmp.height)
            else -> null
        } ?: autoMask(spec, bmp) ?: return@withContext null
        MaskOps.erode(m, (bmp.width * 0.06f).toInt().coerceAtLeast(2)).data.takeIf { d -> d.count { it } > 50 }
    }

    /**
     * Piel del rostro a partir de los puntos: óvalo reducido, sin ojos, cejas, labios ni orificios
     * nasales (dilatados), que no son piel y sesgarían el color medio.
     */
    fun faceSkin(points: Map<String, Pt>, w: Int, h: Int): BooleanArray? = faceSkinMask(points, w, h)

    private fun autoMask(spec: ZoneAlignment, bmp: Bitmap): Mask? {
        if (spec.detector == Detector.FACE) {
            val pts = LandmarkDetector(stream = false, context, FaceEngine.get(context)).use { it.detect(Detector.FACE, bmp) }
            val oval = (0 until 36).mapNotNull { pts["oval_$it"] }
            if (oval.size == 36) return MaskOps.polygon(oval, bmp.width, bmp.height)
        }
        return SubjectSegmenter().use { it.segment(bmp) }
    }

    companion object Core {
        const val DETECT_W = 720
        const val REFINE_W = 192
        const val MEASURE_W = 360
        /** El afinado por contornos puede subir el residuo de los puntos como mucho este factor (+ holgura). */
        const val REFINE_TOLERANCE = 1.15f
        const val REFINE_SLACK = 0.002f

    /**
     * Núcleo de la alineación (sin detectores ni cargador de imágenes, para poder probarlo con
     * puntos ya detectados): perspectiva → semejanza por puntos → afinado por contornos.
     * [maskOf]: silueta de una imagen (solo si no hay puntos para la perspectiva).
     */
    fun alignCore(spec: ZoneAlignment, b: Bitmap, a0: Bitmap, bDet: Detection, aDet0: Detection, manualKeystone: Float?,
                  options: AlignOptions, maskOf: (Bitmap) -> Mask? = { null },
                  /** Región del cuerpo (ver [BodyRegion]): el afinado por contornos ignora los bordes del fondo. */
                  bodyOf: (Bitmap) -> Mask? = { null }): AlignmentResult {
        val bPts = if (options.points) bDet.points else emptyMap()
        val aPts0 = if (options.points) aDet0.points else emptyMap()
        val (phi, src) = manualKeystone?.let { it to tr("manual") } ?: estimateKeystone(spec, b, a0, bPts, aPts0, options, maskOf)
        val a = ImageOps.correctPerspective(a0, phi, Perspective.DEFAULT_FOCAL)
        val aPts = aPts0.mapValues { Perspective.mapPt(it.value, phi) }
        val lm = if (options.points) landmarkTransform(spec.landmarks + bPts.keys.filter { it.startsWith(FOLD_KEY) }, bPts, aPts) else null
        val gb = bDet.gaze.takeIf { options.gaze }
        val ga = aDet0.gaze.takeIf { options.gaze }
        val method = when {
            lm != null && options.contours -> trn("Puntos anatómicos + contornos")
            lm != null -> trn("Puntos anatómicos")
            options.contours -> trn("Contornos anatómicos")
            else -> trn("Sin alineación geométrica")
        }
        val window = when {
            !options.contours -> EdgeAligner.Window(0f, 0f, 0f)   // solo puntúa, no mueve
            lm != null -> EdgeAligner.Window.NARROW
            else -> EdgeAligner.Window.WIDE
        }
        // Solo bordes del cuerpo, en las dos fotos a la vez (si una no se aísla, ninguna)
        val bodyB = if (options.contours) bodyOf(b) else null
        val bodyA = if (bodyB != null) bodyOf(a) else null
        val both = bodyB != null && bodyA != null
        val gB = ImageOps.gray(b, REFINE_W, bodyB.takeIf { both }); val gA = ImageOps.gray(a, REFINE_W, bodyA.takeIf { both })
        val edges = EdgeAligner(spec.contours)
        var r = edges.align(gB, gA, lm?.first ?: if (options.contours) null else Similarity.IDENTITY, window)
        var m = method
        // Salvaguarda: los puntos anatómicos mandan. Si el afinado por contornos (pelo, ropa, fondo)
        // aleja los puntos de su sitio, se descarta y queda la alineación por puntos.
        if (lm != null && r.transform != lm.first) {
            val keys = lm.second
            val pa = keys.map { aPts[it]!! }; val pb = keys.map { bPts[it]!! }
            val base = Similarity.rms(lm.first, pa, pb)
            val refined = Similarity.rms(r.transform, pa, pb)
            if (refined > base * REFINE_TOLERANCE + REFINE_SLACK) {
                r = edges.align(gB, gA, lm.first, EdgeAligner.Window(0f, 0f, 0f))
                m = trn("Puntos anatómicos")
            }
        }
        // validación por rasgo con la transformación final (después → antes)
        val features = if (spec.detector == Detector.FACE && options.points) FeatureMatch.scores(bPts, aPts, r.transform) else emptyList()
        return AlignmentResult(r.transform, m, lm?.second.orEmpty(), r.score, r.regionScores, phi, src, featureScores = features, gazeBefore = gb, gazeAfter = ga)
    }

    private fun estimateKeystone(spec: ZoneAlignment, b: Bitmap, a: Bitmap, bPts: Map<String, Pt>, aPts: Map<String, Pt>,
                                 options: AlignOptions, maskOf: (Bitmap) -> Mask?): Pair<Float, String> {
        if (options.contours && options.beforeOutline != null && options.afterOutline != null) {
            KeystoneFit.fromOutlines(options.beforeOutline, options.afterOutline)?.let { return it to tr("contornos seleccionados") }
        }
        val keys = spec.landmarks.filter { it in bPts && it in aPts }
        KeystoneFit.fromLandmarks(keys.map { bPts[it]!! }, keys.map { aPts[it]!! })?.let {
            return it.keystoneDeg to tr("proporciones de %1\$d puntos de contorno", keys.size)
        }
        if (!options.contours) return 0f to ""
        val mb = maskOf(Bitmap.createScaledBitmap(b, MEASURE_W, MEASURE_W * 4 / 3, true))?.let(ContourMetrics::of)
        val ma = maskOf(Bitmap.createScaledBitmap(a, MEASURE_W, MEASURE_W * 4 / 3, true))?.let(ContourMetrics::of)
        if (mb != null && ma != null) {
            KeystoneFit.fromOutlines(mb.outline, ma.outline)?.let { return it to tr("proporciones de la silueta") }
        }
        return 0f to ""
    }

    }
}

/**
 * Piel del rostro a partir de los puntos: óvalo reducido, sin ojos, cejas, labios ni orificios
 * nasales (dilatados), que no son piel y sesgarían el color medio.
 */
fun faceSkinMask(points: Map<String, Pt>, w: Int, h: Int): BooleanArray? {
    val p = FaceKeys.canonical(points)
    val oval = (0 until 36).mapNotNull { p["oval_$it"] }
    if (oval.size < 30) return null
    val skin = MaskOps.erode(MaskOps.polygon(oval, w, h), (w * 0.04f).toInt().coerceAtLeast(2))
    fun group(g: String) = (0 until 20).mapNotNull { p["$g$it"] }
    val holes = listOf(
        group("eye_l_"), group("eye_r_"),
        group("brow_l_top_") + group("brow_l_bottom_").reversed(), group("brow_r_top_") + group("brow_r_bottom_").reversed(),
        group("lip_upper_top_") + group("lip_lower_bottom_"),
        group("nose_bottom_"),
    ).filter { it.size >= 3 }
    for (poly in holes) {
        val d = MaskOps.dilate(MaskOps.polygon(poly, w, h), (w * 0.025f).toInt().coerceAtLeast(2))
        for (i in skin.data.indices) if (d.data[i]) skin.data[i] = false
    }
    return skin.data.takeIf { d -> d.count { it } > 200 }
}

/** Prefijo de los puntos de surcos usados para alinear. */
const val FOLD_KEY = "fold:"

/**
 * Puntos de correspondencia de los surcos con el mismo nombre en las dos fotos: 5 puntos a 10–90 % de
 * su longitud (los añadidos a mano y los rasgos no cuentan). Mismas coordenadas que las líneas.
 */
fun foldPairs(before: List<AnnotLine>?, after: List<AnnotLine>?): Pair<Map<String, Pt>, Map<String, Pt>> {
    if (before == null || after == null) return emptyMap<String, Pt>() to emptyMap()
    fun usable(l: AnnotLine) = l.layer == AnnotLayer.FOLDS && !l.estimated && l.points.size >= 3 &&
        l.metrics?.name?.let { !it.startsWith("Surco añadido") } == true
    val a = after.filter(::usable).associateBy { it.metrics!!.name }
    val fb = HashMap<String, Pt>(); val fa = HashMap<String, Pt>()
    for (lb in before.filter(::usable)) {
        val la = a[lb.metrics!!.name] ?: continue
        val pb = FreehandTrace.resample(lb.points, 11); val pa = FreehandTrace.resample(la.points, 11)
        for (k in 1..9 step 2) { fb["$FOLD_KEY${lb.metrics.name}:$k"] = pb[k]; fa["$FOLD_KEY${lb.metrics.name}:$k"] = pa[k] }
    }
    return fb to fa
}
