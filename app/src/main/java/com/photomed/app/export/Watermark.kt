package com.photomed.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn
import java.io.File

/**
 * Logo oficial (de la clínica o del profesional) para las imágenes exportadas: se adjunta en los
 * Ajustes (se guarda una copia en el almacenamiento privado de la app, con transparencia) y la
 * esquina se elige al exportar; la elección se recuerda.
 */
object Watermark {
    /** Posición del logo en la imagen exportada ([NONE]: sin logo). */
    enum class Position(private val es: String) {
        NONE(trn("Sin logo")), TOP_LEFT(trn("Arriba a la izquierda")), TOP_RIGHT(trn("Arriba a la derecha")),
        BOTTOM_LEFT(trn("Abajo a la izquierda")), BOTTOM_RIGHT(trn("Abajo a la derecha"));
        val label: String get() = tr(es)
    }

    /** Logo y dónde ponerlo. */
    class Mark(val logo: Bitmap, val position: Position)

    private const val PREF_POS = "watermark_position"
    /** Ancho del logo respecto al ancho de la imagen y margen respecto al lado menor. */
    const val WIDTH_FRACTION = 0.2f
    const val MARGIN_FRACTION = 0.03f
    private const val MAX_SIDE = 1024

    @Volatile private var cached: Pair<Long, Bitmap>? = null

    fun file(context: Context) = File(File(context.filesDir, "marca").apply { mkdirs() }, "logo.png")

    fun has(context: Context) = file(context).exists()

    /** Logo guardado (o null). */
    fun logo(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists()) return null
        cached?.let { (t, b) -> if (t == f.lastModified()) return b }
        return BitmapFactory.decodeFile(f.absolutePath)?.also { cached = f.lastModified() to it }
    }

    /** Copia la imagen elegida como logo (PNG con transparencia, lado mayor ≤ 1024 px). */
    fun set(context: Context, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException(tr("No se pudo leer la imagen"))
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException(tr("El archivo no es una imagen"))
        val s = minOf(1f, MAX_SIDE / maxOf(b.width, b.height).toFloat())
        val out = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else b
        val f = file(context)
        val tmp = File(f.parentFile, "logo.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        cached = null
        // al adjuntar un logo, que se use (abajo a la derecha) si aún no se eligió posición
        val prefs = context.getSharedPreferences("photomed", Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_POS)) prefs.edit().putString(PREF_POS, Position.BOTTOM_RIGHT.name).apply()
    }

    fun clear(context: Context) { file(context).delete(); cached = null }

    fun position(context: Context): Position = runCatching {
        Position.valueOf(context.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString(PREF_POS, Position.BOTTOM_RIGHT.name)!!)
    }.getOrDefault(Position.BOTTOM_RIGHT)

    fun setPosition(context: Context, p: Position) =
        context.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString(PREF_POS, p.name).apply()

    /** Logo y posición actuales para exportar (null: sin logo adjunto o «Sin logo»). */
    fun mark(context: Context): Mark? {
        val p = position(context).takeIf { it != Position.NONE } ?: return null
        return logo(context)?.let { Mark(it, p) }
    }

    /** Rectángulo del logo en una imagen [w]×[h]. */
    fun rect(w: Int, h: Int, logo: Bitmap, p: Position): RectF {
        val lw = w * WIDTH_FRACTION
        val lh = lw * logo.height / logo.width.coerceAtLeast(1)
        val m = minOf(w, h) * MARGIN_FRACTION
        val left = if (p == Position.TOP_LEFT || p == Position.BOTTOM_LEFT) m else w - m - lw
        val top = if (p == Position.TOP_LEFT || p == Position.TOP_RIGHT) m else h - m - lh
        return RectF(left, top, left + lw, top + lh)
    }

    /** Dibuja el logo en [c] (imagen de [w]×[h]). */
    fun draw(c: Canvas, w: Int, h: Int, mark: Mark?) {
        if (mark == null || mark.position == Position.NONE) return
        c.drawBitmap(mark.logo, null, rect(w, h, mark.logo, mark.position), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    /** Copia de [b] con el logo (la original no se toca). */
    fun apply(b: Bitmap, mark: Mark?): Bitmap {
        if (mark == null || mark.position == Position.NONE) return b
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        draw(Canvas(out), out.width, out.height, mark)
        return out
    }
}
