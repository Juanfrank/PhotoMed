package com.photomed.app.align

import com.photomed.app.i18n.tr
import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.io.Closeable
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Etiquetas por píxel de la segmentación multiclase (pelo, piel del rostro, piel del cuerpo, ropa).
 * [data]: una clase por píxel, fila mayor.
 */
class FaceLabels(val width: Int, val height: Int, val data: ByteArray) {
    operator fun get(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height) data[y * width + x].toInt() else BG

    fun mask(vararg classes: Int): Mask {
        val m = Mask(width, height)
        for (i in data.indices) m.data[i] = data[i].toInt() in classes
        return m
    }

    companion object {
        const val BG = 0
        const val HAIR = 1
        const val BODY_SKIN = 2
        const val FACE_SKIN = 3
        const val CLOTHES = 4
        const val OTHER = 5
    }
}

/**
 * Segmentación multiclase en el dispositivo (MediaPipe "selfie multiclass 256×256", Apache 2.0,
 * incluido en assets/selfie_multiclass.tflite): separa pelo, piel del rostro, piel del cuerpo y ropa.
 */
class HairSkinSegmenter(context: Context) : Closeable {
    private val seg: ImageSegmenter = ImageSegmenter.createFromOptions(context,
        ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true).setOutputConfidenceMasks(false)
            .build())

    fun labels(bmp: Bitmap): FaceLabels? = synchronized(this) { runCatching<FaceLabels?> {
        val src = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val r = seg.segment(BitmapImageBuilder(src).build())
        val m = r.categoryMask().orElse(null) ?: error(tr("sin máscara de categorías"))
        val buf = ByteBufferExtractor.extract(m)
        val out = ByteArray(m.width * m.height); buf.rewind(); buf.get(out)
        FaceLabels(m.width, m.height, out)
    }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull() }

    override fun close() = seg.close()

    companion object {
        const val MODEL = "selfie_multiclass.tflite"
        /** null si el modelo no se puede cargar (p. ej. dispositivo sin soporte). */
        fun createOrNull(context: Context): HairSkinSegmenter? =
            runCatching { HairSkinSegmenter(context) }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()

        @Volatile private var shared: HairSkinSegmenter? = null

        /**
         * Instancia compartida de los análisis de fotos (con la puerta de los modelos nativos): antes cada
         * análisis creaba y cerraba un segmentador nuevo, a veces varios por foto. No se cierra.
         */
        fun sharedOrNull(context: Context): HairSkinSegmenter? = shared ?: synchronized(this) {
            shared ?: createOrNull(context.applicationContext)?.also { shared = it }
        }

        /** Motivo del último fallo al cargar el modelo (para mostrarlo en pantalla y diagnosticar). */
        @Volatile var lastError: String? = null
    }
}

/**
 * Rasgos a partir de las etiquetas y los puntos del rostro:
 * - **línea de implantación frontal** (paso de piel del rostro a pelo por encima de las cejas),
 *   con la **altura de la frente** relativa a la distancia interpupilar (seguimiento tricológico);
 * - **piel del rostro** sin pelo, cejas, gafas ni fondo (para igualar el color).
 */
object HairSkin {
    data class Hairline(
        /** Puntos de la línea (x del ancho, y en unidades del ancho), de la derecha de la imagen a la izquierda. */
        val points: List<Pt>,
        /** Altura mediana de la frente (cejas → línea de implantación) / interpupilar; null si no se mide. */
        val heightIpd: Float?,
        /** Fracción del ancho de la frente donde se encontró la línea (el resto: flequillo, calvicie o fondo). */
        val coverage: Float,
    )

    const val SAMPLES = 31

    /** Zonas donde se sigue la línea de implantación frontal (vistas de frente que muestran la frente). */
    fun appliesTo(zone: com.photomed.app.data.Zone) =
        zone.guide == com.photomed.app.data.GuideType.FACE_FRONT || zone.guide == com.photomed.app.data.GuideType.FOREHEAD ||
            zone.guide == com.photomed.app.data.GuideType.SCALP_FRONT

    fun hairline(labels: FaceLabels, points: Map<String, Pt>): Hairline? {
        val p = FaceKeys.canonical(points)
        val el = p[Landmarks.EYE_L] ?: return null; val er = p[Landmarks.EYE_R] ?: return null
        val w = labels.width.toFloat()
        val ipd = hypot(el.x - er.x, el.y - er.y).takeIf { it > 1e-4f } ?: return null
        val ex = Pt((el.x - er.x) / ipd, (el.y - er.y) / ipd)
        val up = Pt(ex.y, -ex.x).let { if (it.y > 0) Pt(-it.x, -it.y) else it }
        // borde superior de las cejas, ordenado a lo largo de la línea de los ojos
        val brow = (0 until 5).flatMap { listOfNotNull(p["brow_r_top_$it"], p["brow_l_top_$it"]) }
        if (brow.size < 6) return null
        fun along(q: Pt) = q.x * ex.x + q.y * ex.y
        val sorted = brow.sortedBy(::along)
        val a0 = along(sorted.first()); val a1 = along(sorted.last())
        fun browAt(a: Float): Pt {
            val i = sorted.indexOfFirst { along(it) >= a }.let { if (it <= 0) 1 else it }.coerceAtMost(sorted.size - 1)
            val s = sorted[i - 1]; val e = sorted[i]
            val t = ((a - along(s)) / (along(e) - along(s)).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            return Pt(s.x + (e.x - s.x) * t, s.y + (e.y - s.y) * t)
        }
        val out = ArrayList<Pt>(); val heights = ArrayList<Float>()
        val step = 1f / w  // un píxel de las etiquetas, en unidades del ancho
        for (k in 0 until SAMPLES) {
            val a = a0 + (a1 - a0) * (0.04f + 0.92f * k / (SAMPLES - 1))
            val b = browAt(a)
            // desde algo por encima de la ceja (que la segmentación puede marcar como pelo)
            var d = 0.10f * ipd
            fun lab(dist: Float): Int {
                val q = Pt(b.x + up.x * dist, b.y + up.y * dist)
                return labels[(q.x * w).roundToInt(), (q.y * w).roundToInt()]
            }
            if (lab(d) != FaceLabels.FACE_SKIN) continue  // flequillo sobre las cejas: no se mide
            var found: Float? = null
            while (d < 1.8f * ipd) {
                val c = lab(d)
                if (c == FaceLabels.HAIR && lab(d + 2 * step) == FaceLabels.HAIR) { found = d; break }
                if (c != FaceLabels.FACE_SKIN && c != FaceLabels.HAIR) break  // fondo u otro: sin línea aquí
                d += step
            }
            val f = found ?: continue
            out += Pt(b.x + up.x * f, b.y + up.y * f)
            if (k in SAMPLES / 5..SAMPLES * 4 / 5) heights += f / ipd
        }
        if (out.size < SAMPLES / 4) return null
        val h = heights.sorted().let { if (it.size >= 3) it[it.size / 2] else null }
        return Hairline(out, h, out.size / SAMPLES.toFloat())
    }

    /**
     * Ajuste del óvalo facial al límite real de la cara según la segmentación: cada punto se mueve a
     * lo largo de la normal del óvalo (como mucho [MAX_SNAP] del ancho de la cara) hasta el borde
     * más cercano. Sienes y mejillas: piel del rostro → pelo o fondo; mandíbula y mentón: rostro (o
     * barba) → cuello, ropa o fondo (así la barba o la papada no lo encogen). La frente (puntos
     * altos, donde manda la línea de implantación) no se toca. Desplazamientos suavizados entre
     * vecinos. Devuelve los puntos con el óvalo ajustado.
     */
    fun snapOval(labels: FaceLabels, points: Map<String, Pt>): Map<String, Pt> {
        val p = FaceKeys.canonical(points)
        val oval = (0 until 36).map { p["oval_$it"] ?: return points }
        val w = labels.width.toFloat()
        val cx = oval.map { it.x }.average().toFloat(); val cy = oval.map { it.y }.average().toFloat()
        val faceW = oval.maxOf { it.x } - oval.minOf { it.x }
        val top = oval.minOf { it.y }; val bottom = oval.maxOf { it.y }
        val mouthY = p["lip_upper_top_5"]?.y ?: (top + (bottom - top) * 0.7f)
        // franja de las orejas: de los ojos a la base de la nariz (el borde de la piel ahí puede ser la oreja)
        val eyeY = listOfNotNull(p[Landmarks.EYE_L], p[Landmarks.EYE_R]).map { it.y }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            ?: (top + (bottom - top) * 0.4f)
        val noseY = p["nose_bottom_1"]?.y ?: (top + (bottom - top) * 0.62f)
        fun lab(q: Pt) = labels[(q.x * w).roundToInt(), (q.y * w).roundToInt()]
        val offs = FloatArray(36) { Float.NaN }
        for (i in 0 until 36) {
            val o = oval[i]
            // sienes (por encima de los ojos): borde en horizontal
            val upper = o.y < minOf(top + (bottom - top) * 0.38f, eyeY)
            val ear = o.y in eyeY..noseY
            val lateral = kotlin.math.abs(o.x - cx) > 0.25f * faceW
            // frente (centro): la decide la línea de implantación, no el óvalo
            if (upper && !lateral) { offs[i] = 0f; continue }
            val n = hypot(o.x - cx, o.y - cy).takeIf { it > 1e-5f } ?: continue
            // sienes: el borde de la piel se busca en horizontal (con la dirección radial desde el centro de
            // la cara, en diagonal, el contorno se metía hacia dentro de la sien)
            val nx = if (upper) kotlin.math.sign(o.x - cx) else (o.x - cx) / n
            val ny = if (upper) 0f else (o.y - cy) / n
            val lower = o.y > mouthY
            fun inside(c: Int) = if (lower) c == FaceLabels.FACE_SKIN || c == FaceLabels.HAIR else c == FaceLabels.FACE_SKIN
            val r = MAX_SNAP * faceW; val step = 1f / w
            var best: Float? = null
            var d = -r
            var prevIn = inside(lab(Pt(o.x + nx * d, o.y + ny * d)))
            // a la altura de las orejas, como mucho un 1,5 % del ancho hacia fuera (no se sigue la oreja)
            val maxOut = if (ear) EAR_OUT * faceW else r
            while (d <= minOf(r, maxOut)) {
                val cin = inside(lab(Pt(o.x + nx * d, o.y + ny * d)))
                if (prevIn && !cin && (best == null || kotlin.math.abs(d) < kotlin.math.abs(best))) best = d
                prevIn = cin; d += step
            }
            offs[i] = best ?: Float.NaN
        }
        if (offs.count { !it.isNaN() } < 18) return points
        // suavizado circular de los desplazamientos (los huecos toman el de sus vecinos)
        val sm = FloatArray(36) { i ->
            val v = (-2..2).mapNotNull { k -> offs[(i + k + 36) % 36].takeIf { !it.isNaN() } }
            if (v.isEmpty()) 0f else v.sorted()[v.size / 2]
        }
        val out = p.toMutableMap()
        for (i in 0 until 36) {
            val o = oval[i]
            val n = hypot(o.x - cx, o.y - cy).takeIf { it > 1e-5f } ?: continue
            val upper = o.y < minOf(top + (bottom - top) * 0.38f, eyeY)
            out["oval_$i"] = if (upper) Pt(o.x + kotlin.math.sign(o.x - cx) * sm[i], o.y)
                else Pt(o.x + (o.x - cx) / n * sm[i], o.y + (o.y - cy) / n * sm[i])
        }
        return out
    }

    const val MAX_SNAP = 0.08f
    /** Máximo hacia fuera a la altura de las orejas (fracción del ancho de la cara). */
    const val EAR_OUT = 0.015f

    /**
     * Puntos tapados en una foto: rasgos interiores (ojos, nariz, labios, pupilas, comisuras) que
     * caen sobre pelo (flequillo), accesorios (gafas oscuras, mascarilla), la mano o el fondo.
     * Óvalo y cejas quedan fuera (están en el límite o la segmentación las marca como pelo).
     */
    fun occluded(labels: FaceLabels, points: Map<String, Pt>): Set<String> {
        val w = labels.width.toFloat()
        return points.filter { (k, q) ->
            if (k.startsWith("oval_") || k.startsWith("brow_") || k.startsWith("ear") || k.startsWith("skull") || k == "vertex") return@filter false
            val c = labels[(q.x * w).roundToInt(), (q.y * w).roundToInt()]
            c == FaceLabels.HAIR || c == FaceLabels.OTHER || c == FaceLabels.BG || c == FaceLabels.BODY_SKIN
        }.keys
    }

    /** Piel del rostro (clase "piel del rostro") a [w]×[h]. */
    fun faceSkin(labels: FaceLabels, w: Int, h: Int): BooleanArray =
        BodyRegion.resample(labels.mask(FaceLabels.FACE_SKIN), w, h).data
}
