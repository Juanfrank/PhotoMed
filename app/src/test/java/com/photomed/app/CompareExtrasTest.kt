package com.photomed.app

import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import com.photomed.app.ui.compare.GifEncoder
import com.photomed.app.ui.compare.GridOrder
import com.photomed.app.ui.compare.displayOrder
import com.photomed.app.ui.compare.footprint
import com.photomed.app.ui.compare.moved
import com.photomed.app.ui.compare.overlapCrop
import com.photomed.app.ui.compare.stampLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

/** Comparar: recorte según la alineación, GIF, fechas con hora y orden de la cuadrícula. */
class CompareExtrasTest {
    @Test
    fun cropRemovesBlackBordersOfAllAlignedPhotos() {
        assertNull("sin alineación no hay recorte", overlapCrop(listOf(footprint(null, 0f))))
        val ts = listOf(Similarity(1.06f, Math.toRadians(3.0).toFloat(), 0.03f, -0.02f), Similarity(0.95f, Math.toRadians(-2.0).toFloat(), -0.02f, 0.04f))
        val polys = ts.map { footprint(it, 0f) } + listOf(footprint(Similarity.IDENTITY, 2f))
        val crop = overlapCrop(polys)
        assertNotNull(crop); crop!!
        // el encuadre recortado (todo el 3:4 tras el recorte) cae dentro de cada foto alineada
        val inv = crop.inverse()
        for (corner in listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 4f / 3f), Pt(0f, 4f / 3f))) {
            val q = inv.apply(corner)
            for (poly in polys) assertTrue("esquina $q fuera de una foto", insideConvex(poly, q, 1e-3f))
        }
        assertTrue("escala moderada: ${crop.scale}", crop.scale in 1.02f..1.4f)
        println("RECORTE escala ×%.3f".format(crop.scale))
    }

    private fun insideConvex(poly: List<Pt>, p: Pt, eps: Float): Boolean {
        var sign = 0
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val cr = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val s = if (cr > eps) 1 else if (cr < -eps) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
        }
        return true
    }

    @Test
    fun timeOnlyForPhotosOnTheSameDay() {
        fun at(d: Int, h: Int, m: Int) = Calendar.getInstance().apply { set(2026, 10, d, h, m, 0) }.timeInMillis
        val l = stampLabels(listOf(at(3, 9, 15), at(10, 10, 0), at(10, 16, 42), at(20, 11, 5)))
        assertTrue(l[0], !l[0].contains(":"))
        assertTrue(l[1], l[1].endsWith("10:00"))
        assertTrue(l[2], l[2].endsWith("16:42"))
        assertTrue(l[3], !l[3].contains(":"))
    }

    @Test
    fun gridOrderAndManualMove() {
        val seq = listOf("a", "b", "c", "d")
        assertEquals(seq, displayOrder(seq, GridOrder.OLDEST_FIRST, emptyList()))
        assertEquals(seq.reversed(), displayOrder(seq, GridOrder.NEWEST_FIRST, emptyList()))
        // manual: arrastrar "d" a la primera posición; las fotos nuevas van al final
        val m = seq.moved(3, 0)
        assertEquals(listOf("d", "a", "b", "c"), m)
        assertEquals(listOf("d", "a", "c", "e"), displayOrder(listOf("a", "c", "d", "e"), GridOrder.MANUAL, m))
    }

    @Test
    fun gifDecodesWithAllFramesAndColors() {
        val w = 64; val h = 48
        val enc = GifEncoder(w, h)
        val colors = intArrayOf(0xFFD2A08C.toInt(), 0xFF3C5A96.toInt(), 0xFF20B040.toInt())
        // 3 cuadros: degradado de piel + bloque de color (pone a prueba la paleta y el LZW > 4096 códigos)
        val frames = colors.map { c -> IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x in 10..30 && y in 10..30) c else (0xFF shl 24) or ((150 + x) shl 16) or ((100 + y) shl 8) or (80 + (x * y) % 60)
        } }
        frames.forEach { enc.add(it, 200) }
        val bytes = enc.toByteArray()
        val decoded = decodeGif(bytes)
        assertEquals(3, decoded.size)
        for (k in 0 until 3) {
            val img = decoded[k]
            var err = 0.0
            for (i in 0 until w * h) {
                val a = frames[k][i]; val b = img[i]
                for (sh in intArrayOf(16, 8, 0)) err += abs(((a shr sh) and 0xFF) - ((b shr sh) and 0xFF))
            }
            val mean = err / (w * h * 3)
            println("GIF cuadro $k: error medio %.2f niveles".format(mean))
            assertTrue("cuadro $k: error $mean", mean < 6.0)
            val c = img[20 * w + 20]
            assertTrue("color del bloque", intArrayOf(16, 8, 0).all { sh -> abs(((c shr sh) and 0xFF) - ((colors[k] shr sh) and 0xFF)) <= 12 })
        }
    }

    @Test
    fun gifLargeNoisyFrameRoundTrip() {
        // ruido: llena la tabla LZW (4096 códigos) varias veces → códigos de borrado
        val w = 320; val h = 240; val rnd = java.util.Random(3)
        // colores distintos también en la caché RGB 5-5-5 del codificador (múltiplos de 8)
        val pal = (0 until 32768).shuffled(rnd).take(64).map { k -> (0xFF shl 24) or (((k shr 10) and 31) * 8 shl 16) or (((k shr 5) and 31) * 8 shl 8) or ((k and 31) * 8) }.toIntArray()
        val px = IntArray(w * h) { pal[rnd.nextInt(64)] }
        val enc = GifEncoder(w, h); enc.add(px, 100)
        val bytes = enc.toByteArray()
        java.io.File("build/reports").mkdirs(); java.io.File("build/reports/gif_noise.gif").writeBytes(bytes)
        val d = decodeGif(bytes).single()
        assertTrue("todos los píxeles idénticos", px.indices.all { (px[it] and 0xFFFFFF) == (d[it] and 0xFFFFFF) })
    }

    @Test
    fun previewTimelineMatchesGifFrames() {
        // la vista previa usa gifStateAt; el archivo, la lista de cuadros: deben coincidir en todo el ciclo
        for (timing in listOf(com.photomed.app.ui.compare.CompareExport.GifTiming(1200, 700),
                              com.photomed.app.ui.compare.CompareExport.GifTiming(500, 0),
                              com.photomed.app.ui.compare.CompareExport.GifTiming(2000, 2500))) {
            val n = 3
            val frames = ArrayList<Triple<Int, Float, Int>>()   // foto, avance, duración (como gifFrames)
            for (i in 0 until n) {
                frames += Triple(i, 0f, timing.holdMs)
                for (k in 1..timing.steps) frames += Triple(i, k / (timing.steps + 1f), timing.stepMs)
            }
            assertEquals(timing.cycleMs(n), frames.sumOf { it.third })
            var t = 0L
            for ((i, f, d) in frames) {
                val (pi, pf) = com.photomed.app.ui.compare.CompareExport.gifStateAt(t + d / 2, n, timing)
                assertEquals(i, pi); assertEquals(f, pf, 1e-4f)
                t += d
            }
        }
    }

    /** Descodificador GIF mínimo (paleta global, LZW) para verificar el codificador. */
    private fun decodeGif(b: ByteArray): List<IntArray> {
        var p = 6
        fun u8() = b[p++].toInt() and 0xFF
        fun u16() = u8() or (u8() shl 8)
        val w = u16(); val h = u16(); val flags = u8(); p += 2
        val pal = IntArray(if (flags and 0x80 != 0) 2 shl (flags and 7) else 0) { (u8() shl 16) or (u8() shl 8) or u8() }
        val out = ArrayList<IntArray>()
        while (p < b.size) {
            when (u8()) {
                0x21 -> { u8(); while (true) { val n = u8(); if (n == 0) break; p += n } }
                0x2C -> {
                    p += 8; u8()
                    val minCode = u8()
                    val data = java.io.ByteArrayOutputStream()
                    while (true) { val n = u8(); if (n == 0) break; data.write(b, p, n); p += n }
                    val bits = data.toByteArray()
                    val clear = 1 shl minCode; val eoi = clear + 1
                    var size = minCode + 1; var bitPos = 0
                    val prefix = IntArray(4096); val suffix = IntArray(4096); val len = IntArray(4096)
                    for (i in 0 until clear) { suffix[i] = i; len[i] = 1 }
                    var next = eoi + 1; var prev = -1
                    val px = ArrayList<Int>(w * h)
                    fun read(): Int { var v = 0; for (i in 0 until size) { val bit = (bits[(bitPos + i) / 8].toInt() shr ((bitPos + i) % 8)) and 1; v = v or (bit shl i) }; bitPos += size; return v }
                    fun seq(c: Int): IntArray { val r = IntArray(len[c]); var x = c; for (i in len[c] - 1 downTo 0) { r[i] = suffix[x]; x = prefix[x] }; return r }
                    while (true) {
                        val c = read()
                        if (c == clear) { size = minCode + 1; next = eoi + 1; prev = -1; continue }
                        if (c == eoi) break
                        val s = if (c < next) seq(c) else seq(prev).let { it + it[0] }
                        s.forEach { px += it }
                        if (prev >= 0 && next < 4096) { prefix[next] = prev; suffix[next] = s[0]; len[next] = len[prev] + 1; next++ }
                        if (next == (1 shl size) && size < 12) size++
                        prev = c
                    }
                    out += IntArray(w * h) { pal[px[it]] }
                }
                0x3B -> break
            }
        }
        return out
    }
}
