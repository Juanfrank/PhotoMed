package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.EyeComposite
import com.photomed.app.align.FaceCrop
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot

/**
 * Diagrama del ojo solo frente al mismo segmento del diagrama de la cara entera, en recortes de ojo de caras con malla
 * conocida (locales, no versionados; mallas en ojo/eye_meshes.json). Por capa: cuántas líneas da cada uno y la
 * distancia media entre las líneas del mismo nombre (en anchos de ojo). Informe: build/reports/eye_diagram.md y .png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EyeDiagramCompareTest {
    private val dir = File("src/test/resources/stock/zones/ojo")

    private fun mesh(a: org.json.JSONArray) = (0 until a.length()).map { a.getJSONArray(it).let { p -> Pt(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) } }

    /** Distancia media de los puntos de [a] a la polilínea [b] (unidades de entrada). */
    private fun chamfer(a: List<Pt>, b: List<Pt>): Float = a.map { p ->
        b.zipWithNext().minOfOrNull { (u, v) ->
            val dx = v.x - u.x; val dy = v.y - u.y; val l2 = dx * dx + dy * dy
            val t = if (l2 < 1e-12f) 0f else (((p.x - u.x) * dx + (p.y - u.y) * dy) / l2).coerceIn(0f, 1f)
            hypot(p.x - (u.x + dx * t), p.y - (u.y + dy * t))
        } ?: hypot(p.x - b[0].x, p.y - b[0].y)
    }.average().toFloat()

    @Test fun singleEyeAgainstFullFace() {
        val mf = File(dir, "eye_meshes.json"); assumeTrue(mf.exists())
        val js = JSONObject(mf.readText())
        val stats = HashMap<String, MutableList<Float>>(); val countA = HashMap<String, Int>(); val countB = HashMap<String, Int>(); val countB0 = HashMap<String, Int>()
        val tiles = ArrayList<Bitmap>()
        for (name in js.keys().asSequence().sorted()) {
            val img = BitmapFactory.decodeFile(File(dir, name).path) ?: continue
            val e = js.getJSONObject(name)
            val full = mesh(e.getJSONArray("full")); val compM = mesh(e.getJSONArray("comp"))
            val w = img.width.toFloat()
            fun inPhoto(l: AnnotLine) = l.points.map { it.x }.average() in 0.0..1.0 && l.points.map { it.y * w / img.height }.average() in 0.0..1.0
            // referencia: la cara entera en la foto original, llevada al recorte del ojo
            val orig = e.optString("orig").takeIf { it.isNotEmpty() }?.let { BitmapFactory.decodeFile(File("src/test/resources/stock/$it").path) } ?: continue
            val om = mesh(e.getJSONArray("origMesh")); val mp = e.getJSONArray("map")
            val sc = mp.getDouble(0).toFloat(); val tx = mp.getDouble(1).toFloat(); val ty = mp.getDouble(2).toFloat(); val ow = orig.width.toFloat()
            val a = FaceCrop.annotate(om, orig, { _, _ -> null }, invalid = { false })
                .map { l -> l.mapped { q -> Pt((sc * q.x * ow + tx) / w, (sc * q.y * ow + ty) / w) } }
                .filter { it.layer in EyeComposite.LAYERS && inPhoto(it) }
            val (comp, lay) = EyeComposite.build(img, false)
            val b0 = FaceCrop.annotate(compM, comp, { _, _ -> null }).filter { it.layer in EyeComposite.LAYERS && lay.isReal(it.points) }.map { it.mapped(lay::toPhoto) }
            val b = EyeComposite.keep(FaceCrop.annotate(compM, comp, { _, _ -> null }, invalid = lay::isPad), lay)
            val ew = hypot(full[33].x - full[133].x, full[33].y - full[133].y)
            fun key(l: AnnotLine) = l.layer.name + ":" + (l.metrics?.name?.substringBefore(" (") ?: if (l.closed) "cerrada" else "abierta")
            a.groupBy(::key).forEach { (k, la) -> countA[k] = (countA[k] ?: 0) + la.size }
            b.groupBy(::key).forEach { (k, lb) -> countB[k] = (countB[k] ?: 0) + lb.size }
            b0.groupBy(::key).forEach { (k, lb) -> countB0[k] = (countB0[k] ?: 0) + lb.size }
            for (la in a) {
                val cands = b.filter { key(it) == key(la) }
                if (cands.isEmpty()) continue
                val d = cands.minOf { chamfer(la.points, it.points) } / ew
                stats.getOrPut(key(la)) { ArrayList() } += d
            }
            if (tiles.size < 12) {
                fun draw(lines: List<AnnotLine>): Bitmap {
                    val o = img.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(o)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
                    for (l in lines) {
                        p.color = when (l.layer) { AnnotLayer.EYES -> Color.CYAN; AnnotLayer.CROWS_FEET -> Color.GREEN; AnnotLayer.DARK_CIRCLES -> Color.MAGENTA
                            AnnotLayer.EYE_BAGS -> Color.YELLOW; AnnotLayer.INFRAORBITAL -> Color.WHITE; AnnotLayer.FOLDS -> Color.rgb(255, 128, 0); else -> Color.RED }
                        val pts = if (l.closed) l.points + l.points.first() else l.points
                        for (i in 1 until pts.size) c.drawLine(pts[i - 1].x * w, pts[i - 1].y * w, pts[i].x * w, pts[i].y * w, p)
                    }
                    return o
                }
                val pa = draw(a); val pb = draw(b)
                val t = Bitmap.createBitmap(img.width * 2 + 4, img.height, Bitmap.Config.ARGB_8888)
                Canvas(t).apply { drawColor(Color.BLACK); drawBitmap(pa, 0f, 0f, null); drawBitmap(pb, img.width + 4f, 0f, null) }
                tiles += Bitmap.createScaledBitmap(t, 480, 480 * t.height / t.width, true)
            }
            comp.recycle()
        }
        val rep = StringBuilder("# Ojo solo frente al segmento del ojo de la cara entera\n\n| línea | cara entera | ojo solo (antes) | ojo solo | distancia media (anchos de ojo) |\n|---|---|---|---|---|\n")
        (countA.keys + countB.keys).toSortedSet().forEach { k ->
            rep.append("| $k | ${countA[k] ?: 0} | ${countB0[k] ?: 0} | ${countB[k] ?: 0} | ${stats[k]?.let { "%.2f".format(it.average()) } ?: "—"} |\n")
        }
        File("build/reports").mkdirs(); File("build/reports/eye_diagram.md").writeText(rep.toString())
        if (tiles.isNotEmpty()) {
            val th = tiles.maxOf { it.height }; val cols = 3
            val sheet = Bitmap.createBitmap(480 * cols, th * ((tiles.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
            Canvas(sheet).apply { tiles.forEachIndexed { i, t -> drawBitmap(t, (i % cols) * 480f, (i / cols) * th.toFloat(), null) } }
            File("build/reports/eye_diagram.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        println(rep)
    }
}
