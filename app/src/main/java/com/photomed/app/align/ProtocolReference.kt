package com.photomed.app.align

import android.content.Context
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.ProtocolRef
import com.photomed.app.data.RefLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Referencia de un paso de protocolo a partir de una foto existente, sin la imagen: lo mismo que la
 * cámara usa para coincidir (puntos anatómicos, pose, mirada, línea del pelo, perfil) más los
 * contornos, rasgos y surcos para dibujarlos como guía, y la distancia y el zoom de la toma.
 * Coordenadas en el encuadre 3:4 de la foto (unidades de su ancho), como en la cámara.
 */
object ProtocolReference {
    /** Capas que se guardan como guía (contornos, rasgos, surcos y arrugas). */
    private val LINE_LAYERS = setOf(AnnotLayer.CONTOUR, AnnotLayer.HAIRLINE, AnnotLayer.EYES, AnnotLayer.NOSE, AnnotLayer.LIPS,
        AnnotLayer.FOLDS, AnnotLayer.FOREHEAD, AnnotLayer.GLABELLA, AnnotLayer.CROWS_FEET)
    private const val MAX_LINE_PTS = 40

    suspend fun fromPhoto(context: Context, repo: PhotoRepository, photo: Photo): ProtocolRef? {
        // 1) lo que usa la coincidencia en vivo (modelos nativos, de uno en uno)
        val det = NativeGate.run("referencia de protocolo (${photo.zone.id})") {
            withContext(Dispatchers.Default) {
                val bmp = ImageOps.load34(context, photo.uri, 360)
                LiveMatcher(photo.zone, context).use { m ->
                    m.setReference(bmp)
                    val d0 = m.referenceDetection
                    // luz del rostro en la foto (como la mide la cámara en vivo)
                    val light = if (photo.zone.isFaceZone() && d0.points.isNotEmpty()) {
                        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                        Standardization.lighting(px, bmp.width, bmp.height, d0.points)
                    } else null
                    Triple(d0, m.referenceHairline, m.referenceProfile) to light
                }
            }
        }
        val (d, hair, profile) = det.first
        val light = det.second
        // 2) contornos y surcos: los confirmados de la foto o, si no hay, los del análisis
        val lines = runCatching { linesOf(context, photo) }.getOrDefault(emptyList())
        val cond = withContext(Dispatchers.IO) { runCatching { repo.readInfo(photo)?.conditions }.getOrNull() }.orEmpty()
        if (d.points.isEmpty() && profile == null && lines.isEmpty()) return null
        return ProtocolRef(d.points, d.yaw, d.pitch, d.roll, d.gaze, hair, profile, lines,
            cond["DistanceCm"]?.toFloatOrNull(), cond["Zoom"]?.toFloatOrNull(), photo.takenAt,
            light = light?.face?.takeIf { it > 0f },
            lightAsym = light?.let { l -> maxOf(l.leftCheek, l.rightCheek).takeIf { it > 0f }?.let { m -> kotlin.math.abs(l.leftCheek - l.rightCheek) / m } })
    }

    private suspend fun linesOf(context: Context, photo: Photo): List<RefLine> {
        val u = photo.uri.toString()
        val edit = PhotoEdits.of(context, u)
        val confirmed = withContext(Dispatchers.IO) { ConfirmedTraces.of(context, u) }?.lines(edit, TraceFrame.C34)
        val all = confirmed ?: run {
            val bmp = ImageOps.load34(context, photo.uri, 1024)
            PhotoAnnotator(context.applicationContext).analyze(bmp, photo.zone).first
        }
        return all.filter { it.layer in LINE_LAYERS && it.points.size >= 2 && !it.estimated }
            .map { l -> RefLine(l.layer.name, l.metrics?.name, simplify(l.points), l.closed) }
    }

    /** Como mucho [MAX_LINE_PTS] puntos por línea (el archivo queda pequeño; para dibujar basta). */
    private fun simplify(p: List<Pt>): List<Pt> = if (p.size <= MAX_LINE_PTS) p
        else (0 until MAX_LINE_PTS).map { i -> p[i * (p.size - 1) / (MAX_LINE_PTS - 1)] }

    /** Detección para la cámara a partir de la referencia guardada. */
    fun detectionOf(r: ProtocolRef) = Detection(r.points, r.yaw, r.pitch, r.roll, r.gaze)
}
