package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.photomed.app.align.AutoCapture
import com.photomed.app.align.ContourMatch
import com.photomed.app.align.Detection
import com.photomed.app.align.Detector
import com.photomed.app.align.EdgeAligner
import com.photomed.app.align.FaceMeshMap
import com.photomed.app.align.ImageOps
import com.photomed.app.align.LandmarkDetector
import com.photomed.app.align.LiveDecision
import com.photomed.app.align.LiveMatch
import com.photomed.app.align.LiveMatcher
import com.photomed.app.align.MatchEval
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.Standardization
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Zone
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Captura automática en TODAS las zonas con fotos reales de internet (Openverse / Wikimedia Commons,
 * licencias libres; fuera del repositorio: ver tools/eval/fetch_zone_images.py). Los puntos de cara y
 * cuerpo los da MediaPipe en Python (tools/eval/zone_landmarks.py, los mismos modelos de la app) y aquí
 * se pasan por el MISMO camino que la cámara: conversión de la malla y de la pose
 * ([FaceMeshMap.detectionOf], [LandmarkDetector.finishFace], [LandmarkDetector.poseKeys]), luz del
 * rostro, estandarización o coincidencia con la referencia, alineación por bordes (zonas sin detector)
 * y la condición del disparo ([AutoCapture.ready]).
 *
 * Para cada zona:
 * - Primera foto (sin referencia): ¿dispara con una foto bien tomada? ¿y NO con la vista equivocada?
 * - Foto de control (con referencia): dispara con el mismo encuadre y NO si el paciente se mueve o la
 *   cámara se aleja (es también lo que vigila la cancelación durante la captura).
 *
 * Informe: build/reports/zone_autocapture.md. Sin las imágenes (no están en el repositorio) se omite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoneAutoCaptureTest {
    private val root = File("src/test/resources/stock/zones")

    /** Carpeta de imágenes de cada zona y si su lado se elige con el espejo (m_…). */
    private fun dirOf(z: Zone): String = when (z) {
        Zone.FACE_FRONTAL -> "rostro_frontal"
        Zone.FACE_OBLIQUE_R, Zone.FACE_OBLIQUE_L -> "rostro_oblicuo"
        Zone.FACE_PROFILE_R, Zone.FACE_PROFILE_L -> "rostro_perfil"
        Zone.FOREHEAD -> "tercio_superior"
        Zone.PERIOCULAR -> "periocular"
        Zone.EYE_R, Zone.EYE_L -> "ojo"
        Zone.LIPS -> "labios"
        Zone.NECK -> "cuello"
        Zone.SCALP_VERTEX, Zone.SCALP_CROWN, Zone.SCALP_PARIETAL_R, Zone.SCALP_PARIETAL_L -> "cuero_cabelludo"
        Zone.TORSO_FRONT -> "abdomen_frontal"
        Zone.TORSO_SIDE, Zone.TORSO_SIDE_L -> "abdomen_lateral"
        Zone.BREASTS -> "torax"
        Zone.BACK -> "espalda"
        Zone.GLUTES -> "gluteos"
        Zone.LEGS -> "piernas"
        Zone.ARM_R, Zone.ARM_L -> "brazo"
        Zone.HANDS_DORSAL -> "manos_dorso"
        Zone.HANDS_PALMAR -> "manos_palma"
        Zone.FEET_DORSAL -> "pies_dorso"
        Zone.FEET_PLANTAR -> "pies_planta"
        Zone.FREE -> "rostro_frontal"
    }

    class Sample(val file: File, val w: Int, val h: Int, val face: Detection, val pose: Detection)

    private fun load(dir: String): List<Sample> {
        val d = File(root, dir); val js = File(d, "landmarks.json")
        if (!js.exists()) return emptyList()
        val o = JSONObject(js.readText())
        return o.keys().asSequence().sorted().map { name ->
            val e = o.getJSONObject(name); val w = e.getInt("w"); val h = e.getInt("h")
            val face = e.optJSONObject("face")?.let { f ->
                val mesh = f.getJSONArray("mesh")
                val m = f.optJSONArray("matrix")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
                LandmarkDetector.finishFace(FaceMeshMap.detectionOf({ i -> mesh.getJSONArray(i).let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() } },
                    mesh.length(), w, h, m))
            } ?: Detection.EMPTY
            val pose = e.optJSONArray("pose")?.let { a ->
                Detection(LandmarkDetector.poseKeys((0 until a.length()).associateWith { i -> a.getJSONArray(i).let {
                    Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w, it.getDouble(2).toFloat()) } }))
            } ?: Detection.EMPTY
            Sample(File(d, name), w, h, face, pose)
        }.toList()
    }

    private fun detOf(s: Sample, z: Zone) = when (ZoneAlignment.of(z).detector) {
        Detector.FACE -> s.face; Detector.POSE -> s.pose; Detector.NONE -> Detection.EMPTY
    }

    private val bitmaps = HashMap<File, Bitmap>()
    private fun bmp(f: File): Bitmap = bitmaps.getOrPut(f) { BitmapFactory.decodeFile(f.absolutePath) }

    private fun light(s: Sample, det: Detection): Standardization.Lighting? {
        val b = bmp(s.file)
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return Standardization.lighting(px, b.width, b.height, det.points)
    }

    /** Primera foto, sin referencia: lo que hace LiveMatcher.standardize + AutoCapture.ready. */
    private fun standard(z: Zone, s: Sample): MatchEval {
        val det = detOf(s, z)
        val l = if (ZoneAlignment.of(z).detector == Detector.FACE) light(s, det) else null
        return Standardization.evaluate(z, det, l)
    }

    /** Con referencia (puntos): LiveDecision, como LiveMatcher.analyze con detector. */
    private fun withRef(z: Zone, ref: Detection, cur: Detection) =
        LiveDecision.evaluate(ZoneAlignment.of(z), ref, cur, LiveMatch.DEFAULT_THRESHOLD, wasMatched = false)

    /** Con referencia (sin detector): alineación por bordes, como LiveMatcher.analyze. */
    private fun withRefEdges(z: Zone, ref: Bitmap, cur: Bitmap): MatchEval {
        val spec = ZoneAlignment.of(z)
        val refAll = ImageOps.gray(ref, LiveMatcher.EDGE_W)
        val refG = ImageOps.gray(ref, LiveMatcher.EDGE_W, null)
        val refPts = ContourMatch.referencePoints(refG, spec.contours)
        val g = ImageOps.gray(cur, LiveMatcher.EDGE_W, null)
        val r = EdgeAligner(spec.contours).align(refAll, g, null, EdgeAligner.Window.LIVE, levels = 2)
        val t = if (EdgeAligner.isConfident(r.score)) r.transform else null
        return LiveMatch.evaluate(t, spec.pivot, false, ContourMatch.fraction(refPts, g), LiveMatch.DEFAULT_THRESHOLD, gate = true)
    }

    /** Puntos desplazados y escalados respecto del centro (paciente que se mueve / cámara que se aleja). */
    private fun moved(d: Detection, dx: Float, scale: Float): Detection {
        val c = d.points.values.let { v -> Pt(v.map { it.x }.average().toFloat(), v.map { it.y }.average().toFloat()) }
        return d.copy(points = d.points.mapValues { (_, p) -> Pt(c.x + (p.x - c.x) * scale + dx, c.y + (p.y - c.y) * scale) })
    }

    /** Recorte desplazado de la imagen (el teléfono se mueve una fracción [dx] del ancho). */
    private fun shifted(b: Bitmap, dx: Float, scale: Float = 1f): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out); c.drawColor(android.graphics.Color.GRAY)
        c.translate(dx * b.width + b.width * (1 - scale) / 2, b.height * (1 - scale) / 2); c.scale(scale, scale)
        c.drawBitmap(b, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun side(s: Sample) = Standardization.facingSide(s.face.points)
    private fun mirroredName(s: Sample) = s.file.name.startsWith("m_")

    /** Imágenes de cada zona (las de lado derecho / izquierdo según la geometría o el espejo). */
    private fun samplesFor(z: Zone, all: List<Sample>): List<Sample> = when (z) {
        Zone.FACE_OBLIQUE_R, Zone.FACE_PROFILE_R -> all.filter { (side(it) ?: 0f) > 0.08f || (it.face.points.isEmpty() && !mirroredName(it)) }
        Zone.FACE_OBLIQUE_L, Zone.FACE_PROFILE_L -> all.filter { (side(it) ?: 0f) < -0.08f || (it.face.points.isEmpty() && mirroredName(it)) }
        Zone.EYE_L, Zone.ARM_L, Zone.TORSO_SIDE_L -> all.filter { mirroredName(it) }
        Zone.EYE_R, Zone.ARM_R, Zone.TORSO_SIDE -> all.filter { !mirroredName(it) }
        else -> all
    }

    data class Row(val zone: Zone, val images: Int, val detected: Int, val firstReady: Int, val wrongViewReady: Int?,
                   val refSame: Int, val refMoved: Int, val refN: Int, val notes: String)

    @Test
    fun everyZoneAutoCaptures() {
        assumeTrue("sin imágenes de prueba (tools/eval/fetch_zone_images.py + zone_landmarks.py)", File(root, "rostro_frontal/landmarks.json").exists())
        val cache = HashMap<String, List<Sample>>()
        val rows = ArrayList<Row>()
        val frontal = cache.getOrPut("rostro_frontal") { load("rostro_frontal") }
        for (z in Zone.entries) {
            val spec = ZoneAlignment.of(z)
            val all = cache.getOrPut(dirOf(z)) { load(dirOf(z)) }
            val ss = samplesFor(z, all)
            val dets = ss.map { detOf(it, z) }
            val detected = if (spec.detector == Detector.NONE) ss.size else dets.count { it.points.size >= LiveDecision.MIN_POINTS }
            // 1) primera foto (sin referencia)
            val firstReady = if (spec.detector == Detector.NONE) 0 else ss.count { AutoCapture.ready(standard(z, it)) }
            // vista equivocada: fotos frontales en las zonas de oblicuo/perfil (no debe disparar)
            val wrong = if (z in setOf(Zone.FACE_OBLIQUE_R, Zone.FACE_OBLIQUE_L, Zone.FACE_PROFILE_R, Zone.FACE_PROFILE_L))
                frontal.count { AutoCapture.ready(standard(z, it)) } else null
            // 2) con referencia: el mismo encuadre dispara; movido 15 % y alejado ×0,8, no
            var same = 0; var mov = 0; var n = 0
            for ((i, s) in ss.withIndex().take(12)) {
                if (spec.detector == Detector.NONE) {
                    val b = bmp(s.file); n++
                    if (AutoCapture.ready(withRefEdges(z, b, shifted(b, 0.01f)), protocol = true)) same++
                    if (AutoCapture.ready(withRefEdges(z, b, shifted(b, 0.15f, 0.8f)), protocol = true)) mov++
                } else {
                    val d = dets[i]; if (d.points.size < LiveDecision.MIN_POINTS) continue
                    n++
                    if (AutoCapture.ready(withRef(z, d, d), protocol = true)) same++
                    if (AutoCapture.ready(withRef(z, d, moved(d, 0.15f, 0.8f)), protocol = true)) mov++
                }
            }
            // indicaciones más frecuentes de las que no dispararon (para el informe)
            val hints = if (spec.detector == Detector.NONE) emptyList() else ss.map { standard(z, it) }.filter { !AutoCapture.ready(it) }
                .flatMap { it.hints }.groupingBy { it.substringBefore(" ").substringBefore(":") + ": " + it.substringAfter(": ").take(28) }.eachCount()
                .entries.sortedByDescending { it.value }.take(3).joinToString("; ") { "${it.key} (${it.value})" }
            rows += Row(z, ss.size, detected, firstReady, wrong, same, mov, n, hints)
        }
        val md = buildString {
            appendLine("# Captura automática por zona (fotos de internet)\n")
            appendLine("| Zona | Detector | Imágenes | Detectadas | 1.ª foto: dispara | Vista equivocada dispara | Con referencia: mismo encuadre | Con referencia: movido | Indicaciones más frecuentes al no disparar |")
            appendLine("|---|---|---|---|---|---|---|---|---|")
            rows.forEach { r ->
                val det = ZoneAlignment.of(r.zone).detector
                appendLine("| ${r.zone.id} | $det | ${r.images} | ${r.detected} | ${if (det == Detector.NONE) "—" else "${r.firstReady}/${r.images}"} | " +
                    "${r.wrongViewReady?.let { "$it/${frontal.size}" } ?: "—"} | ${r.refSame}/${r.refN} | ${r.refMoved}/${r.refN} | ${r.notes} |")
            }
        }
        File("build/reports").mkdirs(); File("build/reports/zone_autocapture.md").writeText(md)
        println(md)
        // comprobaciones: cada zona con imágenes dispara con referencia en el mismo encuadre y no movida;
        // las zonas con detector disparan en la primera foto con alguna foto bien tomada; la vista
        // equivocada no dispara
        val problems = rows.filter { it.images > 0 }.flatMap { r ->
            val det = ZoneAlignment.of(r.zone).detector
            listOfNotNull(
                if (r.refN == 0) "${r.zone.id}: ninguna imagen utilizable con referencia" else null,
                if (r.refN > 0 && r.refSame < r.refN * 0.9) "${r.zone.id}: con referencia y el mismo encuadre dispara solo ${r.refSame}/${r.refN}" else null,
                if (r.refMoved > 0) "${r.zone.id}: con referencia dispara aunque el paciente se haya movido (${r.refMoved}/${r.refN})" else null,
                if (det != Detector.NONE && r.firstReady == 0) "${r.zone.id}: la primera foto (sin referencia) nunca dispara" else null,
                if ((r.wrongViewReady ?: 0) > 0) "${r.zone.id}: dispara con la vista equivocada (${r.wrongViewReady})" else null,
            )
        }
        println(problems.joinToString("\n", "PROBLEMAS:\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
