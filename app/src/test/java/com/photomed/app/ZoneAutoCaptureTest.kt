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
import com.photomed.app.align.SteadyFraming
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

    class Sample(val file: File, val w: Int, val h: Int, val face: Detection, val pose: Detection,
                 val hands: List<com.photomed.app.align.ZoneCriteria.Hand>? = null, val classes: com.photomed.app.align.ZoneCriteria.Classes? = null)

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
            val hands = e.optJSONArray("hands")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                val pts = o.getJSONArray("points")
                com.photomed.app.align.ZoneCriteria.Hand((0 until pts.length()).map { j -> pts.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w) } }, o.getString("label"))
            } } }
            val classes = e.optString("classes", "").takeIf { it.length == 96 * 128 }?.let { str ->
                com.photomed.app.align.ZoneCriteria.Classes(96, 128, ByteArray(str.length) { (str[it] - '0').toByte() })
            }
            Sample(File(d, e.optString("image", name)), w, h, face, pose, hands, classes)
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
        return Standardization.lighting(px, b.width, b.height, det.points, det.yaw)
    }

    /** Primera foto, sin referencia, con puntos: Standardization (LiveMatcher.standardize). */
    private fun standard(z: Zone, s: Sample): MatchEval {
        val det = detOf(s, z)
        val l = if (ZoneAlignment.of(z).detector == Detector.FACE) light(s, det) else null
        return Standardization.evaluate(z, det, l)
    }

    /**
     * Primera foto por quietud del encuadre (SteadyFraming, como LiveMatcher.standardize): 1,2 s de
     * cuadros cada 100 ms; [move]: desplazamiento de cada cuadro (temblor de la mano o movimiento).
     */
    private fun steadyRun(b: Bitmap, move: Float): MatchEval {
        val sf = SteadyFraming(); var e: MatchEval? = null
        for (k in 0..12) e = sf.update(ImageOps.luma(shifted(b, if (k % 2 == 0) move else -move), LiveMatcher.EDGE_W), k * 100L)
        return e!!
    }

    /** Criterios clínicos de la zona sobre la foto (pose, manos y piel/ropa calculadas con MediaPipe). */
    private fun criteria(z: Zone, s: Sample): List<String> =
        com.photomed.app.align.ZoneCriteria.hints(z, com.photomed.app.align.ZoneCriteria.Scene(
            if (ZoneAlignment.of(z).detector == Detector.POSE) s.pose.points else emptyMap(), s.hands, s.classes, s.h / s.w.toFloat()))

    /** Primera foto completa: por quietud si no hay puntos que comprobar, si no la estandarización. */
    private fun first(z: Zone, s: Sample): MatchEval =
        if (SteadyFraming.applies(z, detOf(s, z))) steadyRun(bmp(s.file), 0.001f) else standard(z, s)

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
        return LiveMatcher.edgeEval(t, r.score, ContourMatch.fraction(refPts, g), spec.pivot, false, LiveMatch.DEFAULT_THRESHOLD)
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

    data class Row(val zone: Zone, val images: Int, val detected: Int, val firstN: Int, val firstReady: Int, val geomOk: Int, val unexplained: Int,
                   val criteriaN: Int, val criteriaOk: Int, val criteriaNotes: String, val wrongViewReady: Int?,
                   val steadyMovingReady: Int, val steadyN: Int, val refSame: Int, val refMoved: Int, val refN: Int, val notes: String)

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
            // 1) primera foto (sin referencia). Las vistas completas del rostro necesitan la cara (el
            // perfil sin cara usa la silueta de ML Kit, que no corre en la JVM: se omite)
            val firstSet = ss.filterIndexed { i, s -> SteadyFraming.applies(z, dets[i]) || dets[i].points.size >= LiveDecision.MIN_POINTS }
            // criterios clínicos de la zona (manos completas, zona descubierta, postura…): la toma solo
            // dispara si además los cumple
            val hasCriteria = com.photomed.app.align.ZoneCriteria.applies(z)
            val crit = if (hasCriteria) firstSet.map { criteria(z, it) } else firstSet.map { emptyList<String>() }
            val critOk = crit.count { it.isEmpty() }
            val critNotes = crit.flatten().groupingBy { it.take(48) }.eachCount().entries.sortedByDescending { it.value }.take(3)
                .joinToString("; ") { "${it.key} (${it.value})" }
            val firstEvals = firstSet.mapIndexed { i, it -> first(z, it).let { e -> if (crit[i].isEmpty()) e else e.copy(status = if (e.status == com.photomed.app.align.MatchStatus.MATCH) com.photomed.app.align.MatchStatus.CLOSE else e.status,
                quality = e.quality.coerceAtMost(0.75f), hints = e.hints + crit[i]) } }
            val firstReady = firstEvals.count { AutoCapture.ready(it) }
            // fotos que cumplen la geometría de la vista (sin indicaciones de cabeza, barbilla o lado):
            // si las hay, alguna debe disparar (el resto falla por luz, expresión o mirada)
            val geometric = listOf("Cabeza", "Barbilla", "Cara:", "Perfil", "No se detecta", "Mantenga", "Encuadre")
            val geomOk = firstEvals.count { e -> e.hints.none { h -> geometric.any { h.startsWith(it) } } }
            // rechazos sin explicación (sin indicación): serían un error
            val unexplained = firstEvals.count { !AutoCapture.ready(it) && it.hints.isEmpty() }
            // por quietud: moviendo el teléfono (3 % por cuadro) no debe disparar; ni con la cámara tapada
            val steadySet = firstSet.filter { SteadyFraming.applies(z, detOf(it, z)) }
            val steadyMoving = steadySet.take(8).count { AutoCapture.ready(steadyRun(bmp(it.file), 0.03f)) }
            // vista equivocada: fotos frontales en las zonas de oblicuo/perfil (no debe disparar)
            // y el lado contrario (perfil u oblicuo izquierdo en la zona derecha y al revés)
            val opposite = when (z) {
                Zone.FACE_OBLIQUE_R -> Zone.FACE_OBLIQUE_L; Zone.FACE_OBLIQUE_L -> Zone.FACE_OBLIQUE_R
                Zone.FACE_PROFILE_R -> Zone.FACE_PROFILE_L; Zone.FACE_PROFILE_L -> Zone.FACE_PROFILE_R; else -> null
            }
            val wrong = opposite?.let { o ->
                frontal.count { AutoCapture.ready(standard(z, it)) } +
                    samplesFor(o, cache.getOrPut(dirOf(o)) { load(dirOf(o)) }).filter { detOf(it, o).points.size >= LiveDecision.MIN_POINTS }
                        .count { AutoCapture.ready(standard(z, it)) }
            }
            // 2) con referencia (como LiveMatcher.analyze): con puntos en la referencia, LiveDecision; si
            // no, alineación por bordes. El mismo encuadre dispara; movido 15 % y alejado ×0,8, no
            var same = 0; var mov = 0; var n = 0
            val refFails = ArrayList<String>()
            for ((i, s) in ss.withIndex().take(12)) {
                val d = dets[i]; n++
                if (LiveMatcher.usesPoints(spec, d)) {
                    val e = withRef(z, d, d)
                    if (AutoCapture.ready(e, protocol = true)) same++
                    else refFails += "puntos ${e.status} ${"%.2f".format(e.pointFraction ?: -1f)} ${e.hints.take(2)}"
                    if (AutoCapture.ready(withRef(z, d, moved(d, 0.15f, 0.8f)), protocol = true)) mov++
                } else {
                    val b = bmp(s.file)
                    val e = withRefEdges(z, b, shifted(b, 0.01f))
                    if (AutoCapture.ready(e, protocol = true)) same++
                    else refFails += "bordes ${e.status} ${"%.2f".format(e.pointFraction ?: -1f)} ${e.hints.take(2)}"
                    if (AutoCapture.ready(withRefEdges(z, b, shifted(b, 0.15f, 0.8f)), protocol = true)) mov++
                }
            }
            // indicaciones más frecuentes de las que no dispararon (para el informe)
            val hints = firstSet.map { first(z, it) }.filter { !AutoCapture.ready(it) }
                .flatMap { it.hints }.groupingBy { it.take(40) }.eachCount()
                .entries.sortedByDescending { it.value }.take(3).joinToString("; ") { "${it.key} (${it.value})" }
            if (refFails.isNotEmpty()) println("REF ${z.id}: " + refFails.joinToString(" | "))
            if (System.getenv("PM_ZONE_DEBUG") != null && spec.detector == Detector.FACE) firstSet.forEach { s0 ->
                val e = first(z, s0); val d = detOf(s0, z)
                println("DBG ${z.id} ${s0.file.name} yaw=${d.yaw} pose=${d.pose?.yaw} side=${Standardization.facingSide(d.points)} pitch=${d.pitch} roll=${d.roll} gaze=${d.gaze} ${e.status} q=${"%.2f".format(e.quality)} ${e.hints}")
            }
            rows += Row(z, ss.size, detected, firstSet.size, firstReady, geomOk, unexplained,
                if (hasCriteria) firstSet.size else 0, critOk, critNotes, wrong, steadyMoving, steadySet.size.coerceAtMost(8), same, mov, n, hints)
        }
        val md = buildString {
            appendLine("# Captura automática por zona (fotos de internet)\n")
            appendLine("| Zona | Detector | Imágenes | Con puntos | Cumplen los criterios clínicos | Criterios incumplidos más frecuentes | 1.ª foto: dispara | 1.ª foto: cumplen la geometría | 1.ª foto moviendo el teléfono: dispara | Vista equivocada: dispara | Referencia, mismo encuadre: dispara | Referencia, movido: dispara | Indicaciones más frecuentes al no disparar |")
            appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
            rows.forEach { r ->
                val det = ZoneAlignment.of(r.zone).detector
                appendLine("| ${r.zone.id} | $det | ${r.images} | ${if (det == Detector.NONE) "—" else "${r.detected}"} | ${if (r.criteriaN > 0) "${r.criteriaOk}/${r.criteriaN}" else "—"} | ${r.criteriaNotes} | ${r.firstReady}/${r.firstN} | ${r.geomOk}/${r.firstN} | " +
                    "${if (r.steadyN > 0) "${r.steadyMovingReady}/${r.steadyN}" else "—"} | ${r.wrongViewReady?.toString() ?: "—"} | " +
                    "${r.refSame}/${r.refN} | ${r.refMoved}/${r.refN} | ${r.notes} |")
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
                // con criterios clínicos, que no dispare es lo esperado si las fotos no los cumplen
                if (r.criteriaN == 0 && r.geomOk > 0 && r.firstReady == 0) "${r.zone.id}: la primera foto nunca dispara aunque ${r.geomOk} fotos cumplen la geometría" else null,
                if (r.criteriaN > 0 && r.firstReady > r.criteriaOk) "${r.zone.id}: dispara con fotos que no cumplen los criterios clínicos" else null,
                if (r.unexplained > 0) "${r.zone.id}: ${r.unexplained} rechazos sin indicación" else null,
                if (r.steadyMovingReady > 0) "${r.zone.id}: dispara moviendo el teléfono (${r.steadyMovingReady}/${r.steadyN})" else null,
                if ((r.wrongViewReady ?: 0) > 0) "${r.zone.id}: dispara con la vista equivocada (${r.wrongViewReady})" else null,
            )
        }
        println(problems.joinToString("\n", "PROBLEMAS:\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * Lo que la app identifica sobre cada foto de prueba, por zona (build/reports/zone_overlays/):
     * contornos de la cara (óvalo, ojos, cejas, nariz, labios) y pupilas, esqueleto del cuerpo, áreas
     * donde se mide la luz, regiones de contorno de la guía (elipses, donde se buscan los bordes) y
     * bordes de referencia, con el resultado del disparo de la primera foto.
     */
    @Test
    fun drawOverlays() {
        assumeTrue(File(root, "rostro_frontal/landmarks.json").exists())
        val out = File("build/reports/zone_overlays").apply { mkdirs() }
        val cache = HashMap<String, List<Sample>>()
        for (z in Zone.entries) {
            val spec = ZoneAlignment.of(z)
            val ss = samplesFor(z, cache.getOrPut(dirOf(z)) { load(dirOf(z)) }).filter { !it.file.name.startsWith("pm_") }.take(12)
            if (ss.isEmpty()) continue
            val tw = 300; val th = 400; val cols = 4; val rows = (ss.size + cols - 1) / cols
            val sheet = Bitmap.createBitmap(tw * cols, th * rows + 40, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(sheet); c.drawColor(android.graphics.Color.WHITE)
            val title = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = android.graphics.Color.BLACK; isFakeBoldText = true }
            c.drawText("${z.id} · detector ${spec.detector}", 10f, 28f, title)
            ss.forEachIndexed { k, s0 ->
                val b = bmp(s0.file); val det = detOf(s0, z)
                val tile = Bitmap.createScaledBitmap(b, tw, th, true).copy(Bitmap.Config.ARGB_8888, true)
                drawOverlay(android.graphics.Canvas(tile), tw, th, z, s0, det, b)
                c.drawBitmap(tile, (k % cols * tw).toFloat(), (40 + k / cols * th).toFloat(), null)
            }
            File(out, "${z.id}.jpg").outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }
    }

    private fun drawOverlay(c: android.graphics.Canvas, w: Int, h: Int, z: Zone, s: Sample, det: Detection, src: Bitmap) {
        val spec = ZoneAlignment.of(z)
        fun P(p: Pt) = android.graphics.PointF(p.x * w, p.y * w)     // puntos en unidades del ancho
        fun paint(col: Int, sw: Float = 2f, fill: Boolean = false) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = col; strokeWidth = sw; style = if (fill) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE }
        // regiones de contorno de la guía (donde se buscan los bordes con referencia)
        val reg = paint(0x9900BCD4.toInt(), 2f)
        spec.contours.forEach { r ->
            c.drawOval(android.graphics.RectF((r.center.x - r.radius.x) * w, (r.center.y - r.radius.y) * w,
                (r.center.x + r.radius.x) * w, (r.center.y + r.radius.y) * w), reg)
        }
        // bordes de referencia (zonas sin puntos)
        if (!LiveMatcher.usesPoints(spec, det)) {
            val g = ImageOps.gray(src, LiveMatcher.EDGE_W, null)
            val dot = paint(0xFFFFEB3B.toInt(), 1f, true)
            ContourMatch.referencePoints(g, spec.contours).forEach { e ->
                c.drawCircle((e.x + 0.5f) / g.width * w, (e.y + 0.5f) / g.height * h, 1.6f, dot)
            }
        }
        // cara: contornos por grupo
        val groups = det.points.keys.filter { it.matches(Regex("[a-z_]+_\\d+")) }.groupBy { it.substringBeforeLast("_") }
        val faceCols = mapOf("oval" to 0xFF00E676.toInt(), "eye_l" to 0xFF29B6F6.toInt(), "eye_r" to 0xFF29B6F6.toInt(),
            "nose_bridge" to 0xFFFFA726.toInt(), "nose_bottom" to 0xFFFFA726.toInt())
        if (spec.detector == Detector.FACE) groups.forEach { (g, keys) ->
            val pts = keys.sortedBy { it.substringAfterLast("_").toInt() }.mapNotNull { det.points[it] }
            if (pts.size < 2) return@forEach
            val col = faceCols[g] ?: if (g.startsWith("brow")) 0xFFAB47BC.toInt() else if (g.startsWith("lip")) 0xFFEF5350.toInt() else 0xFFFFFFFF.toInt()
            val path = android.graphics.Path().apply { moveTo(P(pts[0]).x, P(pts[0]).y); pts.drop(1).forEach { lineTo(P(it).x, P(it).y) }; if (g == "oval" || g.startsWith("eye")) close() }
            c.drawPath(path, paint(col, 2f))
        }
        if (spec.detector == Detector.FACE) listOf(com.photomed.app.align.Landmarks.EYE_L, com.photomed.app.align.Landmarks.EYE_R)
            .mapNotNull { det.points[it] }.forEach { c.drawCircle(P(it).x, P(it).y, 3f, paint(0xFFFFFFFF.toInt(), 1f, true)) }
        // cuerpo: esqueleto
        if (spec.detector == Detector.POSE) {
            val L = com.photomed.app.align.Landmarks
            val bones = listOf(L.SHOULDER_L to L.SHOULDER_R, L.SHOULDER_L to L.ELBOW_L, L.ELBOW_L to L.WRIST_L, L.SHOULDER_R to L.ELBOW_R,
                L.ELBOW_R to L.WRIST_R, L.SHOULDER_L to L.HIP_L, L.SHOULDER_R to L.HIP_R, L.HIP_L to L.HIP_R, L.HIP_L to L.KNEE_L,
                L.KNEE_L to L.ANKLE_L, L.HIP_R to L.KNEE_R, L.KNEE_R to L.ANKLE_R, L.ANKLE_L to L.HEEL_L, L.ANKLE_R to L.HEEL_R,
                L.HEEL_L to L.FOOT_INDEX_L, L.HEEL_R to L.FOOT_INDEX_R, L.WRIST_L to L.INDEX_L, L.WRIST_R to L.INDEX_R)
            val bp = paint(0xFF00E676.toInt(), 3f)
            bones.forEach { (a, b) -> val pa = det.points[a]; val pb = det.points[b]; if (pa != null && pb != null) c.drawLine(P(pa).x, P(pa).y, P(pb).x, P(pb).y, bp) }
            det.points.values.forEach { c.drawCircle(P(it).x, P(it).y, 3.5f, paint(0xFFFF4081.toInt(), 1f, true)) }
        }
        // luz: áreas de medida (rostro)
        if (spec.detector == Detector.FACE && det.points.size >= LiveDecision.MIN_POINTS) {
            val (a, regional) = Standardization.lightAreas(s.w, s.h, det.points, det.yaw)
            val lp = paint(if (regional) 0xFFFF9800.toInt() else 0xFFFFFF00.toInt(), 2f)
            listOf(a.first, a.second, a.third).forEach { ar ->
                val cx = ar.center.x * w; val cy = ar.center.y * w; val r = ar.radius * w
                c.drawRect(cx - r, cy - r, cx + r, cy + r, lp)
            }
        }
        // resultado de la primera foto
        val e = first(z, s); val crit = if (com.photomed.app.align.ZoneCriteria.applies(z)) criteria(z, s) else emptyList()
        val ok = AutoCapture.ready(e) && crit.isEmpty()
        val label = if (ok) "Dispara" else (crit.firstOrNull() ?: e.hints.firstOrNull())?.take(34) ?: e.status.name
        val bg = paint(if (ok) 0xCC2E7D32.toInt() else 0xCCB71C1C.toInt(), 1f, true)
        c.drawRect(0f, h - 30f, w.toFloat(), h.toFloat(), bg)
        c.drawText(label, 6f, h - 9f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 16f })
    }
}
