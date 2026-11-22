package com.photomed.app.align

/**
 * Condición del disparo automático (la misma en la cámara y en las pruebas): coincide con la guía o
 * la referencia, con la expresión pedida y la luz del protocolo; sin protocolo, también «casi
 * alineada» (≥ 80 %) si el ajuste lo permite: la alineación fina se hace después.
 */
object AutoCapture {
    /** Grado de coincidencia 0..1 (null: buscando); como el anillo del disparador. */
    fun degree(eval: MatchEval?): Float? = when {
        eval == null || eval.status == MatchStatus.SEARCHING -> null
        eval.status == MatchStatus.MATCH -> 1f
        else -> (eval.pointFraction?.let { it / eval.threshold.coerceAtLeast(0.05f) } ?: eval.quality).coerceIn(0f, 0.95f)
    }

    fun ready(eval: MatchEval?, lightOk: Boolean = true, nearCapture: Boolean = true, protocol: Boolean = false): Boolean =
        eval != null && eval.expressionOk && lightOk &&
            (eval.status == MatchStatus.MATCH || (nearCapture && !protocol && (degree(eval) ?: 0f) >= 0.8f))
}
