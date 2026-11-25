package com.photomed.app.align

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Primer plano de un ojo analizado con la malla facial. En un primer plano no hay cara que detectar (MediaPipe
 * encontraba malla en 5 de 52), así que la foto se junta con su espejo por el lado del puente nasal: queda una
 * «cara» superior simétrica (dos ojos, dos cejas, nariz) y debajo se continúa con el color de la mejilla.
 * Sobre ella se ejecuta el mismo análisis que en el rostro y se conservan solo las líneas del ojo real (la
 * sección del ojo de la malla: contorno, iris, ceja, patas de gallo, ojeras, bolsas…), llevadas a la foto.
 *
 * Validado con 26 recortes de ojo de caras con malla conocida: malla en 23 (umbral 0,5 de la app); error medio
 * del contorno del ojo 5,5 % del ancho del ojo, ceja 10,8 %, iris 9,4 % (`tools/eval`, prototipo en Python).
 *
 * Coordenadas de las líneas: x del ancho, y en unidades del ancho (de la compuesta o de la foto).
 */
object EyeComposite {
    /** Alto del relleno inferior (fracción del alto de la foto). */
    const val PAD = 0.9f

    /** [mirrored]: ojo izquierdo (puente nasal a la izquierda de la foto): la compuesta es espejo | foto. */
    class Layout(val w: Int, val h: Int, val mirrored: Boolean) {
        val width get() = 2 * w
        val height get() = h + (h * PAD).toInt()
        /** Desplazamiento de la foto real en la compuesta (px). */
        val offset get() = if (mirrored) w else 0

        /** Punto de la compuesta → foto (unidades del ancho de cada una). */
        fun toPhoto(p: Pt) = Pt((p.x * width - offset) / w, p.y * width / w)
        /** Punto de la foto → compuesta. */
        fun toComposite(p: Pt) = Pt((p.x * w + offset) / width, p.y * w / width)
        /** La línea es del ojo real (su centro cae en la mitad de la foto, con un margen). */
        fun isReal(points: List<Pt>): Boolean {
            if (points.isEmpty()) return false
            val cx = points.map { it.x }.average().toFloat() * width - offset
            return cx >= -0.02f * w && cx <= 1.02f * w
        }
    }

    fun build(bmp: Bitmap, mirrored: Boolean): Pair<Bitmap, Layout> {
        val l = Layout(bmp.width, bmp.height, mirrored)
        val out = Bitmap.createBitmap(l.width, l.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // relleno: color mediano de la parte baja de la foto (mejilla)
        c.drawColor(cheekColor(bmp))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val flip = Matrix().apply { setScale(-1f, 1f); postTranslate(bmp.width.toFloat(), 0f) }
        if (!mirrored) {
            c.drawBitmap(bmp, 0f, 0f, paint)
            c.drawBitmap(bmp, flip.apply { postTranslate(bmp.width.toFloat(), 0f) }, paint)
        } else {
            c.drawBitmap(bmp, flip, paint)
            c.drawBitmap(bmp, bmp.width.toFloat(), 0f, paint)
        }
        return out to l
    }

    private fun cheekColor(bmp: Bitmap): Int {
        val y0 = (bmp.height * 0.85f).toInt().coerceAtMost(bmp.height - 1)
        val n = bmp.height - y0
        val px = IntArray(bmp.width * n).also { bmp.getPixels(it, 0, bmp.width, 0, y0, bmp.width, n) }
        fun med(sh: Int) = px.map { (it shr sh) and 0xFF }.sorted()[px.size / 2]
        return (0xFF shl 24) or (med(16) shl 16) or (med(8) shl 8) or med(0)
    }

    /** Capas del ojo que se conservan (lo demás de la «cara» compuesta no es de la foto). */
    val LAYERS = setOf(AnnotLayer.EYES, AnnotLayer.CROWS_FEET, AnnotLayer.DARK_CIRCLES, AnnotLayer.EYE_BAGS,
        AnnotLayer.INFRAORBITAL, AnnotLayer.FOLDS)
}
