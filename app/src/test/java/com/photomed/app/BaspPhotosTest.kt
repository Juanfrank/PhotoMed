package com.photomed.app

import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.FeatureMetrics
import com.photomed.app.align.HairDensity
import com.photomed.app.align.HairSkin
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.hypot

/**
 * Tipo básico BASP de la línea frontal ([HairDensity.baspBasic]) en retratos reales: CelebA (atributo
 * «Receding_Hairline» etiquetado por personas, hombres sin flequillo ni gorro) y los retratos frontales de
 * la evaluación de zonas. Malla y segmentación de `tools/eval/hair_labels.py`
 * (`stock/zones/eval_basp/labels.json`; las fotos no se suben). Informe en `build/reports/basp_photos.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaspPhotosTest {
    private val root = File("src/test/resources/stock/zones/eval_basp")

    data class Row(val key: String, val center: Float?, val temple: Float?, val type: String?, val hair: List<Pt> = emptyList())

    private fun analyze(): List<Row> {
        val js = JSONObject(File(root, "labels.json").readText())
        return js.keys().asSequence().sorted().map { key ->
            val o = js.getJSONObject(key); val w = o.getInt("w"); val h = o.getInt("h")
            val meshJs = o.optJSONArray("mesh") ?: return@map Row(key, null, null, null)
            val mesh = (0 until meshJs.length()).map { i -> meshJs.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w) } }
            val labels = FaceLabels(w, h, java.util.Base64.getDecoder().decode(o.getString("labels")))
            val keys = FaceAnnotations.keysFromMesh(mesh)
            val hair = HairSkin.hairline(labels, keys)?.points ?: return@map Row(key, null, null, null)
            val ipd = hypot(mesh[468].x - mesh[473].x, mesh[468].y - mesh[473].y)
            val m = FeatureMetrics.hairlineM(mesh, hair, 63f / ipd, "interpupilar")?.metrics ?: return@map Row(key, null, null, null)
            val f = m.features.toMap()
            val c = f["Frente central (cejas → pelo), mm"]
            val tr = f["Sobre la cola de la ceja derecha (canto externo), mm"]; val tl = f["Sobre la cola de la ceja izquierda (canto externo), mm"]
            Row(key, c, if (tr != null && tl != null) (tr + tl) / 2 else null, m.type, hair)
        }.toList()
    }

    @Test
    fun recedingHairlinesClassifiedFromRealPortraits() {
        assumeTrue("sin fotos", File(root, "labels.json").exists())
        val rows = analyze()
        val rep = StringBuilder("# Tipo básico BASP en retratos\n\n| foto | frente central | entradas | tipo |\n|---|---|---|---|\n")
        rows.forEach { r -> rep.append("| ${r.key} | ${r.center?.let { "%.0f mm".format(it) } ?: "–"} | ${r.temple?.let { "%.0f mm".format(it) } ?: "–"} | ${r.type ?: "sin medir"} |\n") }
        fun group(tag: String) = rows.filter { it.key.contains("/ce_${tag}_") }
        fun receding(r: Row) = r.type != null && !r.type.startsWith("BASP L")
        val rec = group("rec").filter { it.type != null }; val nor = group("nor").filter { it.type != null }
        val tpr = rec.count(::receding) / rec.size.coerceAtLeast(1).toFloat()
        val tnr = nor.count { !receding(it) } / nor.size.coerceAtLeast(1).toFloat()
        val front = rows.filter { it.key.startsWith("rostro_frontal/") && it.type != null }
        rep.append("\nCelebA, con entradas: %d/%d medidas, %.0f %% no L; sin entradas: %d/%d medidas, %.0f %% L; exactitud equilibrada %.0f %%\n"
            .format(rec.size, group("rec").size, tpr * 100, nor.size, group("nor").size, tnr * 100, (tpr + tnr) * 50))
        rep.append("Retratos frontales de la evaluación: %d medidos, %.0f %% L\n".format(front.size, front.count { !receding(it) } * 100f / front.size.coerceAtLeast(1)))
        File("build/reports").mkdirs(); File("build/reports/basp_photos.md").writeText(rep.toString())
        File("build/reports/basp_hairlines.json").writeText(org.json.JSONObject(rows.associate { r ->
            r.key to org.json.JSONArray(r.hair.map { org.json.JSONArray(listOf(it.x.toDouble(), it.y.toDouble())) }) }).toString())
        println(rep)
        assumeTrue(rec.size >= 10 && nor.size >= 10)
        assert((tpr + tnr) / 2 >= 0.7f) { "exactitud equilibrada ${(tpr + tnr) / 2}" }
    }
}
