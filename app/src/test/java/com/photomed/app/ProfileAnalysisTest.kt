package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Perfil (muestreo de pilas) del análisis de una cara: dónde se va el tiempo de CPU. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ProfileAnalysisTest {
    /** En varios núcleos el análisis da exactamente lo mismo que en uno (mismas líneas y medidas). */
    @Test
    fun parallelGivesSameLinesAsSerial() {
        val cases = load()
        assumeTrue(cases.isNotEmpty())
        fun all() = cases.map { (mesh, img, labels) -> com.photomed.app.align.FaceCrop.annotate(mesh, img, { _, _ -> labels }) }
        com.photomed.app.align.Par.enabled = false
        val t0 = System.nanoTime(); val serial = all(); val ts = System.nanoTime() - t0
        com.photomed.app.align.Par.enabled = true
        val t1 = System.nanoTime(); val par = all(); val tp = System.nanoTime() - t1
        println("NÚCLEOS ${com.photomed.app.align.Par.threads}: serie ${ts / 1_000_000} ms, paralelo ${tp / 1_000_000} ms (${cases.size} caras)")
        org.junit.Assert.assertEquals(serial, par)
        org.junit.Assert.assertTrue(serial.sumOf { it.size } > 0)
    }

    private fun load(): List<Triple<List<Pt>, Bitmap, FaceLabels?>> {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText() ?: return emptyList()
        val o = JSONObject(json)
        return o.keys().asSequence().sorted().mapNotNull { k ->
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: return@mapNotNull null
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) }
            val labels = labBmp?.let { b -> val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                FaceLabels(b.width, b.height, ByteArray(px.size) { Color.red(px[it]).toByte() }) }
            Triple(mesh, img, labels)
        }.toList()
    }

    @Test
    fun profile() {
        assumeTrue(System.getProperty("pm.profile") != null || System.getenv("PM_PROFILE") != null)
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue(json != null)
        val o = JSONObject(json!!)
        val cases = o.keys().asSequence().sorted().mapNotNull { k ->
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: return@mapNotNull null
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) }
            val labels = labBmp?.let { b -> val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                FaceLabels(b.width, b.height, ByteArray(px.size) { Color.red(px[it]).toByte() }) }
            Triple(mesh, img, labels)
        }.toList()
        fun run() = cases.forEach { (mesh, img, labels) -> com.photomed.app.align.FaceCrop.annotate(mesh, img, { _, _ -> labels }) }
        run() // calentamiento
        val counts = HashMap<String, Int>(); val self = HashMap<String, Int>()
        var worker: Thread? = null
        val t0 = System.nanoTime()
        worker = Thread { repeat(2) { run() } }.also { it.start() }
        var samples = 0
        while (worker.isAlive) {
            val st = worker.stackTrace
            val ours = st.filter { it.className.startsWith("com.photomed") }.map { "${it.className.substringAfterLast('.')}.${it.methodName}" }.distinct()
            ours.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            ours.firstOrNull()?.let { self[it] = (self[it] ?: 0) + 1 }
            samples++; Thread.sleep(2)
        }
        val ms = (System.nanoTime() - t0) / 1e6 / (2 * cases.size)
        println("PERFIL: ${cases.size} caras, %.0f ms por cara, $samples muestras".format(ms))
        println("INCLUSIVO:"); counts.entries.sortedByDescending { it.value }.take(30).forEach { println("  ${it.value * 100 / samples}%  ${it.key}") }
        println("PROPIO:"); self.entries.sortedByDescending { it.value }.take(20).forEach { println("  ${it.value * 100 / samples}%  ${it.key}") }
    }
}
