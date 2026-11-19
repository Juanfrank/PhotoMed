package com.photomed.app

import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.ModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** Descarga a petición del modelo opcional: verificación de la huella y archivo final solo si coincide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelStoreTest {
    @Test
    fun downloadVerifiesHash() = runBlocking {
        val body = ByteArray(200_000) { (it * 31).toByte() }
        // servidor HTTP mínimo (responde siempre con [body])
        val server = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!server.isClosed) runCatching {
                server.accept().use { sock ->
                    val r = sock.getInputStream().bufferedReader(); while (r.readLine()?.isNotEmpty() == true) {}
                    val out = sock.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body); out.flush()
                }
            }
        }.apply { isDaemon = true }.start()
        try {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val url = "http://127.0.0.1:${server.localPort}/m.tflite"
            val good = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
            val dst = File(ctx.filesDir, "models/test.tflite")
            var last = 0f
            ModelStore.download(ctx, url, good, dst) { last = it }
            assertTrue(dst.isFile && dst.length() == body.size.toLong())
            assertTrue(last > 0.99f)
            // huella distinta: no se guarda nada
            val bad = File(ctx.filesDir, "models/bad.tflite")
            val r = runCatching { ModelStore.download(ctx, url, "0".repeat(64), bad) }
            assertTrue(r.isFailure)
            assertFalse(bad.exists())
            assertFalse(File(bad.parentFile, "bad.tflite.part").exists())
        } finally { server.close() }
    }
}
