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
            if (old != null && old !== engine) executor.execute { runCatching { old.close() } }
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

    fun shutdown() {
        synchronized(lock) {
            if (stopped) return
            stopped = true // los análisis aún encolados verán el pipeline detenido y no usarán el motor
            val last = current.getAndSet(null)
            executor.execute { last?.let { runCatching { it.close() } } }
            executor.shutdown()
        }
    }
}
