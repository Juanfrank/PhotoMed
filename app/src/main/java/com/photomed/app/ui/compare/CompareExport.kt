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
import kotlin.math.roundToInt
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
                       crop: Similarity? = null,
                       /** Líneas de anotación (en la foto corregida) y capas visibles, dibujadas como en pantalla. */
                       annots: List<com.photomed.app.align.AnnotLine>? = null,
                       layers: Set<com.photomed.app.align.AnnotLayer> = emptySet(),
                       /** Giro y espejo de la vista (después de alinear y recortar). */
                       orient: com.photomed.app.align.ImageEdit? = null,
                       /** Números de las líneas (comunes a todas las fotos, como en la tabla). */
                       numbers: Map<String, Int>? = null): Bitmap {
        var bmp = ImageOps.load34(context, photo.uri, width)
        val ks = result?.keystoneDeg ?: 0f
        return withContext(Dispatchers.Default) {
            if (ks != 0f) bmp = ImageOps.correctPerspective(bmp, ks, Perspective.DEFAULT_FOCAL)
            if (color != null) {
                val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                bmp = Bitmap.createBitmap(color.apply(px), bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            }
            if (annots != null && layers.isNotEmpty()) bmp = drawAnnotations(bmp, annots, layers,
                numbers ?: com.photomed.app.align.MeasureNumbering.of(listOf(annots)))
            val out = transformed(bmp, withCrop(result?.transform, crop))
            if (orient == null || orient.isIdentity) out else orient.copy(crop = null).apply(out)
        }
    }

    /** Dibuja las anotaciones visibles sobre una copia de la foto (mismo estilo que en pantalla). */
    fun drawAnnotations(src: Bitmap, lines: List<com.photomed.app.align.AnnotLine>, layers: Set<com.photomed.app.align.AnnotLayer>,
                        numbers: Map<String, Int> = com.photomed.app.align.MeasureNumbering.of(listOf(lines))): Bitmap {
        val b = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(b); val w = b.width.toFloat()
        val k = w / 1080f * 3f
        for (l in lines) {
            if (l.layer !in layers || l.points.size < 2) continue
            val path = android.graphics.Path()
            l.points.forEachIndexed { i, q -> if (i == 0) path.moveTo(q.x * w, q.y * w) else path.lineTo(q.x * w, q.y * w) }
            if (l.closed) path.close()
            val col = com.photomed.app.ui.camera.lineColor(l).let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
            if (l.filled) {
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = (col and 0x00FFFFFF) or 0x48000000 })
                continue
            }
            val dash = if (l.estimated) android.graphics.DashPathEffect(floatArrayOf(7f * k, 5f * k), 0f) else null
            fun paint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = width; this.color = color; strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND; pathEffect = dash
            }
            c.drawPath(path, paint(0x66000000, 3.6f * k))
            c.drawPath(path, paint(col, 1.5f * k))
        }
        // números (los mismos que en la tabla de medidas)
        val r = 5.5f * k
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; textSize = r * 1.15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD }
        for ((l, a) in com.photomed.app.align.MeasureNumbering.place(lines.filter { it.layer in layers }, numbers, r / w)) {
            val n = numbers[l.metrics!!.name] ?: continue
            val x = a.x * w; val y = a.y * w
            val col = com.photomed.app.ui.camera.lineColor(l).let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
            c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6202020.toInt() })
            c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * k / 1.5f; color = col })
            c.drawText(n.toString(), x, y + txt.textSize * 0.36f, txt)
        }
        return b
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
        // celdas con la proporción de las fotos (3:4, o 4:3 si la vista está girada)
        val cellH = images.firstOrNull()?.first?.let { cellW * it.height / it.width } ?: (cellW * 4 / 3); val gap = 8
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
    /** Duración de cada paso de la transición (≈ 14 cuadros/s). */
    const val GIF_STEP_MS = 70

    /** Tiempos del GIF: [holdMs] cada foto quieta y [transitionMs] el paso a la siguiente (0 = corte). */
    data class GifTiming(val holdMs: Int = 1200, val transitionMs: Int = 700) {
        /** Pasos intermedios de la transición. */
        val steps: Int get() = if (transitionMs <= 0) 0 else (transitionMs / GIF_STEP_MS.toFloat()).roundToInt().coerceIn(2, 30)
        val stepMs: Int get() = if (steps == 0) 0 else transitionMs / steps
        /** Duración de un ciclo completo con [n] fotos. */
        fun cycleMs(n: Int) = n * (holdMs + steps * stepMs)
    }

    /**
     * Estado de la animación en el instante [timeMs] (en bucle): foto actual y avance de la
     * transición hacia la siguiente (0 = quieta), cuantizado en los mismos pasos que el GIF para que
     * la vista previa sea idéntica al archivo.
     */
    fun gifStateAt(timeMs: Long, n: Int, timing: GifTiming): Pair<Int, Float> {
        if (n < 1) return 0 to 0f
        val per = timing.holdMs + timing.steps * timing.stepMs
        val t = (timeMs % (per.toLong() * n)).toInt()
        val i = t / per; val r = t % per
        if (r < timing.holdMs || timing.steps == 0) return i to 0f
        val k = ((r - timing.holdMs) / timing.stepMs.coerceAtLeast(1)).coerceAtMost(timing.steps - 1) + 1
        return i to k / (timing.steps + 1f)
    }

    /** Dibuja un cuadro: foto [a] → [b] con avance [t] (fundido o cortinilla) y sus etiquetas. */
    fun drawGifFrame(c: Canvas, w: Int, h: Int, a: Bitmap, b: Bitmap, la: String, lb: String, t: Float, mode: GifMode) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = w * 0.045f; typeface = Typeface.DEFAULT_BOLD }
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        fun label(s: String, right: Boolean) {
            val pad = w * 0.02f; val tw = text.measureText(s)
            val x0 = if (right) w - pad * 3 - tw else pad
            c.drawRoundRect(RectF(x0, pad, x0 + pad * 2 + tw, pad * 3 + text.textSize), pad, pad, pill)
            c.drawText(s, x0 + pad, pad * 1.6f + text.textSize, text)
        }
        val dst = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val img = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(a, null, dst, img)
        if (t <= 0f) { label(la, false); return }
        when (mode) {
            GifMode.FADE -> {
                c.drawBitmap(b, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (t * 255).toInt() })
                label(if (t < 0.5f) la else lb, false)
            }
            GifMode.SLIDE -> {
                val x = w * (1f - t)
                c.save(); c.clipRect(x, 0f, w.toFloat(), h.toFloat()); c.drawBitmap(b, null, dst, img); c.restore()
                c.drawLine(x, 0f, x, h.toFloat(), Paint().apply { color = android.graphics.Color.WHITE; strokeWidth = w * 0.006f })
                label(la, false); label(lb, true)
            }
        }
    }

    /**
     * Cuadros del GIF: cada foto quieta [GifTiming.holdMs] y paso a la siguiente (la última vuelve a
     * la primera) en [GifTiming.steps] pasos por fundido o cortinilla (como el deslizador).
     */
    fun gifFrames(images: List<Pair<Bitmap, String>>, mode: GifMode, timing: GifTiming = GifTiming()): List<Pair<IntArray, Int>> {
        val w = GIF_W; val h = images.firstOrNull()?.first?.let { w * it.height / it.width } ?: (w * 4 / 3)
        val scaled = images.map { (b, l) -> Bitmap.createScaledBitmap(b, w, h, true) to l }
        val out = ArrayList<Pair<IntArray, Int>>()
        fun frame(a: Int, t: Float): IntArray {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val (ba, la) = scaled[a]; val (bb, lb) = scaled[(a + 1) % scaled.size]
            drawGifFrame(Canvas(bmp), w, h, ba, bb, la, lb, t, mode)
            return IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        }
        for (i in scaled.indices) {
            out += frame(i, 0f) to timing.holdMs
            for (k in 1..timing.steps) out += frame(i, k / (timing.steps + 1f)) to timing.stepMs
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
        gifTiming: GifTiming = GifTiming(),
        /** Recorte común según la alineación (ver [overlapCrop]). */
        crop: Similarity? = null,
        /** Etiquetas (fecha, y hora si hay fotos del mismo día); por defecto, la fecha. */
        labels: List<String>? = null,
        /** Anotaciones por clave de foto y capas visibles. */
        annots: Map<String, List<com.photomed.app.align.AnnotLine>> = emptyMap(),
        layers: Set<com.photomed.app.align.AnnotLayer> = emptySet(),
        /** Giro y espejo de la vista. */
        orient: com.photomed.app.align.ImageEdit? = null,
        /** Orden de presentación (índices de [seq]) para la cuadrícula y lado a lado. */
        order: List<Int>? = null,
    ): Result {
        dir.mkdirs()
        val files = mutableListOf<File>(); var failed = 0
        val rendered = mutableListOf<Pair<Bitmap, String>>()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val numbers = com.photomed.app.align.MeasureNumbering.of(annots.values.toList())
        seq.forEachIndexed { i, p ->
            coroutineContext.ensureActive()
            onProgress(i, seq.size)
            val b = runCatching { render(context, p, results[p.key], colors[p.key], crop = crop, annots = annots[p.key], layers = layers, orient = orient, numbers = numbers) }.getOrNull()
            if (b == null) { failed++; return@forEachIndexed }
            val d = labels?.getOrNull(i) ?: formatDate(p.takenAt)
            val label = if (seq.size == 2) (if (i == 0) "Antes · " else "Después · ") + d else "${i + 1} · $d"
            rendered += (if (grid || side || gif != null) Bitmap.createScaledBitmap(b, 540, 540 * b.height / b.width, true) else b) to label
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
                val enc = GifEncoder(GIF_W, rendered.first().first.let { GIF_W * it.height / it.width })
                gifFrames(rendered, gif, gifTiming).forEach { (px, ms) -> enc.add(px, ms) }
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
