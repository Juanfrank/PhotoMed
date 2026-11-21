package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.FeatureMetrics
import com.photomed.app.align.GuideMigration
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.MeasureGuide
import com.photomed.app.align.Pt
import com.photomed.app.align.StoredTrace
import com.photomed.app.align.TraceJson
import com.photomed.app.align.TraceSet
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trazos confirmados de versiones anteriores: cada tramo vuelve a su fila. */
class GuideMigrationTest {
    private fun pts(vararg p: Float) = JSONArray().apply { p.forEach { put(it.toDouble()) } }
    private fun metrics(m: LineMetrics) = TraceJson.metrics(m)

    /** JSON como el de 2.25–2.26: tramos sin «k». */
    @Test fun keysFromOldGuidesByShortNameOrValue() {
        val nose = LineMetrics("Nariz", scale = "iris", features = listOf("Ancho alar, mm" to 34.2f, "Ancho del puente (raíz), mm" to 10.7f,
            "Ancho del dorso (mitad), mm" to 9.9f, "Longitud del dorso (raíz → punta), mm" to 36.1f, "Alto (entre los ojos → subnasal), mm" to 50.8f))
        val guides = JSONArray()
        listOf("ancho alar" to 34.2f, "puente" to 10.7f, "dorso" to 9.9f, "longitud" to 36.1f, "otro nombre" to 50.8f).forEach { (s, v) ->
            guides.put(JSONObject().put("l", FeatureMetrics.mmText(s, v)).put("p", pts(0.1f, 0.1f, 0.2f, 0.2f))) }
        val json = JSONObject().put("v", 1).put("aspect", 1.3333).put("at", 1L).put("traces", JSONArray().put(
            JSONObject().put("layer", "NOSE").put("pts", pts(0.4f, 0.5f, 0.6f, 0.5f)).put("m", metrics(nose)).put("guides", guides))).toString()
        val t = TraceJson.read(json)!!.traces.single()
        assertEquals(listOf("Ancho alar", "Ancho del puente (raíz)", "Ancho del dorso (mitad)", "Longitud del dorso (raíz → punta)",
            "Alto (entre los ojos → subnasal)"), t.guides.map { it.key })   // el último, por su valor
    }

    /** JSON como el de 2.23–2.24: sin tramos; arrugas, ojeras y bolsas los recuperan de su trazo. */
    @Test fun guidesRebuiltFromTheStoredTraces() {
        val fold = LineMetrics("Surco nasogeniano derecho", lengthMm = 40f, widthMm = 2f, depthMm = 0.4f)
        val dark = LineMetrics("Ojera derecha", areaMm2 = 120f, deltaL = 6f)
        val bag = LineMetrics("Bolsa palpebral derecha", scale = "iris", features = listOf("Altura (párpado → surco inferior), mm" to 7f,
            "Longitud del surco inferior, mm" to 20f, "Grado orientativo (0–3)" to 2f))
        fun tr(layer: String, p: JSONArray, m: LineMetrics, filled: Boolean) = JSONObject().put("layer", layer).put("pts", p).put("m", metrics(m))
            .put("closed", filled).put("filled", filled)
        val crescent = pts(0.30f, 0.40f, 0.35f, 0.40f, 0.40f, 0.40f, 0.40f, 0.41f, 0.35f, 0.43f, 0.30f, 0.41f)
        val json = JSONObject().put("v", 1).put("aspect", 1.3333).put("at", 1L).put("traces", JSONArray()
            .put(tr("FOLDS", pts(0.4f, 0.6f, 0.42f, 0.65f, 0.44f, 0.7f, 0.46f, 0.75f), fold, false))
            .put(tr("DARK_CIRCLES", crescent, dark, true)).put(tr("EYE_BAGS", crescent, bag, true))).toString()
        val s = TraceJson.read(json)!!
        val g = s.traces.associate { it.name to it.guides }
        assertEquals(listOf("Ancho (a media profundidad)"), g.getValue("Surco nasogeniano derecho").map { it.key })
        assertEquals(2, g.getValue("Ojera derecha").size)
        assertEquals(listOf("Altura (párpado → surco inferior)", "Longitud del surco inferior"), g.getValue("Bolsa palpebral derecha").map { it.key })
        assertTrue(g.getValue("Bolsa palpebral derecha")[0].label.contains("7"))
    }

    /** Rasgos sin tramos: se toman de un análisis nuevo, con las cifras confirmadas en el texto. */
    @Test fun featureGuidesAdoptedWithConfirmedValues() {
        val m = LineMetrics("Ojo derecho", scale = "iris", features = listOf("Ancho hendidura, mm" to 30f, "Alto hendidura, mm" to 10f))
        val set = TraceSet(4f / 3f, listOf(StoredTrace(AnnotLayer.EYES, listOf(Pt(0.3f, 0.4f), Pt(0.4f, 0.4f)), true, false, false, m)))
        assertTrue(GuideMigration.needsFeatureGuides(set))
        val fresh = AnnotLine(AnnotLayer.EYES, emptyList(), true, metrics = m.copy(features = listOf("Ancho hendidura, mm" to 31f)),
            guides = listOf(MeasureGuide(FeatureMetrics.mmText("ancho", 31f), Pt(0.3f, 0.4f), Pt(0.4f, 0.4f), "Ancho hendidura"),
                MeasureGuide(FeatureMetrics.mmText("alto", 11f), Pt(0.35f, 0.39f), Pt(0.35f, 0.41f), "Alto hendidura")))
        val out = GuideMigration.adoptFeatureGuides(set, listOf(fresh))
        val gs = out.traces.single().guides
        assertEquals(FeatureMetrics.mmText("ancho", 30f), gs[0].label)   // la cifra confirmada, no la nueva
        assertEquals(FeatureMetrics.mmText("alto", 10f), gs[1].label)
        assertTrue(!GuideMigration.needsFeatureGuides(out))
    }
}
