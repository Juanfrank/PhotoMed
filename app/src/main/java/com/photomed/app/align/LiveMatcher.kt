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
class LiveMatcher(private val zone: Zone, context: android.content.Context? = null, engine: FaceEngine = FaceEngine.ML_KIT) : Closeable {
    private val spec = ZoneAlignment.of(zone)
    private val detector = LandmarkDetector(stream = true, context, engine)
    val engine: FaceEngine = detector.engine
    private val edges = EdgeAligner(spec.contours)
    private var ref: Detection = Detection.EMPTY
    private var refGray: GrayImage? = null
    private var refEdgePts: List<ContourMatch.EdgePt> = emptyList()

    /** Puntos de la referencia (para adaptar la guía a la foto). */
    val referencePoints: Map<String, Pt> get() = ref.points
    private var last: Similarity? = null
    private var matched = false

    /** Umbral de puntos coincidentes (0..1). */
    @Volatile var threshold: Float = LiveMatch.DEFAULT_THRESHOLD

    /** Silueta del sujeto en la referencia (segmentación), para adaptar la guía corporal y de perfil. */
    @Volatile var referenceSilhouette: Silhouette? = null
        private set

    fun setReference(bmp: Bitmap) {
        ref = detector.detectFull(spec.detector, bmp)
        referenceSilhouette = runCatching {
            SubjectSegmenter().use { s -> s.segment(bmp) }?.let { Silhouette.fromMask(it, Silhouette.axisOf(ref.points)) }
        }.getOrNull()
        val g = ImageOps.gray(bmp, EDGE_W)
        refGray = g
        refEdgePts = ContourMatch.referencePoints(g, spec.contours)
        last = null; matched = false
    }

    /** Sin referencia: estandarización de la toma (vista, Frankfort, iluminación). */
    fun standardize(frame: Bitmap): LiveFrame {
        val cur = detector.detectFull(spec.detector, frame)
        val light = if (spec.detector == Detector.FACE && cur.points.isNotEmpty())
            Standardization.lighting(frame.rgb(), frame.width, frame.height, cur.points) else null
        val eval = withDiagnostic(Standardization.evaluate(zone, cur, light), cur)
        return LiveFrame(eval, method = "estandarización", currentPointsAll = cur.points, standard = true,
            headYaw = cur.yaw, headPitch = cur.pose?.pitch ?: cur.pitch, gaze = cur.gaze)
    }

    /** Exigir que la mirada coincida con la de la referencia. */
    @Volatile var useGaze: Boolean = true

    /** false: ignorar la referencia (superposición apagada) y estandarizar. */
    @Volatile var useReference: Boolean = true

    fun analyze(frame: Bitmap): LiveFrame {
        val refG = refGray?.takeIf { useReference } ?: return standardize(frame)
        var eval: MatchEval
        val method: String
        var drawKeys: List<String> = emptyList()
        val total: Int
        var cur = Detection.EMPTY
        if (spec.detector != Detector.NONE && ref.points.size >= LiveDecision.MIN_POINTS) {
            // Zona con detector y referencia con puntos: solo coincide si el cuadro también los tiene.
            cur = detector.detectFull(spec.detector, frame)
            eval = LiveDecision.evaluate(spec, ref, cur, threshold, matched, useGaze)
            drawKeys = spec.landmarks.filter { it in ref.points && it in cur.points }
            method = "puntos anatómicos"; total = drawKeys.size
            last = null
        } else {
            val g = ImageOps.gray(frame, EDGE_W)
            val r = edges.align(refG, g, last, if (last != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.LIVE, levels = 2)
            val t = if (EdgeAligner.isConfident(r.score)) r.transform else null
            last = t ?: last
            eval = LiveMatch.evaluate(t, spec.pivot, matched, ContourMatch.fraction(refEdgePts, g), threshold, gate = true)
            method = "puntos de contorno"; total = refEdgePts.size
        }
        matched = eval.status == MatchStatus.MATCH
        eval = withDiagnostic(eval, cur)
        val shown = if (drawKeys.size > MAX_DRAWN) drawKeys.filterIndexed { i, _ -> i % (drawKeys.size / MAX_DRAWN + 1) == 0 } else drawKeys
        return LiveFrame(eval, shown.map { ref.points[it]!! }, shown.map { cur.points[it]!! }, method, total, cur.points, headYaw = cur.yaw, headPitch = cur.pose?.pitch ?: cur.pitch, gaze = cur.gaze)
    }

    /** Sin puntos y con un error del detector: mostrarlo para poder diagnosticar en el dispositivo. */
    private fun withDiagnostic(e: MatchEval, cur: Detection): MatchEval {
        if (spec.detector == Detector.NONE || cur.points.isNotEmpty()) return e
        val d = detector.diagnostic ?: return e
        return e.copy(hints = e.hints + "Detector ($engine): $d")
    }

    override fun close() = detector.close()

    companion object {
        const val EDGE_W = 96
        const val MAX_DRAWN = 36
    }
}
