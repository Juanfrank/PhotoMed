package com.photomed.app.ui.compare

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Codificador GIF89a animado (sin bibliotecas): paleta global de 256 colores por corte de la
 * mediana sobre una muestra de todos los cuadros, asignación al color más cercano (con caché
 * RGB 5-5-5) y compresión LZW de códigos variables (como el codificador de referencia de GIF).
 * Bucle infinito (extensión NETSCAPE2.0).
 */
class GifEncoder(private val width: Int, private val height: Int) {
    private val frames = ArrayList<Pair<IntArray, Int>>()

    /** Cuadro ARGB ([width]×[height]) con su duración en milisegundos. */
    fun add(argb: IntArray, delayMs: Int) {
        require(argb.size == width * height)
        frames += argb to delayMs
    }

    fun write(out: OutputStream) {
        require(frames.isNotEmpty())
        val palette = medianCut(sample(), 256)
        val cache = IntArray(1 shl 15) { -1 }
        fun index(c: Int): Int {
            val key = ((c shr 9) and 0x7C00) or ((c shr 6) and 0x3E0) or ((c shr 3) and 0x1F)
            val hit = cache[key]; if (hit >= 0) return hit
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            var best = 0; var bd = Int.MAX_VALUE
            for (i in palette.indices) {
                val p = palette[i]
                val dr = r - ((p shr 16) and 0xFF); val dg = g - ((p shr 8) and 0xFF); val db = b - (p and 0xFF)
                val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
                if (d < bd) { bd = d; best = i }
            }
            cache[key] = best; return best
        }
        out.write("GIF89a".toByteArray())
        le16(out, width); le16(out, height)
        out.write(0xF7); out.write(0); out.write(0)              // tabla global de 256 colores
        for (i in 0 until 256) {
            val p = palette.getOrElse(i) { 0 }
            out.write((p shr 16) and 0xFF); out.write((p shr 8) and 0xFF); out.write(p and 0xFF)
        }
        // bucle infinito
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11)); out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(3, 1, 0, 0, 0))
        for ((px, delay) in frames) {
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x04)) // control gráfico: no desechar
            le16(out, (delay + 5) / 10); out.write(0); out.write(0)
            out.write(0x2C); le16(out, 0); le16(out, 0); le16(out, width); le16(out, height); out.write(0)
            val idx = ByteArray(px.size) { index(px[it]).toByte() }
            lzw(idx, out)
        }
        out.write(0x3B)
    }

    fun toByteArray(): ByteArray = ByteArrayOutputStream().also { write(it) }.toByteArray()

    private fun sample(): IntArray {
        val total = frames.size.toLong() * width * height
        val step = maxOf(1L, total / 120_000L).toInt()
        val out = ArrayList<Int>()
        var k = 0
        for ((px, _) in frames) for (i in px.indices) { if (k++ % step == 0) out += px[i] and 0xFFFFFF }
        return out.toIntArray()
    }

    private fun le16(out: OutputStream, v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    companion object {
        /** Paleta por corte de la mediana: divide la caja de mayor rango en su mediana hasta [n] cajas. */
        fun medianCut(colors: IntArray, n: Int): IntArray {
            if (colors.isEmpty()) return intArrayOf(0)
            val boxes = ArrayList<IntArray>(); boxes += colors
            fun range(b: IntArray, sh: Int): Int { var lo = 255; var hi = 0; for (c in b) { val v = (c shr sh) and 0xFF; if (v < lo) lo = v; if (v > hi) hi = v }; return hi - lo }
            while (boxes.size < n) {
                var bi = -1; var br = 0; var bsh = 0
                for ((i, b) in boxes.withIndex()) {
                    if (b.size < 2) continue
                    for (sh in intArrayOf(16, 8, 0)) { val r = range(b, sh) * (if (sh == 8) 4 else 3); if (r > br) { br = r; bi = i; bsh = sh } }
                }
                if (bi < 0 || br == 0) break
                val b = boxes.removeAt(bi)
                val s = b.sortedBy { (it shr bsh) and 0xFF }.toIntArray()
                // corte en el cambio de valor más cercano a la mediana (cada corte separa colores distintos)
                fun v(i: Int) = (s[i] shr bsh) and 0xFF
                var cut = s.size / 2
                while (cut < s.size && v(cut) == v(cut - 1)) cut++
                if (cut == s.size) { cut = s.size / 2; while (cut > 1 && v(cut) == v(cut - 1)) cut-- }
                boxes += s.copyOfRange(0, cut); boxes += s.copyOfRange(cut, s.size)
            }
            return IntArray(boxes.size) { i ->
                val b = boxes[i]; var r = 0L; var g = 0L; var bl = 0L
                for (c in b) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF }
                (((r / b.size).toInt()) shl 16) or (((g / b.size).toInt()) shl 8) or (bl / b.size).toInt()
            }
        }

        /** LZW de GIF (tamaño mínimo de código 8), en sub-bloques de 255 bytes. */
        fun lzw(indices: ByteArray, out: OutputStream) {
            val minCode = 8
            val clear = 1 shl minCode; val eoi = clear + 1
            out.write(minCode)
            val block = ByteArray(255); var blen = 0
            var acc = 0L; var nbits = 0
            fun flushByte(b: Int) { block[blen++] = b.toByte(); if (blen == 255) { out.write(255); out.write(block, 0, 255); blen = 0 } }
            var codeSize = minCode + 1
            var nextCode = eoi + 1
            val dict = HashMap<Int, Int>(8192)
            fun emit(code: Int) {
                acc = acc or (code.toLong() shl nbits); nbits += codeSize
                while (nbits >= 8) { flushByte((acc and 0xFF).toInt()); acc = acc ushr 8; nbits -= 8 }
                // como el codificador de referencia: el tamaño sube cuando el próximo código no cabe
                if (nextCode > (1 shl codeSize) - 1 && codeSize < 12) codeSize++
            }
            emit(clear)
            if (indices.isNotEmpty()) {
                var prefix = indices[0].toInt() and 0xFF
                for (i in 1 until indices.size) {
                    val k = indices[i].toInt() and 0xFF
                    val key = (prefix shl 8) or k
                    val code = dict[key]
                    if (code != null) { prefix = code; continue }
                    emit(prefix)
                    if (nextCode < 4096) { dict[key] = nextCode++ }
                    else {
                        // tabla llena: código de borrado y se reinicia
                        nextCode = eoi + 1
                        emit(clear)
                        dict.clear(); codeSize = minCode + 1
                    }
                    prefix = k
                }
                emit(prefix)
            }
            emit(eoi)
            if (nbits > 0) flushByte((acc and 0xFF).toInt())
            if (blen > 0) { out.write(blen); out.write(block, 0, blen) }
            out.write(0)
        }
    }
}
