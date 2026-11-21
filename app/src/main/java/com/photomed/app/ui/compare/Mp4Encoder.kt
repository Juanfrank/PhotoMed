package com.photomed.app.ui.compare

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Exportación de la animación como vídeo MP4 (H.264, sin bibliotecas: MediaCodec + MediaMuxer).
 * Mismo guion de ida y vuelta que el GIF ([CompareExport.gifSchedule]): el vídeo dura un ciclo y,
 * repetido por el reproductor, enlaza sin salto. Cuadros constantes a [FPS] (las fotos quietas se
 * repiten; cada cuadro distinto se convierte a YUV una sola vez).
 */
object Mp4Encoder {
    const val FPS = 30
    /** Ancho del vídeo (múltiplo de 16, como piden muchos codificadores). */
    const val WIDTH = 528

    /** Cuadros del vídeo: índice del paso del guion para cada cuadro a [fps] (muestreo en el centro). */
    fun frameIndices(schedule: List<CompareExport.GifStep>, fps: Int = FPS): IntArray {
        val total = schedule.sumOf { it.ms }
        val n = ((total * fps + 500) / 1000).coerceAtLeast(1)
        val ends = IntArray(schedule.size); var acc = 0
        schedule.forEachIndexed { i, s -> acc += s.ms; ends[i] = acc }
        var j = 0
        return IntArray(n) { k ->
            val t = ((k + 0.5) * 1000 / fps).toInt()
            while (j < ends.size - 1 && t >= ends[j]) j++
            j
        }
    }

    fun encode(images: List<Pair<Bitmap, String>>, mode: CompareExport.GifMode, timing: CompareExport.GifTiming, out: File,
               watermark: com.photomed.app.export.Watermark.Mark? = null) {
        val (w, h) = CompareExport.frameSize(images, WIDTH, 16)
        val schedule = CompareExport.gifSchedule(images.size, timing)
        val yuv = ArrayList<ByteArray>()
        CompareExport.renderFrames(images, mode, timing, w, h, watermark) { px, _ -> yuv += toI420(px, w, h) }
        val order = frameIndices(schedule)

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
                        if (next >= order.size) {
                            codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val image = codec.getInputImage(ii) ?: error("El codificador no da imagen de entrada")
                            writeI420(yuv[order[next]], w, h, image)
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
