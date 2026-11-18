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

    /** Foto ajustada (perspectiva → color → semejanza a la referencia), en el encuadre 3:4. */
    suspend fun render(context: Context, photo: Photo, result: AlignmentResult?, color: ColorAdjust?, width: Int = WIDTH): Bitmap {
        var bmp = ImageOps.load34(context, photo.uri, width)
        val ks = result?.keystoneDeg ?: 0f
        return withContext(Dispatchers.Default) {
            if (ks != 0f) bmp = ImageOps.correctPerspective(bmp, ks, Perspective.DEFAULT_FOCAL)
            if (color != null) {
                val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                bmp = Bitmap.createBitmap(color.apply(px), bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            }
            transformed(bmp, result?.transform)
        }
    }

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

    /** Composición en cuadrícula (celdas 3:4) con la etiqueta de cada foto. */
    fun composite(images: List<Pair<Bitmap, String>>, cellW: Int = 540): Bitmap {
        val n = images.size
        val cols = if (n <= 3) n else ceil(sqrt(n.toDouble())).toInt()
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
    ): Result {
        dir.mkdirs()
        val files = mutableListOf<File>(); var failed = 0
        val rendered = mutableListOf<Pair<Bitmap, String>>()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        seq.forEachIndexed { i, p ->
            coroutineContext.ensureActive()
            onProgress(i, seq.size)
            val b = runCatching { render(context, p, results[p.key], colors[p.key]) }.getOrNull()
            if (b == null) { failed++; return@forEachIndexed }
            val label = if (seq.size == 2) (if (i == 0) "Antes · " else "Después · ") + formatDate(p.takenAt) else "${i + 1} · ${formatDate(p.takenAt)}"
            rendered += (if (grid) Bitmap.createScaledBitmap(b, 540, 720, true) else b) to label
            if (individual) withContext(Dispatchers.IO) {
                val name = if (anonymize) anonymizedName(p, i + 1).removeSuffix(".jpg") + "_ajustada.jpg"
                else p.displayName.removeSuffix(".jpg") + "_ajustada.jpg"
                val f = jpeg(b, File(dir, name))
                if (!anonymize) runCatching { repo.readInfo(p)?.let { PhotoMetadata.write(f, it) } }
                files += f
            }
        }
        if (grid && rendered.size >= 2) withContext(Dispatchers.IO) {
            val g = withContext(Dispatchers.Default) { composite(rendered) }
            val zone = seq.first().zone.id
            files += jpeg(g, File(dir, "PhotoMed_${zone}_comparacion_${rendered.size}_$stamp.jpg"))
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
        intent.type = "image/jpeg"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, if (anonymize) "Compartir (anonimizada)" else "Compartir"))
    }
}
