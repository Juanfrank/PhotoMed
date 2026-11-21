package com.photomed.app.align

import com.photomed.app.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Puerta única para los modelos nativos (MediaPipe, ML Kit): de uno en uno en toda la app. En Comparar,
 * la alineación automática y el análisis de cada foto creaban a la vez varios detectores y
 * segmentadores nativos; eso agotaba la memoria o fallaba en código nativo y Android cerraba la app sin
 * pasar por el registro de errores de Java. No es reentrante: se usa solo en los puntos de entrada
 * (análisis de una foto, alineación, importación, relieve, línea del pelo al capturar).
 */
object NativeGate {
    private val sem = Semaphore(1, true)

    /** Ejecuta [block] con la puerta tomada (espera su turno sin ocupar los hilos de cálculo). */
    suspend fun <T> run(what: String, block: suspend () -> T): T {
        runInterruptible(Dispatchers.IO) { sem.acquire() }
        CrashLog.crumb(what)
        try { return block() } finally { sem.release(); CrashLog.crumb("fin: $what") }
    }

    /** Versión bloqueante (hilo de la cámara): si no hay turno en [timeoutMs], devuelve null sin ejecutar. */
    fun <T> tryRunBlocking(what: String, timeoutMs: Long, block: () -> T): T? {
        if (!sem.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) return null
        CrashLog.crumb(what)
        try { return block() } finally { sem.release() }
    }
}
