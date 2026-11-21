package com.photomed.app.align

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.Future

/**
 * Cálculo en varios núcleos para el análisis de imagen (puro Kotlin, sin modelos nativos).
 *
 * - [map]: tareas independientes (p. ej. un rastreo de surco por candidato), con el resultado en el
 *   mismo orden que la entrada: el análisis da exactamente lo mismo que en un solo núcleo.
 * - [rows]: bucles por filas de una imagen (desenfoque, Hessiano, color): cada fila se escribe en su
 *   propio tramo del resultado, sin compartir nada entre hilos.
 *
 * Sin anidamiento: dentro de una tarea de este grupo de hilos, [map] y [rows] corren en el mismo hilo
 * (el nivel exterior ya ocupa los núcleos y así no se esperan unos a otros).
 */
object Par {
    /** Núcleos usados (se deja uno para la interfaz si hay más de dos). */
    val threads: Int = Runtime.getRuntime().availableProcessors().let { if (it > 2) it - 1 else it }.coerceIn(1, 8)

    private val pool: ForkJoinPool by lazy {
        ForkJoinPool(threads, { p ->
            object : ForkJoinWorkerThread(p) {}.apply { name = "photomed-calc-$poolIndex"; priority = Thread.NORM_PRIORITY - 1 }
        }, null, false)
    }

    private fun inPool() = Thread.currentThread().let { it is ForkJoinWorkerThread && it.name.startsWith("photomed-calc-") }
    private fun pool(): ForkJoinPool = pool

    /** Desactivar para comparar con el cálculo en un solo núcleo (pruebas). */
    @Volatile var enabled = true

    /** ¿Merece la pena repartir? (un solo núcleo, ya dentro del grupo o poco trabajo: no). */
    private fun serial(n: Int) = !enabled || threads <= 1 || n < 2 || inPool()

    /** [f] sobre cada elemento, en paralelo; resultados en el orden de [items]. */
    fun <T, R> map(items: List<T>, f: (T) -> R): List<R> {
        if (serial(items.size)) return items.map(f)
        val p = pool()
        val futures: List<Future<R>> = items.map { x -> p.submit(Callable { f(x) }) }
        return futures.map { await(it) }
    }

    /**
     * Filas 0 until [n] repartidas en tramos contiguos ([body] recibe desde, hasta exclusivo).
     * [minRows]: filas mínimas por tramo (por debajo no compensa repartir).
     */
    fun rows(n: Int, minRows: Int = 24, body: (Int, Int) -> Unit) {
        val parts = minOf(threads * 2, n / minRows.coerceAtLeast(1))
        if (parts < 2 || serial(parts)) { body(0, n); return }
        val p = pool()
        val futures = (0 until parts).map { k ->
            val a = n * k / parts; val b = n * (k + 1) / parts
            p.submit { body(a, b) }
        }
        futures.forEach { await(it) }
    }

    private fun <R> await(f: Future<R>): R = try { f.get() } catch (e: ExecutionException) { throw e.cause ?: e }
}
