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
    /** Luz en el rostro (mejillas y centro), si se midió. */
    val light: Standardization.Lighting? = null,
    /** Esquema de la guía ajustado a lo detectado (manos, pies, miembros; ver [BodySchema]). */
    val schema: BodySchema.Schema? = null,
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
    /** Manos (solo en las zonas de manos) y clases por píxel recientes para los criterios de la zona. */
    private val handsLazy = lazy { if (ZoneCriteria.needsHands(zone)) appContext?.let { HandDetector.createOrNull(it) } else null }
    private var lastHands: List<ZoneCriteria.Hand>? = null; private var lastHandsAt = 0L
    private var lastClasses: ZoneCriteria.Classes? = null; private var lastClassesAt = 0L
    /** Esquema de la guía del último cuadro (null: la ilustración fija). */
    @Volatile private var lastSchema: BodySchema.Schema? = null
    /** Modo de una sola extremidad (mano, pie o pierna): los criterios piden una, no las dos. */
    @Volatile var singleLimb: Boolean = false
    /** Giro de la guía en pasos de 90° en sentido horario (botón «Girar 90°» de la referencia). */
    @Volatile var guideRot: Int = 0
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
    /** Detección completa de la referencia (puntos, pose y mirada), para guardarla sin la imagen. */
    val referenceDetection: Detection get() = ref
    /** Puntos del perfil de la referencia (vistas de perfil), si los hay. */
    val referenceProfile: Map<String, Pt>? get() = refProfile
    /** Referencia sin imagen (protocolo): solo coincide por puntos anatómicos o del perfil. */
    private var featureOnly = false

    /**
     * Referencia a partir de sus características (protocolo compartido, sin la foto): puntos, pose,
     * mirada, línea del pelo y perfil. Las zonas sin puntos (contornos de cuerpo) necesitan la imagen:
     * ahí se estandariza la toma.
     */
    fun setReferenceFeatures(d: Detection, hairline: List<Pt>?, profile: Map<String, Pt>?) {
        ref = d; referenceHairline = hairline; refProfile = profile?.takeIf { isProfile && it.size >= 3 }; referenceSchema = null
        referenceSilhouette = null; referenceEdges = null; referenceFree = null
        refBody = null; refGray = null; refGrayAll = null; refEdgePts = emptyList()
        frameSil = null; frameSilAt = 0L; frameBody = null; frameBodyAt = 0L
        featureOnly = true; last = null; matched = false
    }
    private var last: Similarity? = null
    private var matched = false

    /** Umbral de puntos coincidentes (0..1). */
    @Volatile var threshold: Float = LiveMatch.DEFAULT_THRESHOLD
    /** Medir la luz del rostro también con referencia (protocolo con luz mínima). */
    @Volatile var measureLight: Boolean = false

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

    /** Contorno de la referencia (manos, pies, miembros) en sus coordenadas: se dibuja sobre la foto anterior. */
    @Volatile var referenceSchema: BodySchema.Schema? = null
        private set

    fun setReference(bmp: Bitmap) {
        featureOnly = false
        referenceSchema = null
        ref = detector.detectFull(spec.detector, bmp)
        // zonas del cuerpo: los mismos puntos adicionales que en la toma (manos, pies, bordes)
        if (ZoneCriteria.applies(zone)) runCatching {
            val hs = if (ZoneCriteria.needsHands(zone)) handsLazy.value?.detect(bmp) else null
            val cl = if (ZoneCriteria.needsClasses(zone)) referenceClasses(bmp) else null
            val refScene = ZoneCriteria.Scene(ref.points, hs, cl, bmp.height / bmp.width.toFloat(), single = singleLimb)
            val extra = BodyPoints.of(zone, refScene)
            if (extra.isNotEmpty()) ref = ref.copy(points = ref.points + extra)
            // contorno de la referencia con el mismo detector que en vivo (sobre la propia foto: coincide con ella)
            referenceSchema = runCatching { BodySchema.of(zone, refScene) }.getOrNull()?.takeIf { !it.isEmpty }
        }
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
        // luz: también sin la cara entera (primeros planos), por regiones
        val light = if (spec.detector == Detector.FACE)
            runCatching { Standardization.lighting(frame.rgb(), frame.width, frame.height, cur.points, cur.yaw) }.getOrNull() else null
        var eval = withDiagnostic(Standardization.evaluate(zone, cur, light, expression), cur)
        // sin puntos que comprobar (zonas sin detector, cuerpo de espaldas, manos, pies, primeros
        // planos sin cara): dispara con el encuadre quieto; si no, se reinicia
        if (SteadyFraming.applies(zone, cur)) eval = steady.update(ImageOps.luma(frame, EDGE_W), System.currentTimeMillis())
        else steady.reset()
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
        eval = withCriteria(eval, frame, cur)
        return LiveFrame(eval, method = "estandarización", currentPointsAll = cur.points, standard = true, freeContour = free, light = light, schema = lastSchema,
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
        var d = detector.detectFull(spec.detector, frame)
        // zonas del cuerpo: más puntos de referencia (manos, pies, bordes de miembros y tronco)
        if (ZoneCriteria.applies(zone)) {
            refreshScene(frame)
            val extra = runCatching { BodyPoints.of(zone, ZoneCriteria.Scene(d.points, lastHands, lastClasses, frame.height / frame.width.toFloat(), single = singleLimb)) }
                .getOrDefault(emptyMap())
            if (extra.isNotEmpty()) d = d.copy(points = d.points + extra)
        }
        return if (smooth) smoother.smooth(d, System.currentTimeMillis()) else d
    }

    /** Manos y piel por píxel del cuadro (como mucho cada 150 / 500 ms; en las manos, la piel con las manos). */
    private fun refreshScene(frame: Bitmap) {
        val now = System.currentTimeMillis()
        var handsNow = false
        if (ZoneCriteria.needsHands(zone) && now - lastHandsAt > 150) {
            lastHandsAt = now; handsNow = true
            lastHands = handsLazy.value?.let { h -> runCatching { h.detect(frame) }.getOrNull() }
        }
        // en las manos, la piel del mismo cuadro que los puntos (el contorno sigue a la mano sin retraso)
        // pies: cada 0,2 s (el contorno dibujado sigue al pie al mover la cámara)
        val every = if (zone.guide == com.photomed.app.data.GuideType.FEET) 200 else 500
        if (ZoneCriteria.needsClasses(zone) && (now - lastClassesAt > every || handsNow)) {
            lastClassesAt = now
            val raw = runCatching { hairSkin?.labels(frame) }.getOrNull()?.let { lb0 ->
                // la rejilla con la proporción del cuadro (las coordenadas se miden en anchos del cuadro)
                val lb = sameAspect(lb0, frame)
                // pies y manos: con el color del cuadro en la misma rejilla (piel, surcos, uñas)
                if (zone.guide == com.photomed.app.data.GuideType.FEET || zone.guide == com.photomed.app.data.GuideType.HANDS) ZoneCriteria.Classes(lb.width, lb.height, lb.data, rgbOf(frame, lb.width, lb.height))
                else ZoneCriteria.Classes.of(lb)
            }
            lastRawClasses = raw
            // estable: mayoría de las últimas segmentaciones, solo con la escena quieta (si se mueve, la actual)
            lastClasses = raw?.let { fusion.add(it) }
        }
    }

    private val fusion = ClassesFusion()
    private var lastFace: Map<String, Pt> = emptyMap(); private var lastFaceAt = 0L
    private val SCALP_GUIDES = setOf(com.photomed.app.data.GuideType.SCALP, com.photomed.app.data.GuideType.SCALP_CROWN, com.photomed.app.data.GuideType.SCALP_PARIETAL)
    /** Última segmentación sin fusionar: para dibujar el contorno sin retraso. */
    @Volatile private var lastRawClasses: ZoneCriteria.Classes? = null

    /** Etiquetas con la proporción del cuadro (si el modelo devolviera otra, se remuestrean al ancho de la máscara). */
    private fun sameAspect(l: FaceLabels, frame: Bitmap): FaceLabels {
        val h2 = Math.round(l.width * frame.height / frame.width.toFloat())
        if (h2 == l.height) return l
        val out = ByteArray(l.width * h2)
        for (y in 0 until h2) { val sy = (y * l.height / h2).coerceIn(0, l.height - 1); System.arraycopy(l.data, sy * l.width, out, y * l.width, l.width) }
        return FaceLabels(l.width, h2, out)
    }

    /**
     * Piel por píxel de la referencia: se segmenta una sola vez, así que una segmentación rara (un dedo
     * perdido) la estropearía para toda la toma; se segmenta con tres luces (−8 %, igual, +8 %) y se toma
     * la mayoría, como en vivo.
     */
    private fun referenceClasses(bmp: Bitmap): ZoneCriteria.Classes? {
        val seg = hairSkin ?: return null
        val f = ClassesFusion(); var last: ZoneCriteria.Classes? = null
        for (k in floatArrayOf(0.92f, 1.08f, 1f)) {
            val b = if (k == 1f) bmp else runCatching { brightness(bmp, k) }.getOrNull() ?: continue
            val lb = runCatching { seg.labels(b) }.getOrNull() ?: continue
            val c = if (zone.guide == com.photomed.app.data.GuideType.FEET || zone.guide == com.photomed.app.data.GuideType.HANDS) ZoneCriteria.Classes(lb.width, lb.height, lb.data, rgbOf(bmp, lb.width, lb.height))
                else ZoneCriteria.Classes.of(lb)
            last = f.add(c)
        }
        return last
    }

    private fun brightness(b: Bitmap, k: Float): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val cm = android.graphics.ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        android.graphics.Canvas(out).drawBitmap(b, 0f, 0f, android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(cm) })
        return out
    }

    private fun rgbOf(frame: Bitmap, w: Int, h: Int): IntArray? = runCatching {
        val b = if (frame.width == w && frame.height == h) frame else Bitmap.createScaledBitmap(frame, w, h, true)
        IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
    }.getOrNull()
    private val smoother = LandmarkSmoother()
    private val steady = SteadyFraming()

    fun analyze(frame: Bitmap, smooth: Boolean = true): LiveFrame {
        if (!useReference) return standardize(frame, smooth)
        val byFeatures = featureOnly && (usesPoints(spec, ref) || refProfile != null)
        val refG = refGray ?: if (byFeatures) GrayImage(1, 1) else return standardize(frame, smooth)
        var eval: MatchEval
        val method: String
        var drawKeys: List<String> = emptyList()
        val total: Int
        var cur = Detection.EMPTY
        val rp = refProfile
        if (usesPoints(spec, ref) &&
            !(isProfile && rp != null && detected(frame, smooth).also { cur = it }.points.size < LiveDecision.MIN_POINTS)) {
            // Zona con detector y referencia con puntos: solo coincide si el cuadro también los tiene.
            if (cur.points.isEmpty()) cur = detected(frame, smooth)
            eval = LiveDecision.evaluate(spec, ref, cur, threshold, matched, useGaze, expression = expression)
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
            // referencia sin imagen: sin puntos no hay con qué comparar los bordes; se estandariza la toma
            if (featureOnly) return standardize(frame, smooth)
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
            eval = edgeEval(t, r.score, ContourMatch.fraction(refEdgePts, g), spec.pivot, matched, threshold)
            method = "puntos de contorno"; total = refEdgePts.size
        }
        matched = eval.status == MatchStatus.MATCH
        eval = withDiagnostic(eval, cur)
        val shown = if (drawKeys.size > MAX_DRAWN) drawKeys.filterIndexed { i, _ -> i % (drawKeys.size / MAX_DRAWN + 1) == 0 } else drawKeys
        // luz del rostro: siempre (antes solo en protocolos); fuera de ellos, con el mínimo y máximo generales
        val light = if (spec.detector == Detector.FACE && cur.points.isNotEmpty())
            runCatching { Standardization.lighting(frame.rgb(), frame.width, frame.height, cur.points, cur.yaw) }.getOrNull() else null
        if (!measureLight) light?.let { Standardization.lightHints(it, cur.yaw) }?.takeIf { it.isNotEmpty() }?.let { lh ->
            eval = eval.copy(status = if (eval.status == MatchStatus.MATCH) MatchStatus.CLOSE else eval.status,
                quality = eval.quality.coerceAtMost(0.75f), hints = eval.hints + lh)
        }
        eval = withCriteria(eval, frame, cur)
        return LiveFrame(eval, shown.map { ref.points[it]!! }, shown.map { cur.points[it]!! }, method, total, cur.points, headYaw = cur.yaw, headPitch = cur.pose?.pitch ?: cur.pitch, gaze = cur.gaze,
            hairline = if (smooth) hairlineOf(frame, cur) else null, light = light, schema = lastSchema)
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

    /**
     * Criterios clínicos de la zona ([ZoneCriteria]) sobre el cuadro: si no se cumplen, la coincidencia no
     * llega a «coincide» y se muestran sus indicaciones. Manos cada 0,25 s y segmentación cada 0,5 s.
     */
    private fun withCriteria(e: MatchEval, frame: Bitmap, cur: Detection): MatchEval {
        if (!ZoneCriteria.applies(zone)) { lastSchema = null; return e }
        refreshScene(frame)
        // con referencia por bordes no se buscaron puntos: los criterios del cuerpo los necesitan
        val pose = if (spec.detector != Detector.POSE) emptyMap()
            else cur.points.ifEmpty { if (ZoneCriteria.needsHands(zone)) emptyMap() else runCatching { detected(frame, true).points }.getOrDefault(emptyMap()) }
        val aspect = frame.height / frame.width.toFloat()
        val q = guideRot
        // con la guía girada, los criterios se miden en su orientación y el esquema vuelve al cuadro
        // zona del ojo (sin detector): si el detector de rostro ve la cara entera, no es un primer plano de un ojo
        // (y en el cuero cabelludo: con un rostro a la vista no es el cuero cabelludo; sin él, la piel es calvicie)
        if ((zone.guide == com.photomed.app.data.GuideType.EYE || zone.guide in SCALP_GUIDES) && System.currentTimeMillis() - lastFaceAt > 500) {
            lastFaceAt = System.currentTimeMillis()
            lastFace = runCatching { detector.detectFull(Detector.FACE, frame).points }.getOrDefault(emptyMap())
        }
        // rostro: sus puntos (para ver si algo lo tapa: gafas, gorra, mano)
        val face = if (spec.detector == Detector.FACE && cur.points.isNotEmpty()) cur.points else lastFace
        val scene = with(ZoneCriteria) { ZoneCriteria.Scene(pose, lastHands, lastClasses, aspect, single = singleLimb, face = face).rotatedCcw(q) }
        // el contorno dibujado, con la segmentación más reciente (sin la fusión, que va por detrás si se mueve)
        val drawScene = if (lastRawClasses === lastClasses) scene
            else with(ZoneCriteria) { ZoneCriteria.Scene(pose, lastHands, lastRawClasses, aspect, single = singleLimb).rotatedCcw(q) }
        lastSchema = runCatching { BodySchema.of(zone, drawScene)?.let { sch -> if (q % 4 == 0) sch else with(BodySchema) { sch.map { ZoneCriteria.unrotate(it, q, aspect) } } } }.getOrNull()
        // luz de la zona del cuerpo (sobre su piel): poca o demasiada también impide el disparo
        val lightNow = if (!ZoneCriteria.isBody(zone)) emptyList() else runCatching {
            val c = lastRawClasses
            val px = frame.rgb()
            val skin = c?.let { cl -> { x: Int, y: Int ->
                val k = cl.at(x * cl.width / frame.width, y * cl.height / frame.height); k == FaceLabels.BODY_SKIN || k == FaceLabels.FACE_SKIN } }
            Standardization.bodyLightHints(px, frame.width, frame.height, skin)
        }.getOrDefault(emptyList())
        val now = ZoneCriteria.hints(zone, scene) + lightNow
        // decisión estable: la zona cumple si cumple en al menos 2 de los 3 últimos análisis (una
        // segmentación o detección suelta no cambia el veredicto ni hace parpadear las indicaciones)
        synchronized(votes) {
            votes.addLast(now.isEmpty()); while (votes.size > 3) votes.removeFirst()
            val ok = votes.size >= 2 && votes.count { it } >= 2
            if (ok) return e
        }
        // cumple ahora pero aún no en 2 de 3 análisis: «comprobando» (no el último aviso, ya resuelto)
        val hints = now.ifEmpty { listOf(ZoneCriteria.HINT_ANALYZING) }
        // zona descentrada: el desplazamiento (en el cuadro) enciende las flechas del borde hacia donde mover
        // el teléfono, como en la coincidencia con la referencia
        val move = runCatching { ZoneCriteria.centerOffset(zone, scene) }.getOrNull()?.let { (c, d) ->
            val c0 = ZoneCriteria.unrotate(c, q, aspect); val t0 = ZoneCriteria.unrotate(Pt(c.x + d.x, c.y + d.y), q, aspect)
            Pt(t0.x - c0.x, t0.y - c0.y)
        }
        val base = if (move != null && e.dx == 0f && e.dy == 0f) e.copy(dx = move.x, dy = move.y) else e
        return base.copy(status = if (e.status == MatchStatus.MATCH) MatchStatus.CLOSE else e.status,
            quality = e.quality.coerceAtMost(0.75f), hints = e.hints + hints)
    }
    private val votes = ArrayDeque<Boolean>()

    override fun close() { detector.close(); runCatching { if (handsLazy.isInitialized()) handsLazy.value?.close() }; runCatching { segmenterOrNull?.close() }; runCatching { if (hairSkinLazy.isInitialized()) hairSkinLazy.value?.close() } }

    companion object {
        const val EDGE_W = 96

        /**
         * Con referencia: ¿se comparan puntos? Solo si la referencia tiene los de ESTA zona (no basta
         * con cualquier punto del cuerpo: si no, se quedaba esperando unos puntos que no existen); si no,
         * se alinean los bordes de la imagen.
         */
        /** Confianza mínima de la alineación de bordes para decidir solo por su geometría. */
        const val EDGE_MATCH_SCORE = 0.6f

        /**
         * Coincidencia por bordes (con referencia, sin puntos). Con la alineación segura, decide su
         * geometría (desplazamiento, escala y giro dentro de tolerancia): el % de bordes coincidentes,
         * corregido por azar, sale bajo en escenas con mucha textura (piel, pelo, manos) incluso con la
         * misma imagen. Si la alineación no es segura, decide ese % como antes.
         */
        fun edgeEval(t: Similarity?, score: Float, fraction: Float, pivot: Pt, matched: Boolean, threshold: Float): MatchEval =
            if (t != null && score >= EDGE_MATCH_SCORE) LiveMatch.evaluate(t, pivot, matched, null, threshold, gate = true)
            else LiveMatch.evaluate(t, pivot, matched, fraction, threshold, gate = true)

        fun usesPoints(spec: ZoneAlignment, ref: Detection) =
            spec.detector != Detector.NONE && spec.landmarks.count { it in ref.points } >= LiveDecision.MIN_POINTS
        const val MAX_DRAWN = 36
    }
}
