package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

enum class MatchStatus { SEARCHING, FAR, CLOSE, MATCH }

/** Evaluación de un cuadro de la cámara frente a la foto de referencia. */
data class MatchEval(
    val status: MatchStatus,
    /** 0..1: cuán cerca está del encuadre de referencia. */
    val quality: Float,
    val hints: List<String>,
    /** Desviación en el pivote anatómico (fracción del ancho), escala y rotación. */
    val offset: Float = 0f,
    val scaleError: Float = 0f,
    val rotationDeg: Float = 0f,
)

/**
 * Criterios de coincidencia en vivo. [t] lleva el cuadro actual sobre la referencia
 * (actual → referencia); se mide en el pivote anatómico de la zona.
 */
object LiveMatch {
    const val MATCH_OFFSET = 0.02f      // 2 % del ancho
    const val MATCH_SCALE = 0.03f       // 3 %
    const val MATCH_ROT = 2f            // 2°
    const val CLOSE_FACTOR = 2.5f

    fun evaluate(t: Similarity?, pivot: Pt, wasMatched: Boolean = false): MatchEval {
        if (t == null) return MatchEval(MatchStatus.SEARCHING, 0f, listOf("Buscando referencias anatómicas…"))
        val d = t.apply(pivot) - pivot
        val off = hypot(d.x, d.y)
        val se = abs(ln(t.scale))
        val rd = abs(t.rotationDegrees)
        // histéresis: una vez alineado, tolera un 30 % más antes de perder la coincidencia
        val k = if (wasMatched) 1.3f else 1f
        val norm = maxOf(off / MATCH_OFFSET, se / MATCH_SCALE, rd / MATCH_ROT)
        val status = when {
            norm <= k -> MatchStatus.MATCH
            norm <= CLOSE_FACTOR -> MatchStatus.CLOSE
            else -> MatchStatus.FAR
        }
        val hints = mutableListOf<String>()
        if (status != MatchStatus.MATCH) {
            // El contenido debe desplazarse d para coincidir: mover la cámara en sentido contrario.
            if (abs(d.x) > MATCH_OFFSET * 0.7f) hints += if (d.x > 0) "← Mueva la cámara a la izquierda" else "Mueva la cámara a la derecha →"
            if (abs(d.y) > MATCH_OFFSET * 0.7f) hints += if (d.y > 0) "↑ Suba la cámara" else "↓ Baje la cámara"
            if (se > MATCH_SCALE * 0.7f) hints += if (t.scale > 1f) "Acérquese" else "Aléjese"
            if (rd > MATCH_ROT * 0.7f) hints += if (t.rotationDegrees > 0) "Gire la cámara ↺" else "Gire la cámara ↻"
        }
        val quality = (1f - (norm - 1f) / (CLOSE_FACTOR * 2f)).coerceIn(0f, 1f).let { if (norm <= 1f) 1f else it }
        return MatchEval(status, quality, hints, off, se, t.rotationDegrees)
    }
}
