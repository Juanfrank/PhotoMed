package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.photomed.app.align.AlignOptions
import com.photomed.app.align.Aligner
import com.photomed.app.align.Detection
import com.photomed.app.align.Perspective
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Zone
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Alineación automática con fotos reales: retomas reales (la misma foto girada ±10° con los puntos
 * detectados de nuevo) y cambios sintéticos conocidos. Error = distancia media, dentro del óvalo
 * facial, entre la posición que da la alineación y la verdadera (% del ancho).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlignmentRealTest {
    private data class Face(val name: String, val bmp: Bitmap, val pts: Map<String, Pt>)

    private fun load(json: String, prefix: String, filter: (String) -> Boolean): Map<String, Face> {
        val o = JSONObject(json); val out = HashMap<String, Face>()
        for (k in o.keys()) {
            if (!filter(k)) continue
            val p = o.getJSONObject(k).optJSONObject("pts") ?: continue
            val pts = p.keys().asSequence().associateWith { kk -> p.getJSONArray(kk).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val bmp = javaClass.getResourceAsStream("$prefix$k.jpg")?.let { BitmapFactory.decodeStream(it) } ?: continue
            if (bmp.width.toFloat() / bmp.height !in 0.70f..0.80f) continue
            out[k] = Face(k, Bitmap.createScaledBitmap(bmp, 360, 480, true), pts)
        }
        return out
    }

    /** Rotación (°, + horaria en pantalla) alrededor del centro de la imagen, en unidades del ancho. */
    private fun rot(deg: Float) = Pt(0.5f, 0.6667f).let { c ->
        val t = Math.toRadians(deg.toDouble()).toFloat()
        Similarity(1f, t, c.x - (cos(t) * c.x - sin(t) * c.y), c.y - (sin(t) * c.x + cos(t) * c.y))
    }

    /** Puntos de muestra dentro del óvalo facial de la foto "antes". */
    private fun samples(p: Map<String, Pt>): List<Pt> {
        val oval = (0 until 36).mapNotNull { p["oval_$it"] }
        val cx = oval.map { it.x }.average().toFloat(); val cy = oval.map { it.y }.average().toFloat()
        return oval.flatMap { o -> listOf(0.3f, 0.6f, 0.9f).map { f -> Pt(cx + (o.x - cx) * f, cy + (o.y - cy) * f) } } + Pt(cx, cy)
    }

    /** Error (% ancho): T ∘ perspectiva(φ) ∘ verdad(antes→después) debería ser la identidad. */
    private fun error(t: Similarity, phi: Float, truth: (Pt) -> Pt, ss: List<Pt>): Float =
        ss.map { p -> val q = t.apply(Perspective.mapPt(truth(p), phi)); hypot(q.x - p.x, q.y - p.y) }.average().toFloat() * 100

    private val variants = listOf(
        "puntos" to (AlignOptions(points = true, contours = false, gaze = false) to 0f),
        "puntos+perspectiva" to (AlignOptions(points = true, contours = false, gaze = false) to null),
        "completa (puntos+perspectiva+contornos)" to (AlignOptions(points = true, contours = true, gaze = false) to null),
    )

    @Test
    fun automaticAlignmentImprovesRealRetakes() {
        val json = javaClass.getResourceAsStream("/stock/stock_faces.json")?.bufferedReader()?.readText()
        val stock = json?.let { load(it, "/stock/") { true } }.orEmpty()
        val spec = ZoneAlignment.of(Zone.FACE_FRONTAL)
        val report = StringBuilder()
        val sums = HashMap<String, Double>(); var n = 0
        val worse = mutableListOf<String>()
        fun run(label: String, before: Face, after: Bitmap, afterPts: Map<String, Pt>, truth: (Pt) -> Pt) {
            val ss = samples(before.pts)
            val none = error(Similarity.IDENTITY, 0f, truth, ss)
            sums["sin alinear"] = (sums["sin alinear"] ?: 0.0) + none
            val line = StringBuilder("%-40s sin alinear %5.2f".format(label, none))
            val errs = ArrayList<Float>()
            for ((name, cfg) in variants) {
                val r = Aligner.alignCore(spec, before.bmp, after, Detection(before.pts), Detection(afterPts), cfg.second, cfg.first)
                val e = error(r.transform, r.keystoneDeg, truth, ss)
                sums[name] = (sums[name] ?: 0.0) + e
                line.append(" · %s %5.2f (φ %+.1f°)".format(name, e, r.keystoneDeg))
                errs += e
            }
            // la alineación completa nunca debe empeorar la de solo puntos (ni la foto sin alinear)
            if (errs.last() > errs.first() + 0.05f || errs.last() > none) worse += line.toString()
            report.append(line).append('\n'); n++
        }
        // 1) retomas reales: la misma foto girada ±10° con los puntos detectados de nuevo
        val bases = stock.keys.filter { it.endsWith("_r0") }
        for (k in bases) {
            val b = stock[k]!!
            for (deg in listOf(10, -10)) {
                val a = stock[k.removeSuffix("_r0") + "_r$deg"] ?: continue
                // cv2: ángulo + = antihorario en pantalla
                val g = rot(-deg.toFloat())
                run("${k.take(22)} girada ${deg}°", b, a.bmp, a.pts) { p -> g.apply(p) }
            }
        }
        // 2) cambios sintéticos conocidos sobre las caras de demostración y de stock
        val demo = load(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText(), "/demo/") { true }
        val faces = (demo.values + bases.mapNotNull { stock[it] }).take(12)
        val cases = listOf(
            "escala ×1,08 · 4° · desplazada" to (Similarity(1.08f, Math.toRadians(4.0).toFloat(), 0.02f, -0.03f) to 0f),
            "escala ×0,92 · −3°" to (Similarity(0.92f, Math.toRadians(-3.0).toFloat(), 0.05f, 0.06f) to 0f),
            "desplazada + cámara inclinada 5°" to (Similarity(1.0f, 0f, 0.03f, 0.02f) to 5f),
        )
        for (f in faces) for ((label, c) in cases) {
            val (s, tilt) = c
            // después = antes con la semejanza s y la cámara inclinada (perspectiva −tilt)
            val m = android.graphics.Matrix().apply { setScale(s.scale, s.scale); postRotate(s.rotationDegrees); postTranslate(s.tx * 360, s.ty * 360) }
            var a = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(a).apply { drawColor(android.graphics.Color.DKGRAY); drawBitmap(f.bmp, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
            if (tilt != 0f) a = com.photomed.app.align.ImageOps.correctPerspective(a, -tilt, Perspective.DEFAULT_FOCAL)
            val truth = { p: Pt -> Perspective.mapPt(s.apply(p), -tilt) }
            val ap = f.pts.mapValues { truth(it.value) }
            run("${f.name.take(22)} · $label", f, a, ap, truth)
        }
        val summary = "MEDIA (%d casos): ".format(n) + sums.entries.sortedBy { it.key }.joinToString(" · ") { "%s %.2f".format(it.key, it.value / n) }
        report.append(summary)
        File("build/reports").mkdirs(); File("build/reports/alignment_real.txt").writeText(report.toString())
        println(summary)
        assertTrue(n > 0)
        assertTrue("La alineación completa empeora:\n" + worse.joinToString("\n"), worse.isEmpty())
    }
}
