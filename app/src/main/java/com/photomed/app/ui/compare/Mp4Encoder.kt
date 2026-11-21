package com.photomed.app.ui.compare

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Exportación de la animación como vídeo MP4 (H.264, sin bibliotecas: MediaCodec + MediaMuxer).
 * Misma línea de tiempo de ida y vuelta que el GIF ([CompareExport.timeline]): el vídeo dura un ciclo
 * y, repetido por el reproductor, enlaza sin salto. A [FPS] constantes, cada cuadro con el avance
 * exacto de su instante (movimiento continuo, sin los pasos del GIF).
 */
object Mp4Encoder {
    const val FPS = 30
    /** Ancho del vídeo (múltiplo de 16, como piden muchos codificadores). */
    const val WIDTH = 528

    /**
     * Estado (par, avance) de cada cuadro del vídeo a [fps]: muestreo continuo de la línea de tiempo
     * ([CompareExport.smoothStateAt]), sin los pasos del GIF; el ciclo dura lo mismo que el GIF.
     */
    fun frameStates(n: Int, timing: CompareExport.GifTiming, fps: Int = FPS): List<Pair<Int, Float>> {
        val total = CompareExport.timeline(n, timing).sumOf { it.ms }
        val count = ((total.toLong() * fps + 500) / 1000).toInt().coerceAtLeast(1)
        return List(count) { k -> CompareExport.smoothStateAt(k * 1000.0 / fps, n, timing) }
    }

    fun encode(images: List<Pair<Bitmap, String>>, mode: CompareExport.GifMode, timing: CompareExport.GifTiming, out: File,
               watermark: com.photomed.app.export.Watermark.Mark? = null) {
        val (w, h) = CompareExport.frameSize(images, WIDTH, 16)
        val scaled = images.map { (b, l) -> Bitmap.createScaledBitmap(b, w, h, true) to l }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val states = frameStates(images.size, timing)
        // cada estado distinto se pinta y convierte una vez (las fotos quietas se repiten)
        val cache = HashMap<Pair<Int, Float>, ByteArray>()
        fun frame(k: Int): ByteArray = cache.getOrPut(states[k]) {
            CompareExport.paintState(bmp, scaled, states[k].first, states[k].second, mode, watermark)
            toI420(IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }, w, h)
        }.also { if (states[k].second != 0f && states[k].second != 1f) cache.remove(states[k]) }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var next = 0; var inputDone = false; var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val pts = next * 1_000_000L / FPS
                        if (next >= states.size) {
                            codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val image = codec.getInputImage(ii) ?: error("El codificador no da imagen de entrada")
                            writeI420(frame(next), w, h, image)
                            codec.queueInputBuffer(ii, 0, w * h * 3 / 2, pts, 0)
                            next++
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } catch (e: Throwable) {
            out.delete(); throw e
        } finally {
            runCatching { codec.stop() }; codec.release()
            runCatching { if (started) muxer.stop() }; runCatching { muxer.release() }
        }
    }

    /** ARGB → YUV 4:2:0 planar (BT.601, rango limitado): Y, luego U y V submuestreados 2×2. */
    fun toI420(px: IntArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h * 3 / 2)
        for (i in px.indices) {
            val c = px[i]; val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            out[i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
        }
        val cw = w / 2; val ch = h / 2; val uo = w * h; val vo = uo + cw * ch
        for (y in 0 until ch) for (x in 0 until cw) {
            var r = 0; var g = 0; var b = 0
            for (dy in 0..1) for (dx in 0..1) {
                val c = px[(2 * y + dy) * w + 2 * x + dx]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            r /= 4; g /= 4; b /= 4
            out[uo + y * cw + x] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
            out[vo + y * cw + x] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
        }
        return out
    }

    /** Copia un cuadro I420 en la imagen de entrada del codificador respetando sus pasos de fila y píxel. */
    private fun writeI420(src: ByteArray, w: Int, h: Int, image: android.media.Image) {
        val planes = image.planes
        val offs = intArrayOf(0, w * h, w * h + (w / 2) * (h / 2))
        for (p in 0 until 3) {
            val pw = if (p == 0) w else w / 2; val ph = if (p == 0) h else h / 2
            val plane = planes[p]; val buf = plane.buffer
            val rs = plane.rowStride; val ps = plane.pixelStride
            for (y in 0 until ph) {
                val base = y * rs
                if (ps == 1) { buf.position(base); buf.put(src, offs[p] + y * pw, pw) }
                else for (x in 0 until pw) buf.put(base + x * ps, src[offs[p] + y * pw + x])
            }
        }
    }
}
