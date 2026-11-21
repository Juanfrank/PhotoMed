package com.photomed.app.align

import android.graphics.Bitmap
import com.photomed.app.data.Zone
import java.io.Closeable

/** Resultado de analizar un cuadro: evaluación + puntos para dibujar (normalizados, unidad = ancho). */
data class LiveFrame(
    val eval: MatchEval,
    val referencePoints: List<Pt> = emptyList(),
    val currentPoints: List<Pt> = emptyList(),
    val method: String = "",
    val totalPoints: Int = 0,
    /** Todos los puntos detectados en el cuadro (p. ej. para estimar la distancia por la interpupilar). */
    val currentPointsAll: Map<String, Pt> = emptyMap(),
    /** Evaluación de estandarización (sin foto de referencia). */
    val standard: Boolean = false,
    val headYaw: Float? = null,
    /** Cabeceo (°, + = barbilla arriba): geométrico si hay pose, si no del motor. */
    val headPitch: Float? = null,
    val gaze: Pt? = null,
    /** Modo libre sin referencia: contorno de lo visible en el cuadro actual. */
    val freeContour: FreeContour? = null,
    /** Línea de implantación frontal del cuadro actual (rostro frontal y frente). */
    val hairline: List<Pt>? = null,
)

/**
 * Motor de coincidencia en vivo entre la foto de referencia (superpuesta) y la cámara.
 * Por cuadro:
 * - Puntos anatómicos (ML Kit continuo): % de puntos a ≤ 1,5 % del ancho de su posición de
 *   referencia, semejanza actual → referencia y perspectiva vertical por proporciones.
 * - Sin puntos: puntos de contorno de la referencia (bordes fuertes en las regiones anatómicas)
 *   y % de ellos que encuentran borde en el cuadro actual a esa distancia; semejanza por
 *   coincidencia de contornos.
 * Se llama desde el hilo de análisis (bloqueante).
 */
class LiveMatcher(private val zone: Zone, context: android.content.Context? = null, engine: FaceEngine = FaceEngine.MEDIAPIPE) : Closeable {
    private val spec = ZoneAlignment.of(zone)
    private val appContext = context?.applicationContext
    /** Segmentación pelo / piel (MediaPipe multiclase); null si no hay contexto o modelo. */
    private val hairSkinLazy = lazy { appContext?.let { HairSkinSegmenter.createOrNull(it) } }
    private val hairSkin: HairSkinSegmenter? get() = hairSkinLazy.value
    private val tracksHairline = HairSkin.appliesTo(zone)
    /** Línea de implantación frontal de la referencia (rostro frontal y frente). */
    @Volatile var referenceHairline: List<Pt>? = null
        private set
    private val detector = LandmarkDetector(stream = true, context, engine)
    val engine: FaceEngine = detector.engine
    /** MediaPipe no disponible (error al iniciar o fallos repetidos): los puntos vienen de ML Kit. */
    val detectorUnavailable: Boolean get() = detector.mediaPipeUnavailable
    private val edges = EdgeAligner(spec.contours)
    private var ref: Detection = Detection.EMPTY
    private var refGray: GrayImage? = null
    /** Gris de la referencia con todo el fondo (si el cuadro actual no permite aislar el cuerpo). */
    private var refGrayAll: GrayImage? = null
    private var refBody: Mask? = null
    private var frameBody: Mask? = null
    private var frameBodyAt = 0L
    private var refEdgePts: List<ContourMatch.EdgePt> = emptyList()
    /** Perfil: puntos de la silueta (vértice, punta nasal, pogonion, occipucio) cuando los detectores no ven el rostro. */
    private val isProfile = zone.guide == com.photomed.app.data.GuideType.FACE_PROFILE
    private val frontRight = !zone.mirrored
    private var refProfile: Map<String, Pt>? = null
    private var frameSil: Map<String, Pt>? = null
    private var frameSilAt = 0L

    /** Puntos del perfil del cuadro por silueta (segmentación como mucho cada 0,3 s). */
    private fun frameProfile(frame: Bitmap): Map<String, Pt>? {
        val now = System.currentTimeMillis()
        if (now - frameSilAt > 300) {
            frameSilAt = now
            frameSil = runCatching { segmenter?.segment(frame)?.let { Silhouette.fromMask(it) }?.profilePoints(frontRight) }.getOrNull()
        }
        return frameSil
    }

    /** Puntos de la referencia (para adaptar la guía a la foto). */
    val referencePoints: Map<String, Pt> get() = ref.points
    private var last: Similarity? = null
    private var matched = false

    /** Umbral de puntos coincidentes (0..1). */
    @Volatile var threshold: Float = LiveMatch.DEFAULT_THRESHOLD

    /** Silueta del sujeto en la referencia (segmentación), para adaptar la guía corporal y de perfil. */
    @Volatile var referenceSilhouette: Silhouette? = null
        private set
    /** Bordes de la referencia (fantasma de bordes: más legible que la foto translúcida). */
    @Volatile var referenceEdges: FreeContour? = null
        private set
    /** Modo libre: contorno de lo visible en la referencia. */
    @Volatile var referenceFree: FreeContour? = null
        private set
    private var lastFree: FreeContour? = null
    private var lastFreeAt = 0L
    private var segmenterOrNull: SubjectSegmenter? = null
    private val segmenter: SubjectSegmenter?
        get() = segmenterOrNull ?: runCatching { SubjectSegmenter() }.getOrNull()?.also { segmenterOrNull = it }

    /** Datos de una referencia para dibujar la guía adaptada (sin cambiar la referencia de la coincidencia). */
    class GuideRef(val points: Map<String, Pt>, val silhouette: Silhouette?, val hairline: List<Pt>?)

    /**
     * Guía adaptada a [bmp] (puntos con el óvalo ajustado, silueta y línea de implantación), para
     * dibujarla sobre la referencia sin girar cuando la referencia se muestra girada: la guía se
     * adapta en vertical y se gira entera (sin deformarla).
     */
    fun guideOf(bmp: Bitmap): GuideRef {
        var r = detector.detectFull(spec.detector, bmp)
        val faceView = zone.guide == com.photomed.app.data.GuideType.FACE_FRONT || zone.guide == com.photomed.app.data.GuideType.FACE_OBLIQUE
        val labels = if (faceView || tracksHairline) runCatching { hairSkin?.labels(bmp) }.getOrNull() else null
        if (labels != null && faceView && r.points.isNotEmpty()) r = r.copy(points = HairSkin.snapOval(labels, r.points))
        val hl = if (!tracksHairline || labels == null) null else runCatching { HairSkin.hairline(labels, r.points)?.points }.getOrNull()
        val sil = runCatching {
            val plain = zone.guide == com.photomed.app.data.GuideType.HANDS || zone.guide == com.photomed.app.data.GuideType.FEET
            val mask = if (plain) ForegroundOnPlain.mask(bmp.rgb(), bmp.width, bmp.height) else SubjectSegmenter().use { s -> s.segment(bmp) }
            mask?.let { Silhouette.fromMask(it, Silhouette.axisOf(r.points)) }
        }.getOrNull()
        return GuideRef(r.points, sil, hl)
    }

    fun setReference(bmp: Bitmap) {
        ref = detector.detectFull(spec.detector, bmp)
        // Segmentación pelo / piel de la referencia (vistas faciales): óvalo ajustado al límite real de
        // la cara (la guía se adapta a él) y línea de implantación
        val faceView = zone.guide == com.photomed.app.data.GuideType.FACE_FRONT || zone.guide == com.photomed.app.data.GuideType.FACE_OBLIQUE
        val labels = if (faceView || tracksHairline) runCatching { hairSkin?.labels(bmp) }.getOrNull() else null
        if (labels != null && faceView && ref.points.isNotEmpty()) ref = ref.copy(points = HairSkin.snapOval(labels, ref.points))
        // Bordes solo del cuerpo (sin los del fondo): región del paciente en la referencia
        refBody = runCatching { BodyRegion.of(zone, bmp) { b -> segmenter?.segment(b) } }.getOrNull()
        referenceEdges = runCatching { FreeContour.edgesOf(bmp, refBody) }.getOrNull()
        referenceFree = if (zone.guide == com.photomed.app.data.GuideType.FREE)
            runCatching { FreeContour.from(bmp) { b -> segmenter?.segment(b) } }.getOrNull() else null
        referenceHairline = if (!tracksHairline || labels == null) null else runCatching {
            HairSkin.hairline(labels, ref.points)?.points
        }.getOrNull()
        referenceSilhouette = runCatching {
            val plain = zone.guide == com.photomed.app.data.GuideType.HANDS || zone.guide == com.photomed.app.data.GuideType.FEET
            val mask = if (plain) ForegroundOnPlain.mask(bmp.rgb(), bmp.width, bmp.height)
                else SubjectSegmenter().use { s -> s.segment(bmp) }
            mask?.let { Silhouette.fromMask(it, Silhouette.axisOf(ref.points)) }
        }.getOrNull()
        refProfile = if (!isProfile) null else referenceSilhouette?.profilePoints(frontRight)?.takeIf { it.size >= 3 }
        frameSil = null; frameSilAt = 0L
        refGrayAll = ImageOps.gray(bmp, EDGE_W)
        val g = ImageOps.gray(bmp, EDGE_W, refBody)
        refGray = g
        refEdgePts = ContourMatch.referencePoints(g, spec.contours)
        frameBody = null; frameBodyAt = 0L
        last = null; matched = false
    }

    /** Sin referencia: estandarización de la toma (vista, Frankfort, iluminación). */
    fun standardize(frame: Bitmap, smooth: Boolean = true): LiveFrame {
        val cur = detected(frame, smooth)
        val light = if (spec.detector == Detector.FACE && cur.points.isNotEmpty())
            Standardization.lighting(frame.rgb(), frame.width, frame.height, cur.points) else null
        var eval = withDiagnostic(Standardization.evaluate(zone, cur, light, expression), cur)
        // Perfil sin rostro detectado: si la silueta muestra un perfil hacia el lado de la vista
        // (nariz y mentón en el borde anterior), se acepta como perfil
        if (isProfile && cur.points.size < LiveDecision.MIN_POINTS) {
            val fp = frameProfile(frame)
            if (fp != null && "punta nasal" in fp && "pogonion" in fp)
                eval = MatchEval(MatchStatus.CLOSE, 0.85f, emptyList(), threshold = 1f)
        }
        // Modo libre: el contorno se diseña con lo visible en el cuadro (como mucho cada 0,6 s)
        val free = if (zone.guide != com.photomed.app.data.GuideType.FREE) null else {
            val now = System.currentTimeMillis()
            if (now - lastFreeAt > 600) { lastFreeAt = now; lastFree = runCatching { FreeContour.from(frame) { b -> segmenter?.segment(b) } }.getOrNull() }
            lastFree
        }
        return LiveFrame(eval, method = "estandarización", currentPointsAll = cur.points, standard = true, freeContour = free,
            hairline = if (smooth) hairlineOf(frame, cur) else null,
            headYaw = cur.yaw, headPitch = cur.pose?.pitch ?: cur.pitch, gaze = cur.gaze)
    }

    /** Exigir que la mirada coincida con la de la referencia. */
    @Volatile var useGaze: Boolean = true

    /** false: ignorar la referencia (superposición apagada) y estandarizar. */
    @Volatile var useReference: Boolean = true
    /** Expresión pedida (arrugas dinámicas): se comprueba en lugar de la expresión neutra. */
    @Volatile var expression: com.photomed.app.data.FacialExpression = com.photomed.app.data.FacialExpression.NEUTRAL

    /** Detección del cuadro; en vivo, suavizada en el tiempo (One-Euro) para que la guía y las indicaciones no tiemblen. */
    private fun detected(frame: Bitmap, smooth: Boolean): Detection {
        val d = detector.detectFull(spec.detector, frame)
        return if (smooth) smoother.smooth(d, System.currentTimeMillis()) else d
    }
    private val smoother = LandmarkSmoother()

    fun analyze(frame: Bitmap, smooth: Boolean = true): LiveFrame {
        val refG = refGray?.takeIf { useReference } ?: return standardize(frame, smooth)
        var eval: MatchEval
        val method: String
        var drawKeys: List<String> = emptyList()
        val total: Int
        var cur = Detection.EMPTY
        val rp = refProfile
        if (spec.detector != Detector.NONE && ref.points.size >= LiveDecision.MIN_POINTS &&
            !(isProfile && rp != null && detected(frame, smooth).also { cur = it }.points.size < LiveDecision.MIN_POINTS)) {
            // Zona con detector y referencia con puntos: solo coincide si el cuadro también los tiene.
            if (cur.points.isEmpty()) cur = detected(frame, smooth)
            eval = LiveDecision.evaluate(spec, ref, cur, threshold, matched, useGaze)
            drawKeys = spec.landmarks.filter { it in ref.points && it in cur.points }
            method = "puntos anatómicos"; total = drawKeys.size
            last = null
        } else if (isProfile && rp != null) {
            // Perfil cerca de 90° (los detectores pierden el rostro): puntos de la silueta
            val fp = frameProfile(frame)
            val keys = rp.keys.filter { fp?.containsKey(it) == true }
            val t = if (keys.size >= 3) Similarity.estimate(keys.map { fp!![it]!! }, keys.map { rp[it]!! }) else null
            eval = LiveMatch.evaluate(t, spec.pivot, matched, null, threshold, gate = true)
            method = "silueta del perfil"; total = keys.size
            last = null
        } else {
            // cuerpo en el cuadro actual (segmentación como mucho cada 0,5 s; el encuadre cambia poco)
            if (refBody != null) {
                val now = System.currentTimeMillis()
                if (now - frameBodyAt > 500) {
                    frameBodyAt = now
                    frameBody = runCatching { BodyRegion.of(zone, frame) { b -> segmenter?.segment(b) } }.getOrNull()
                }
            }
            val body = frameBody?.takeIf { refBody != null }
            val g = ImageOps.gray(frame, EDGE_W, body)
            val refUse = if (body != null) refG else refGrayAll ?: refG
            val r = edges.align(refUse, g, last, if (last != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.LIVE, levels = 2)
            val t = if (EdgeAligner.isConfident(r.score)) r.transform else null
            last = t ?: last
            eval = LiveMatch.evaluate(t, spec.pivot, matched, ContourMatch.fraction(refEdgePts, g), threshold, gate = true)
            method = "puntos de contorno"; total = refEdgePts.size
        }
        matched = eval.status == MatchStatus.MATCH
        eval = withDiagnostic(eval, cur)
        val shown = if (drawKeys.size > MAX_DRAWN) drawKeys.filterIndexed { i, _ -> i % (drawKeys.size / MAX_DRAWN + 1) == 0 } else drawKeys
        return LiveFrame(eval, shown.map { ref.points[it]!! }, shown.map { cur.points[it]!! }, method, total, cur.points, headYaw = cur.yaw, headPitch = cur.pose?.pitch ?: cur.pitch, gaze = cur.gaze,
            hairline = if (smooth) hairlineOf(frame, cur) else null)
    }

    /**
     * Puntuación de una foto ya tomada frente a la referencia (para quedarse con la mejor de una
     * ráfaga), sin alterar el estado del análisis en vivo (histéresis y última transformación).
     * Mayor = mejor; > 1 si coincide.
     */
    fun scoreOf(photo: Bitmap): Float {
        val m = matched; val l = last
        return try {
            val e = analyze(photo, smooth = false).eval
            when (e.status) {
                MatchStatus.MATCH -> 1f + (e.pointFraction ?: e.quality)
                MatchStatus.SEARCHING -> 0f
                else -> e.pointFraction?.let { it / e.threshold.coerceAtLeast(0.05f) }?.coerceAtMost(0.99f) ?: e.quality
            }
        } finally { matched = m; last = l }
    }

    /** Sin puntos y con un error del detector: mostrarlo para poder diagnosticar en el dispositivo. */
    private fun withDiagnostic(e: MatchEval, cur: Detection): MatchEval {
        // segmentación de pelo y piel que no carga en este teléfono: decirlo (la línea del pelo no saldría)
        if (tracksHairline && cur.points.isNotEmpty() && hairSkin == null)
            return e.copy(hints = e.hints + "Detector (pelo y piel): ${HairSkinSegmenter.lastError ?: "no disponible"}")
        if (spec.detector == Detector.NONE || cur.points.isNotEmpty()) return e
        val d = detector.diagnostic ?: return e
        return e.copy(hints = e.hints + "Detector ($engine): $d")
    }

    private var liveHairline: List<Pt>? = null
    private var liveHairlineAt = 0L

    /**
     * Línea de implantación del cuadro actual (segmentación como mucho cada 0,5 s; se mantiene la
     * última mientras haya rostro). Así se ve también en la primera toma y sin superponer la referencia.
     */
    private fun hairlineOf(frame: Bitmap, cur: Detection): List<Pt>? {
        if (!tracksHairline || cur.points.size < LiveDecision.MIN_POINTS) { liveHairline = null; return null }
        val now = System.currentTimeMillis()
        if (now - liveHairlineAt > 500) {
            liveHairlineAt = now
            liveHairline = runCatching { hairSkin?.labels(frame)?.let { HairSkin.hairline(it, cur.points)?.points } }.getOrNull()
        }
        return liveHairline
    }

    /** Altura de la frente (cejas → línea de implantación) / interpupilar en una foto; null si no se mide. */
    fun hairlineIpd(photo: Bitmap): Float? = if (!tracksHairline) null else NativeGate.tryRunBlocking("línea del pelo al capturar", 3000) { hairlineIpdGated(photo) }

    private fun hairlineIpdGated(photo: Bitmap): Float? = runCatching {
        val d = detector.detectFull(spec.detector, photo)
        // cejas ajustadas a la imagen antes de medir la frente
        hairSkin?.labels(photo)?.let { lb ->
            val pts = runCatching { BrowRefine.refineKeys(d.points, FaceCrop.lum(photo), lb) }.getOrDefault(d.points)
            HairSkin.hairline(lb, pts)?.heightIpd
        }
    }.getOrNull()

    override fun close() { detector.close(); runCatching { segmenterOrNull?.close() }; runCatching { if (hairSkinLazy.isInitialized()) hairSkinLazy.value?.close() } }

    companion object {
        const val EDGE_W = 96
        const val MAX_DRAWN = 36
    }
}
