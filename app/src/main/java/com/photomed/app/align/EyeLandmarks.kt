package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.hypot

/**
 * Puntos del ojo en un recorte del ojo con el modelo «iris landmark» de MediaPipe (Apache 2.0; assets/
 * iris_landmark.tflite): 71 puntos (párpados, tres halos y la ceja) y 5 del iris (centro y cuatro del borde). El
 * modelo está hecho para un recorte cuadrado de 64 px centrado en el ojo, de ≈ 2,8 anchos de ojo de lado, del ojo
 * derecho de la persona (izquierda de la imagen); el otro ojo se pasa en espejo.
 *
 * En los primeros planos del ojo, la malla facial (foto + espejo, [EyeComposite]) pierde precisión cuando el ojo
 * llena el encuadre (no ve la ceja ni la sien): con el recorte que da la malla, este modelo mejora el contorno de
 * los párpados (error mediano frente a la malla de la foto entera, en anchos de ojo: encuadre normal 0,046 → 0,042;
 * primer plano 0,071 → 0,048; muy cerrado 0,108 → 0,063). Sin malla, el ojo se busca con una rejilla de recortes.
 */
class EyeLandmarks(context: Context) : Closeable {
    private val interpreter: Interpreter
    private val input = ByteBuffer.allocateDirect(4 * 64 * 64 * 3).order(ByteOrder.nativeOrder())
    private val outContour = Array(1) { FloatArray(213) }
    private val outIris = Array(1) { FloatArray(15) }

    init {
        val fd = context.assets.openFd(MODEL)
        val buf = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buf, Interpreter.Options().setNumThreads(2))
    }

    /** Resultado en px de la imagen: 71 puntos del contorno ([CONTOUR_MESH] da su índice en la malla) y 5 del iris. */
    class Eye(val contour: List<Pt>, val iris: List<Pt>) {
        val width: Float get() = hypot(contour[0].x - contour[8].x, contour[0].y - contour[8].y)
        val center: Pt get() = Pt((contour[0].x + contour[8].x) / 2, (contour[0].y + contour[8].y) / 2)
    }

    /** El ojo en el recorte cuadrado de lado [size] centrado en ([cx], [cy]) (px de [bmp]); [flip]: ojo izquierdo de la persona. */
    fun predict(bmp: Bitmap, cx: Float, cy: Float, size: Float, flip: Boolean): Eye {
        val s = 64f / size
        // recorte con promedio por área (4×4 muestras por píxel de salida): sin el aliasing de reducir una foto de
        // varios megapíxeles a 64 px de golpe
        val px = IntArray(64 * 64)
        val step = size / 64f; val x0 = cx - size / 2; val y0 = cy - size / 2
        val bw = bmp.width; val bh = bmp.height
        for (j in 0 until 64) for (i in 0 until 64) {
            val si = if (flip) 63 - i else i
            var r = 0; var g = 0; var b = 0
            for (sy in 0 until 4) for (sx in 0 until 4) {
                val x = (x0 + (si + (sx + 0.5f) / 4f) * step).toInt().coerceIn(0, bw - 1)
                val y = (y0 + (j + (sy + 0.5f) / 4f) * step).toInt().coerceIn(0, bh - 1)
                val c = bmp.getPixel(x, y); r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            px[j * 64 + i] = (0xFF shl 24) or ((r / 16) shl 16) or ((g / 16) shl 8) or (b / 16)
        }
        input.rewind()
        for (c in px) { input.putFloat(((c shr 16) and 0xFF) / 255f); input.putFloat(((c shr 8) and 0xFF) / 255f); input.putFloat((c and 0xFF) / 255f) }
        input.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(input), mapOf(0 to outContour, 1 to outIris))
        fun back(x: Float, y: Float): Pt { val xx = if (flip) 64f - x else x; return Pt(xx / s + cx - size / 2, y / s + cy - size / 2) }
        val contour = (0 until 71).map { back(outContour[0][3 * it], outContour[0][3 * it + 1]) }
        val iris = (0 until 5).map { back(outIris[0][3 * it], outIris[0][3 * it + 1]) }
        return Eye(contour, iris)
    }

    /** Dos pasadas centradas en el ojo que da [corners] (comisuras, px): como en MediaPipe, el recorte sigue al ojo. */
    fun refine(bmp: Bitmap, a: Pt, b: Pt, flip: Boolean): Eye {
        var c = Pt((a.x + b.x) / 2, (a.y + b.y) / 2); var w = hypot(a.x - b.x, a.y - b.y)
        var e = predict(bmp, c.x, c.y, SCALE * w, flip)
        repeat(1) { c = e.center; w = e.width; e = predict(bmp, c.x, c.y, SCALE * w, flip) }
        return e
    }

    /**
     * Sin malla: rejilla de recortes (3 tamaños × 3 × 3 centros); de cada uno, una pasada centrada en lo predicho, y
     * se queda el más coherente (la segunda predicción coincide con la primera en centro y ancho). null si nada encaja.
     */
    fun search(bmp: Bitmap, flip: Boolean): Eye? {
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat(); val side = minOf(w, h)
        var best: Pair<Float, Eye>? = null
        for (fs in floatArrayOf(0.45f, 0.65f, 0.9f)) for (gy in floatArrayOf(0.35f, 0.5f, 0.65f)) for (gx in floatArrayOf(0.3f, 0.5f, 0.7f)) {
            val e1 = predict(bmp, gx * w, gy * h, fs * side, flip)
            val e2 = predict(bmp, e1.center.x, e1.center.y, SCALE * e1.width, flip)
            var cost = hypot(e2.center.x - e1.center.x, e2.center.y - e1.center.y) / e2.width.coerceAtLeast(1f) + kotlin.math.abs(e2.width / e1.width.coerceAtLeast(1f) - 1f)
            if (e2.width < 0.1f * side) cost += 1f
            if (best == null || cost < best.first) best = cost to e2
        }
        val (cost, e) = best ?: return null
        if (cost > MAX_SEARCH_COST) return null
        return refine(bmp, e.contour[0], e.contour[8], flip)
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL = "iris_landmark.tflite"
        /** Lado del recorte en anchos de ojo (la cota de mejor precisión en la validación: 2,8). */
        const val SCALE = 2.8f
        /** Incoherencia máxima de la búsqueda sin malla (centro y ancho de dos pasadas). */
        const val MAX_SEARCH_COST = 0.35f

        /** Índice en la malla de cada uno de los 71 puntos: ojo derecho de la persona (izquierda de la imagen). */
        val CONTOUR_MESH_R = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 246, 161, 160, 159, 158, 157, 173,
            130, 25, 110, 24, 23, 22, 26, 112, 243, 247, 30, 29, 27, 28, 56, 190,
            226, 31, 228, 229, 230, 231, 232, 233, 244, 113, 225, 224, 223, 222, 221, 189,
            35, 124, 46, 53, 52, 65, 143, 111, 117, 118, 119, 120, 121, 128, 245, 156, 70, 63, 105, 66, 107, 55, 193)
        /** Lo mismo para el ojo izquierdo de la persona (derecha de la imagen). */
        val CONTOUR_MESH_L = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 466, 388, 387, 386, 385, 384, 398,
            359, 255, 339, 254, 253, 252, 256, 341, 463, 467, 260, 259, 257, 258, 286, 414,
            446, 261, 448, 449, 450, 451, 452, 453, 464, 342, 445, 444, 443, 442, 441, 413,
            265, 353, 276, 283, 282, 295, 372, 340, 346, 347, 348, 349, 350, 357, 465, 383, 300, 293, 334, 296, 336, 285, 417)

        /**
         * La malla (unidades del ancho de [bmp]) con los párpados (16 puntos) y el iris de los dos ojos del modelo de
         * ojo, en recortes que da la propia malla. Las cejas y los halos se quedan como estaban (las cejas tienen su
         * propio ajuste, [BrowRefine]).
         */
        fun refineMesh(model: EyeLandmarks, bmp: Bitmap, mesh: List<Pt>): List<Pt> {
            if (mesh.size < 478) return mesh
            val w = bmp.width.toFloat()
            val out = mesh.toMutableList()
            for ((map, iris, flip) in listOf(Triple(CONTOUR_MESH_R, 468, false), Triple(CONTOUR_MESH_L, 473, true))) {
                val a = mesh[map[0]]; val b = mesh[map[8]]
                if (hypot(a.x - b.x, a.y - b.y) * w < 8f) continue
                val e = model.refine(bmp, Pt(a.x * w, a.y * w), Pt(b.x * w, b.y * w), flip)
                for (k in 0 until 16) out[map[k]] = Pt(e.contour[k].x / w, e.contour[k].y / w)
                for (k in 0 until 5) out[iris + k] = Pt(e.iris[k].x / w, e.iris[k].y / w)
            }
            return out
        }
    }
}
