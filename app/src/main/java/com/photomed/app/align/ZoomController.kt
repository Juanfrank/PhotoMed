package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Zoom asistido estable (lógica pura, probada con una planta simulada en `ZoomControllerTest`).
 *
 * La escala medida en cada cuadro (cuánto debe crecer el contenido para igualar la referencia)
 * tiene ruido y llega con retraso respecto del zoom aplicado. Para no oscilar:
 * - filtro exponencial del error en escala logarítmica;
 * - zona muerta (±2 %): dentro no se toca el zoom;
 * - tendencia sostenida: [CONFIRM] cuadros seguidos con el mismo signo fuera de la zona muerta;
 * - paso limitado (≤ 12 % por ajuste) con ganancia 0,6;
 * - asentamiento: tras cambiar el zoom se ignoran los cuadros durante [SETTLE_MS] (la cámara
 *   aplica el zoom y vuelve a enfocar; las medidas de ese intervalo son de antes del cambio);
 * - si el error cambia de signo tras un ajuste, se reduce la ganancia (amortiguación).
 */
class ZoomController(private val minZoom: Float = 1f, private val maxZoom: Float = 4f) {
    private var filtered = 0f
    private var streak = 0
    private var lastSign = 0
    private var settleUntil = 0L
    private var gain = GAIN
    private var lastStepSign = 0

    /** Se debe llamar cuando el enfoque terminó (o falló), para habilitar de nuevo las medidas. */
    var focusing = false

    /** true mientras el zoom se aplica o la cámara enfoca: no conviene disparar. */
    fun isSettling(nowMs: Long) = focusing || nowMs < settleUntil

    fun reset() { filtered = 0f; streak = 0; lastSign = 0; gain = GAIN; lastStepSign = 0 }

    /**
     * @param scale escala que necesita el contenido (> 1: acercar), medida en el cuadro actual.
     * @return el nuevo zoom a aplicar, o null si no hay que cambiarlo.
     */
    fun update(scale: Float, currentZoom: Float, nowMs: Long): Float? {
        if (nowMs < settleUntil || focusing) return null
        val e = ln(scale)
        filtered = if (streak == 0) e else filtered + ALPHA * (e - filtered)
        val sign = if (filtered > DEADBAND) 1 else if (filtered < -DEADBAND) -1 else 0
        if (sign == 0) { streak = 0; lastSign = 0; gain = minOf(GAIN, gain * 1.2f); return null }
        streak = if (sign == lastSign) streak + 1 else 1
        lastSign = sign
        if (streak < CONFIRM) return null
        if (lastStepSign != 0 && sign != lastStepSign) gain *= 0.5f // sobrepaso: amortiguar
        val step = (gain * filtered).coerceIn(-MAX_STEP, MAX_STEP)
        val target = (currentZoom * exp(step)).coerceIn(minZoom, maxZoom)
        if (abs(target - currentZoom) < 0.01f) return null
        lastStepSign = sign
        streak = 0; filtered = 0f; lastSign = 0
        settleUntil = nowMs + SETTLE_MS
        return target
    }

    companion object {
        const val ALPHA = 0.5f
        const val DEADBAND = 0.02f
        const val CONFIRM = 3
        const val GAIN = 0.6f
        const val MAX_STEP = 0.12f
        const val SETTLE_MS = 900L
    }
}
