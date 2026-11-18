package com.photomed.app

import com.photomed.app.align.ContourMatch
import com.photomed.app.align.Detection
import com.photomed.app.align.GrayImage
import com.photomed.app.align.LiveDecision
import com.photomed.app.align.LiveMatch
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Zone
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Falsos positivos / negativos con fotos reales (no sintéticas):
 * - Rostros de LFW (Labeled Faces in the Wild, 13 233 fotos de 5 749 personas); puntos extraídos con
 *   MediaPipe Face Landmarker y agrupados como los contornos de ML Kit (tools/eval).
 * - "Retoma": misma foto con temblor real de cámara (≤ 1 % despl., ≤ 1,5 % escala, ≤ 1°, brillo, ruido)
 *   y detección repetida → debe coincidir.
 * - Desalineación moderada / grande → no debe coincidir.
 * - Otra persona con el mismo encuadre (LFW está alineado) → no debe coincidir.
 * - 52 fotos sin paciente (stock aleatorias de Unsplash vía Picsum, texturas reales de piso/ladrillo/
 *   césped/grava de scikit-image): el detector no encuentra rostro → nunca coincide.
 */
class FaceMatchingRealImagesTest {
    private val data = JSONObject(javaClass.getResourceAsStream("/eval/face_matching.json")!!.bufferedReader().readText())
    private val faces = data.getJSONObject("faces")
    private val spec = ZoneAlignment.of(Zone.FACE_FRONTAL)

    private fun det(o: JSONObject?): Detection {
        if (o == null) return Detection.EMPTY
        val p = o.getJSONObject("pts")
        val pts = p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val e = o.optJSONArray("euler")
        return Detection(pts, e?.getDouble(0)?.toFloat(), e?.getDouble(1)?.toFloat(), e?.getDouble(2)?.toFloat())
    }

    private fun matches(ref: Detection, cur: Detection, thr: Float = LiveMatch.DEFAULT_THRESHOLD) =
        LiveDecision.evaluate(spec, ref, cur, thr, wasMatched = false).status == MatchStatus.MATCH

    @Test
    fun landmarkMatchingOnRealFaces() {
        val pert = data.getJSONArray("perturb")
        val byLevel = mutableMapOf<String, MutableList<Boolean>>()
        for (i in 0 until pert.length()) {
            val f = pert.getJSONObject(i)
            val ok = matches(det(faces.getJSONObject(f.getString("ref"))), det(f.optJSONObject("cur")))
            byLevel.getOrPut(f.getString("level")) { mutableListOf() } += ok
        }
        val rate = byLevel.mapValues { (_, v) -> v.count { it } / v.size.toFloat() }
        val diff = data.getJSONArray("diff")
        val fp = (0 until diff.length()).count { i ->
            val p = diff.getJSONArray(i)
            matches(det(faces.getJSONObject(p.getString(0))), det(faces.getJSONObject(p.getString(1))))
        } / diff.length().toFloat()
        val refs = faces.keys().asSequence().take(60).map { det(faces.getJSONObject(it)) }.toList()
        val negFp = refs.count { matches(it, Detection.EMPTY) }
        println("Coincide → retoma ${rate["retoma"]}, moderada ${rate["moderada"]}, grande ${rate["grande"]}; " +
            "otra persona FP $fp; sin rostro FP $negFp/${refs.size}")
        assertTrue("falsos negativos en retoma: ${rate["retoma"]}", rate["retoma"]!! >= 0.98f)
        assertTrue("desalineación grande aceptada: ${rate["grande"]}", rate["grande"]!! == 0f)
        assertTrue("desalineación moderada: ${rate["moderada"]}", rate["moderada"]!! <= 0.15f)
        assertTrue("otra persona aceptada: $fp", fp <= 0.01f)
        assertTrue(negFp == 0)
    }

    private fun pgm(name: String): GrayImage {
        val b = javaClass.getResourceAsStream("/eval/gray/$name")!!.readBytes()
        var i = 0; var fields = 0
        while (fields < 4) { while (b[i].toInt().toChar().isWhitespace()) i++; while (!b[i].toInt().toChar().isWhitespace()) i++; fields++ }
        i++
        val g = GrayImage(96, 128)
        for (k in 0 until 96 * 128) g.data[k] = (b[i + k].toInt() and 0xFF) / 255f
        return g.standardized()
    }

    @Test
    fun contourMatchingOnRealImagesAndTextures() {
        val negatives = data.getJSONArray("negatives").let { a -> (0 until a.length()).map { "neg_" + a.getString(it).substringBeforeLast('.') + ".pgm" } }
        val negGray = negatives.map { pgm(it) }
        var retakeOk = 0; var shiftOk = 0; var negHits = 0; var negTotal = 0; var maxNeg = 0f
        for (i in 0 until 40) {
            val ref = ContourMatch.referencePoints(pgm("ref_%02d.pgm".format(i)), spec.contours)
            if (ContourMatch.fraction(ref, pgm("retake_%02d.pgm".format(i))) >= LiveMatch.DEFAULT_THRESHOLD) retakeOk++
            if (ContourMatch.fraction(ref, pgm("shift8_%02d.pgm".format(i))) >= LiveMatch.DEFAULT_THRESHOLD) shiftOk++
            for (n in negGray) {
                val f = ContourMatch.fraction(ref, n); maxNeg = maxOf(maxNeg, f)
                if (f >= LiveMatch.DEFAULT_THRESHOLD) negHits++
                negTotal++
            }
        }
        println("Contornos → retoma $retakeOk/40, despl. 8 pct $shiftOk/40, sin paciente $negHits/$negTotal (máx. " + "%.2f".format(maxNeg) + ")")
        assertTrue("retoma por contornos: $retakeOk/40", retakeOk >= 36)
        assertTrue("desplazamiento 8 % aceptado: $shiftOk/40", shiftOk <= 2)
        assertTrue("piso/texturas aceptados: $negHits", negHits == 0)
    }
}
