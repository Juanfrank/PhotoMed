package com.photomed.app.ui.compare

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.photomed.app.align.AlignmentResult
import com.photomed.app.align.ColorAdjust
import com.photomed.app.align.ImageOps
import com.photomed.app.align.Perspective
import com.photomed.app.align.Similarity
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.PhotoRepository
import com.photomed.app.ui.formatDate
import com.photomed.app.ui.gallery.anonymizedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Exportación de la comparación tal como se ve: cada foto con la perspectiva corregida, el color
 * igualado y la alineación a la referencia aplicados (mismo encuadre 3:4), y opcionalmente una
 * composición en cuadrícula con las fechas. Se guarda en el subálbum `<expediente>/Exportadas` de
 * la galería o se comparte; sin datos del paciente por defecto.
 */
object CompareExport {
    const val WIDTH = 1080

    /**
     * Foto ajustada (perspectiva → color → semejanza a la referencia → recorte [crop] según la
     * alineación), en el encuadre 3:4.
     */
    suspend fun render(context: Context, photo: Photo, result: AlignmentResult?, color: ColorAdjust?, width: Int = WIDTH,
                       crop: Similarity? = null): Bitmap {
        var bmp = ImageOps.load34(context, photo.uri, width)
        val ks = result?.keystoneDeg ?: 0f
        return withContext(Dispatchers.Default) {
            if (ks != 0f) bmp = ImageOps.correctPerspective(bmp, ks, Perspective.DEFAULT_FOCAL)
            if (color != null) {
                val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                bmp = Bitmap.createBitmap(color.apply(px), bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            }
            transformed(bmp, withCrop(result?.transform, crop))
        }
    }

    /** Semejanza de la foto seguida del recorte común (si lo hay). */
    fun withCrop(t: Similarity?, crop: Similarity?): Similarity? = if (crop == null) t else crop.compose(t ?: Similarity.IDENTITY)

    /** Aplica la semejanza (unidades del ancho, origen arriba-izquierda) como en la vista previa. */
    fun transformed(src: Bitmap, t: Similarity?): Bitmap {
        if (t == null || (t.scale == 1f && t.rotation == 0f && t.tx == 0f && t.ty == 0f)) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val m = matrixOf(t, src.width.toFloat())
        Canvas(out).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG)) }
        return out
    }

    /** Matriz equivalente a [Modifier.alignedBy]: p' = T + R·S·p. */
    fun matrixOf(t: Similarity, width: Float) = Matrix().apply {
        setScale(t.scale, t.scale)
        postRotate(t.rotationDegrees)
        postTranslate(t.tx * width, t.ty * width)
    }

    /** Composición en cuadrícula (celdas 3:4) con la etiqueta de cada foto; [cols] fija las columnas (lado a lado = n). */
    fun composite(images: List<Pair<Bitmap, String>>, cellW: Int = 540, cols0: Int? = null): Bitmap {
        val n = images.size
        val cols = cols0 ?: if (n <= 3) n else ceil(sqrt(n.toDouble())).toInt()
        val rows = (n + cols - 1) / cols
        val cellH = cellW * 4 / 3; val gap = 8
        val out = Bitmap.createBitmap(cols * cellW + (cols + 1) * gap, rows * cellH + (rows + 1) * gap, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.rgb(0x1B, 0x1B, 0x1F))
        val img = Paint(Paint.FILTER_BITMAP_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = cellW * 0.05f; typeface = Typeface.DEFAULT_BOLD }
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        images.forEachIndexed { i, (b, label) ->
            val x = gap + (i % cols) * (cellW + gap); val y = gap + (i / cols) * (cellH + gap)
            c.drawBitmap(b, null, RectF(x.toFloat(), y.toFloat(), (x + cellW).toFloat(), (y + cellH).toFloat()), img)
            val pad = cellW * 0.02f
            val tw = text.measureText(label)
            c.drawRoundRect(RectF(x + pad, y + pad, x + pad * 3 + tw, y + pad * 3 + text.textSize), pad, pad, pill)
            c.drawText(label, x + pad * 2, y + pad * 1.6f + text.textSize, text)
        }
        return out
    }

    /** Animación GIF: [SLIDE] desplazamiento (cortinilla como el deslizador) o [FADE] fundido. */
    enum class GifMode(val label: String) { SLIDE("Desplazamiento"), FADE("Fundido") }

    const val GIF_W = 480
    const val GIF_HOLD_MS = 1200
    const val GIF_STEPS = 10
    const val GIF_STEP_MS = 70

    /**
     * Cuadros del GIF: cada foto se mantiene [GIF_HOLD_MS] y pasa a la siguiente (y la última a la
     * primera) en [GIF_STEPS] pasos, por fundido o con una cortinilla vertical que avanza de derecha
     * a izquierda (como el deslizador de la app). Etiqueta de fecha en cada cuadro.
     */
    fun gifFrames(images: List<Pair<Bitmap, String>>, mode: GifMode): List<Pair<IntArray, Int>> {
        val w = GIF_W; val h = w * 4 / 3
        val scaled = images.map { (b, l) -> Bitmap.createScaledBitmap(b, w, h, true) to l }
        val out = ArrayList<Pair<IntArray, Int>>()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = w * 0.045f; typeface = Typeface.DEFAULT_BOLD }
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        fun label(c: Canvas, s: String, right: Boolean) {
            val pad = w * 0.02f; val tw = text.measureText(s)
            val x0 = if (right) w - pad * 3 - tw else pad
            c.drawRoundRect(RectF(x0, pad, x0 + pad * 2 + tw, pad * 3 + text.textSize), pad, pad, pill)
            c.drawText(s, x0 + pad, pad * 1.6f + text.textSize, text)
        }
        fun frame(draw: (Canvas) -> Unit): IntArray {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); draw(Canvas(b))
            return IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        }
        val alpha = Paint(Paint.FILTER_BITMAP_FLAG)
        val line = Paint().apply { color = android.graphics.Color.WHITE; strokeWidth = w * 0.006f }
        for (i in scaled.indices) {
            val (a, la) = scaled[i]; val (b, lb) = scaled[(i + 1) % scaled.size]
            out += frame { c -> c.drawBitmap(a, 0f, 0f, null); label(c, la, false) } to GIF_HOLD_MS
            for (k in 1 until GIF_STEPS) {
                val t = k / GIF_STEPS.toFloat()
                out += frame { c ->
                    when (mode) {
                        GifMode.FADE -> {
                            c.drawBitmap(a, 0f, 0f, null)
                            alpha.alpha = (t * 255).toInt(); c.drawBitmap(b, 0f, 0f, alpha)
                            label(c, if (t < 0.5f) la else lb, false)
                        }
                        GifMode.SLIDE -> {
                            val x = w * (1f - t)
                            c.drawBitmap(a, 0f, 0f, null)
                            c.save(); c.clipRect(x, 0f, w.toFloat(), h.toFloat()); c.drawBitmap(b, 0f, 0f, null); c.restore()
                            c.drawLine(x, 0f, x, h.toFloat(), line)
                            label(c, la, false); label(c, lb, true)
                        }
                    }
                } to GIF_STEP_MS
            }
        }
        return out
    }

    private fun jpeg(b: Bitmap, f: File): File = f.also { out -> out.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) } }

    data class Result(val files: List<File>, val failed: Int)

    /**
     * Genera los archivos de la exportación en [dir]. Con [anonymize] los nombres no contienen
     * datos del paciente y no se escriben metadatos; si no, se copian los del original (EXIF/XMP).
     */
    suspend fun build(
        context: Context, repo: PhotoRepository, seq: List<Photo>,
        results: Map<String, AlignmentResult>, colors: Map<String, ColorAdjust>,
        individual: Boolean, grid: Boolean, anonymize: Boolean, dir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        /** Composición lado a lado (una fila). */
        side: Boolean = false,
        /** Animación GIF (null = no). */
        gif: GifMode? = null,
        /** Recorte común según la alineación (ver [overlapCrop]). */
        crop: Similarity? = null,
        /** Etiquetas (fecha, y hora si hay fotos del mismo día); por defecto, la fecha. */
        labels: List<String>? = null,
        /** Orden de presentación (índices de [seq]) para la cuadrícula y lado a lado. */
        order: List<Int>? = null,
    ): Result {
        dir.mkdirs()
        val files = mutableListOf<File>(); var failed = 0
        val rendered = mutableListOf<Pair<Bitmap, String>>()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        seq.forEachIndexed { i, p ->
            coroutineContext.ensureActive()
            onProgress(i, seq.size)
            val b = runCatching { render(context, p, results[p.key], colors[p.key], crop = crop) }.getOrNull()
            if (b == null) { failed++; return@forEachIndexed }
            val d = labels?.getOrNull(i) ?: formatDate(p.takenAt)
            val label = if (seq.size == 2) (if (i == 0) "Antes · " else "Después · ") + d else "${i + 1} · $d"
            rendered += (if (grid || side || gif != null) Bitmap.createScaledBitmap(b, 540, 720, true) else b) to label
            if (individual) withContext(Dispatchers.IO) {
                val name = if (anonymize) anonymizedName(p, i + 1).removeSuffix(".jpg") + "_ajustada.jpg"
                else p.displayName.removeSuffix(".jpg") + "_ajustada.jpg"
                val f = jpeg(b, File(dir, name))
                if (!anonymize) runCatching { repo.readInfo(p)?.let { PhotoMetadata.write(f, it) } }
                files += f
            }
        }
        val zone = seq.first().zone.id
        val shown = order?.filter { it in rendered.indices }?.takeIf { it.size == rendered.size }?.map { rendered[it] } ?: rendered
        if (grid && rendered.size >= 2) withContext(Dispatchers.IO) {
            val g = withContext(Dispatchers.Default) { composite(shown) }
            files += jpeg(g, File(dir, "PhotoMed_${zone}_comparacion_${rendered.size}_$stamp.jpg"))
        }
        if (side && rendered.size >= 2) withContext(Dispatchers.IO) {
            val g = withContext(Dispatchers.Default) { composite(shown, cols0 = shown.size) }
            files += jpeg(g, File(dir, "PhotoMed_${zone}_lado_a_lado_${rendered.size}_$stamp.jpg"))
        }
        if (gif != null && rendered.size >= 2) {
            coroutineContext.ensureActive()
            val bytes = withContext(Dispatchers.Default) {
                val enc = GifEncoder(GIF_W, GIF_W * 4 / 3)
                gifFrames(rendered, gif).forEach { (px, ms) -> enc.add(px, ms) }
                enc.toByteArray()
            }
            withContext(Dispatchers.IO) {
                val kind = if (gif == GifMode.FADE) "fundido" else "desplazamiento"
                files += File(dir, "PhotoMed_${zone}_${kind}_${rendered.size}_$stamp.gif").also { it.writeBytes(bytes) }
            }
        }
        onProgress(seq.size, seq.size)
        return Result(files, failed)
    }

    /** Guarda los archivos en la galería (subálbum Exportadas del expediente). */
    suspend fun saveToGallery(repo: PhotoRepository, exp: Expediente, files: List<File>): Int = withContext(Dispatchers.IO) {
        files.count { f -> runCatching { repo.saveExport(exp, f, f.name) }.isSuccess }
    }

    fun share(context: Context, files: List<File>, anonymize: Boolean) {
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        intent.type = if (files.all { it.extension.equals("gif", true) }) "image/gif"
            else if (files.any { it.extension.equals("gif", true) }) "image/*" else "image/jpeg"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, if (anonymize) "Compartir (anonimizada)" else "Compartir"))
    }
}
