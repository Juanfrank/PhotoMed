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
)

/**
 * Motor de coincidencia en vivo entre la foto de referencia (superpuesta) y la cámara.
 * Por cuadro: puntos anatómicos (ML Kit, modo continuo) → semejanza actual → referencia;
 * si no hay puntos suficientes, coincidencia de contornos anatómicos en baja resolución
 * partiendo de la última estimación. Se llama desde el hilo de análisis (bloqueante).
 */
class LiveMatcher(private val zone: Zone) : Closeable {
    private val spec = ZoneAlignment.of(zone)
    private val detector = LandmarkDetector(stream = true)
    private val edges = EdgeAligner(spec.contours)
    private var refPts: Map<String, Pt> = emptyMap()
    private var refGray: GrayImage? = null
    private var last: Similarity? = null
    private var matched = false

    /** Prepara la referencia (foto anterior, ya en encuadre 3:4). */
    fun setReference(bmp: Bitmap) {
        refPts = detector.detect(spec.detector, bmp)
        refGray = ImageOps.gray(bmp, EDGE_W)
        last = null; matched = false
    }

    fun analyze(frame: Bitmap): LiveFrame {
        val ref = refGray ?: return LiveFrame(LiveMatch.evaluate(null, spec.pivot))
        val cur = detector.detect(spec.detector, frame)
        val lm = landmarkTransform(spec.landmarks, refPts, cur) // actual → referencia
        val t: Similarity?
        val method: String
        if (lm != null) {
            t = lm.first; method = "puntos (${lm.second.size})"
        } else {
            val r = edges.align(ref, ImageOps.gray(frame, EDGE_W), last,
                if (last != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.LIVE, levels = 2)
            t = if (EdgeAligner.isConfident(r.score)) r.transform else null
            method = "contornos"
        }
        last = t ?: last
        val eval = LiveMatch.evaluate(t, spec.pivot, matched)
        matched = eval.status == MatchStatus.MATCH
        val keys = lm?.second.orEmpty().let { k -> if (k.size > MAX_DRAWN) k.filterIndexed { i, _ -> i % (k.size / MAX_DRAWN + 1) == 0 } else k }
        return LiveFrame(eval, keys.mapNotNull { refPts[it] }, keys.mapNotNull { cur[it] }, method)
    }

    override fun close() = detector.close()

    companion object {
        const val EDGE_W = 96
        const val MAX_DRAWN = 36
    }
}
