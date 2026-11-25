package com.photomed.app.align

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

/**
 * Hilo de análisis en serie con un motor intercambiable (p. ej. al cambiar ML Kit ↔ MediaPipe).
 *
 * Garantías (probadas en `AnalysisPipelineTest` con hilos concurrentes):
 * - el analizador de cuadros siempre usa el motor **actual** (no el que existía al crearlo);
 * - un motor reemplazado se cierra en el mismo hilo serie, después de cualquier análisis en curso:
 *   nunca se usa un motor cerrado ni se cierra dos veces;
 * - tras [shutdown] no se envían tareas (sin RejectedExecutionException) y el motor se cierra.
 */
class AnalysisPipeline<M : Closeable>(private val executor: ExecutorService) {
    private val current = AtomicReference<M?>(null)
    @Volatile private var stopped = false

    private val lock = Any()

    /** Reemplaza el motor; el anterior se cierra en el hilo de análisis (tras los análisis en curso). */
    fun swap(engine: M) {
        synchronized(lock) {
            if (stopped) { runCatching { engine.close() }; return } // nunca se usó
            val old = current.getAndSet(engine)
            if (old != null && old !== engine) executor.execute { closeGated(old) }
        }
    }

    /** Ejecuta en el hilo de análisis; se descarta si el pipeline está detenido. */
    fun post(block: () -> Unit) {
        if (stopped) return
        try {
            executor.execute { if (!stopped) block() }
        } catch (_: RejectedExecutionException) {
        }
    }

    /** Usar SOLO desde el hilo de análisis: ejecuta con el motor actual. */
    fun <R> withEngine(block: (M) -> R): R? = if (stopped) null else current.get()?.let(block)

    /**
     * Cierra un motor con la puerta de los modelos nativos: liberar MediaPipe o ML Kit mientras otro modelo
     * corre era la misma concurrencia que daba cierres nativos. Si la puerta sigue ocupada tras la espera, se
     * cierra igualmente (fuga peor que esperar sin fin) y queda en el registro.
     */
    private fun closeGated(m: M) {
        NativeGate.tryRunBlocking("cerrar el motor en vivo", CLOSE_WAIT_MS, crumb = false) { runCatching { m.close() } }
            ?: run { com.photomed.app.CrashLog.crumb("motor en vivo cerrado sin la puerta (ocupada)"); runCatching { m.close() } }
    }

    fun shutdown() {
        synchronized(lock) {
            if (stopped) return
            stopped = true // los análisis aún encolados verán el pipeline detenido y no usarán el motor
            val last = current.getAndSet(null)
            executor.execute { last?.let { closeGated(it) } }
            executor.shutdown()
        }
    }

    companion object { const val CLOSE_WAIT_MS = 30_000L }
}
