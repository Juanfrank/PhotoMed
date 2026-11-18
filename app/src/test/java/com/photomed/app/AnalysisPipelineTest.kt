package com.photomed.app

import com.photomed.app.align.AnalysisPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Cruces de eventos simultáneos en el hilo de análisis: cambio de motor (ML Kit ↔ MediaPipe)
 * mientras llegan cuadros y referencias, y cierre de la pantalla en medio de todo.
 */
class AnalysisPipelineTest {
    private class FakeEngine(val id: Int, private val errors: AtomicInteger) : Closeable {
        @Volatile var closed = false
        val closes = AtomicInteger()
        fun use() { if (closed) errors.incrementAndGet(); Thread.sleep(0, 200_000) }
        override fun close() { if (closes.incrementAndGet() > 1) errors.incrementAndGet(); closed = true }
    }

    @Test
    fun swapsAnalysisAndShutdownUnderConcurrency() {
        repeat(20) { round ->
            val errors = AtomicInteger()
            val engines = java.util.Collections.synchronizedList(mutableListOf<FakeEngine>())
            val pipeline = AnalysisPipeline<FakeEngine>(Executors.newSingleThreadExecutor())
            fun newEngine() = FakeEngine(engines.size, errors).also { engines += it }
            pipeline.swap(newEngine())
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(4)
            val rnd = Random(round)
            // 1 hilo cambia de motor, 2 envían cuadros, 1 envía referencias
            pool.execute { start.await(); repeat(40) { pipeline.swap(newEngine()); Thread.sleep(rnd.nextLong(0, 2)) } }
            repeat(2) { pool.execute { start.await(); repeat(300) { pipeline.post { pipeline.withEngine { it.use() } } } } }
            pool.execute { start.await(); repeat(60) { pipeline.post { pipeline.withEngine { it.use() } } } }
            start.countDown()
            Thread.sleep(rnd.nextLong(5, 40))
            pipeline.shutdown() // la pantalla se cierra en medio
            pool.shutdown(); pool.awaitTermination(10, TimeUnit.SECONDS)
            Thread.sleep(50)
            // tras el cierre: nuevos eventos se ignoran sin excepciones
            pipeline.post { pipeline.withEngine { it.use() } }
            pipeline.swap(newEngine())
            Thread.sleep(50)
            assertEquals("uso tras cierre o doble cierre (ronda $round)", 0, errors.get())
            assertTrue("todos los motores cerrados", engines.all { it.closed })
        }
    }

    @Test
    fun analyzerAlwaysUsesCurrentEngine() {
        val errors = AtomicInteger()
        val p = AnalysisPipeline<FakeEngine>(Executors.newSingleThreadExecutor())
        val a = FakeEngine(1, errors); val b = FakeEngine(2, errors)
        p.swap(a)
        val seen = mutableListOf<Int>()
        val done = CountDownLatch(1)
        p.swap(b) // cambio de motor: el analizador creado antes debe usar b
        p.post { p.withEngine { seen += it.id }; done.countDown() }
        done.await(2, TimeUnit.SECONDS)
        assertEquals(listOf(2), seen)
        Thread.sleep(20)
        assertTrue(a.closed); assertEquals(0, errors.get())
        p.shutdown(); Thread.sleep(20); assertTrue(b.closed)
    }
}
