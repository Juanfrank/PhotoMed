package com.photomed.app.align

import com.photomed.app.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Puerta única para los modelos nativos (MediaPipe, ML Kit): de uno en uno en toda la app. En Comparar,
 * la alineación automática y el análisis de cada foto creaban a la vez varios detectores y
 * segmentadores nativos; eso agotaba la memoria o fallaba en código nativo y Android cerraba la app sin
 * pasar por el registro de errores de Java. No es reentrante: se usa solo en los puntos de entrada
 * (análisis de una foto, alineación, importación, relieve, línea del pelo al capturar).
 *
 * Con [priority] se pasa delante de los que esperan sin prioridad. La alineación de Comparar no la usa:
 * espera al análisis de arrugas de cada foto, porque usa sus surcos como puntos de correspondencia.
 */
object NativeGate {
    private val lock = ReentrantLock()
    private val free = lock.newCondition()
    private var busy = false
    private var priorityWaiting = 0

    private fun acquire(priority: Boolean, timeoutNs: Long? = null): Boolean {
        lock.lock()
        try {
            if (priority) priorityWaiting++
            try {
                var left = timeoutNs
                while (busy || (!priority && priorityWaiting > 0)) {
                    if (left == null) free.await() else { if (left <= 0L) return false; left = free.awaitNanos(left) }
                }
            } finally { if (priority) priorityWaiting-- }
            busy = true
            return true
        } finally { lock.unlock() }
    }

    private fun release() { lock.lock(); try { busy = false; free.signalAll() } finally { lock.unlock() } }

    /** Ejecuta [block] con la puerta tomada (espera su turno sin ocupar los hilos de cálculo). */
    suspend fun <T> run(what: String, priority: Boolean = false, block: suspend () -> T): T {
        runInterruptible(Dispatchers.IO) { acquire(priority) }
        CrashLog.crumb(what)
        try { return block() } finally { release(); CrashLog.crumb("fin: $what") }
    }

    /** Versión bloqueante (hilo de la cámara): si no hay turno en [timeoutMs], devuelve null sin ejecutar. */
    fun <T> tryRunBlocking(what: String, timeoutMs: Long, block: () -> T): T? {
        if (!acquire(false, TimeUnit.MILLISECONDS.toNanos(timeoutMs))) return null
        CrashLog.crumb(what)
        try { return block() } finally { release() }
    }
}
