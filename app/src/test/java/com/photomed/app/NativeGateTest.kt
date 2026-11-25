package com.photomed.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.NativeGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Modelos nativos de uno en uno (Comparar: alineación + análisis de cada foto) y migas en el registro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeGateTest {
    @Test fun neverTwoNativeModelsAtOnce() = runBlocking {
        val now = AtomicInteger(0); val max = AtomicInteger(0)
        (0 until 6).map { i ->
            async(Dispatchers.Default) {
                NativeGate.run("prueba $i") { val n = now.incrementAndGet(); max.accumulateAndGet(n) { a, b -> maxOf(a, b) }; delay(20); now.decrementAndGet() }
            }
        }.awaitAll()
        assertEquals(1, max.get())
        // con la puerta tomada, la versión de la cámara no espera más de lo indicado
        val held = async(Dispatchers.Default) { NativeGate.run("larga") { delay(400) } }
        delay(50)
        assertNull(NativeGate.tryRunBlocking("cámara", 50) { 1 })
        held.await()
        assertEquals(2, NativeGate.tryRunBlocking("cámara", 50) { 2 })
    }

    /** Cámara: el cuadro en vivo se salta mientras se analiza una foto (no corren dos modelos a la vez). */
    @Test fun liveFramesSkippedWhileAPhotoIsAnalyzed() = runBlocking {
        val started = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(1)
        val photo = async(Dispatchers.Default) {
            NativeGate.run("análisis de una foto (prueba)") { started.countDown(); done.await(); 1 }
        }
        started.await()
        assertNull(NativeGate.tryRunBlocking("análisis en vivo", 0, crumb = false) { 2 })
        done.countDown(); photo.await()
        assertEquals(3, NativeGate.tryRunBlocking("análisis en vivo", 0, crumb = false) { 3 })
    }

    @Test fun crashLogIncludesLastActions() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        CrashLog.install(ctx)
        CrashLog.crumb("alineación automática (rostro_frontal)")
        val f = CrashLog.write("Cierre de la app", RuntimeException("x"), fatal = true)!!
        assertTrue(f.readText().contains("Últimas acciones:"))
        assertTrue(f.readText().contains("alineación automática"))
        CrashLog.checkExitReasons(ctx)   // sin cierres registrados por el sistema: no falla
    }
}
