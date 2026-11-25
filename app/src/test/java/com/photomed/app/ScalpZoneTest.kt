package com.photomed.app

import com.photomed.app.align.Detection
import com.photomed.app.align.Detector
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Standardization
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Zone
import com.photomed.app.data.ZoneCategory
import com.photomed.app.ui.camera.HintCues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.photomed.app.align.AnnotLayer as L

/** Vista cenital del cuero cabelludo (tricología). */
class ScalpZoneTest {
    @Test
    fun scalpZoneIsCapillaryWithoutFaceDetector() {
        val z = Zone.fromId("cuero_cabelludo_vertice")
        assertEquals(Zone.SCALP_VERTEX, z)
        assertEquals(ZoneCategory.CAPILAR, z!!.category)
        val spec = ZoneAlignment.of(z)
        assertEquals(Detector.NONE, spec.detector)
        assertTrue(spec.contours.isNotEmpty())
        assertTrue(com.photomed.app.data.BuiltInProtocols.ALL.any { Zone.SCALP_VERTEX in it.second })
    }

    @Test
    fun noFaceHintOnScalp() {
        // la cabeza vista desde arriba no tiene rostro: no debe pedir "no se detecta el rostro"
        val e = Standardization.evaluate(Zone.SCALP_VERTEX, Detection.EMPTY, null)
        assertEquals(MatchStatus.SEARCHING, e.status)
        assertTrue(e.hints.isEmpty())
    }

    @Test
    fun cameraAngleHintsPerScalpView() {
        // vértice cenital (90°): con el teléfono a 70° hay que apuntar más abajo
        assertEquals("Cámara: apuntar más abajo 20°", Standardization.cameraAngleHint(Zone.SCALP_VERTEX, 70f))
        assertEquals(null, Standardization.cameraAngleHint(Zone.SCALP_VERTEX, 86f))
        // parietal ≈ 45°: a 80° sobra inclinación
        assertEquals("Cámara: apuntar más arriba 35°", Standardization.cameraAngleHint(Zone.SCALP_PARIETAL_L, 80f))
        assertEquals(null, Standardization.cameraAngleHint(Zone.SCALP_CROWN, 38f))
        assertEquals(null, Standardization.cameraAngleHint(Zone.EYE_R, 0f))
        val c = HintCues.of("Cámara: apuntar más abajo 20°")
        assertNotNull(c)
        assertEquals("20°", c!!.value)
    }

    /** Rostro sintético de frente (óvalo simétrico, ojos nivelados, nariz centrada) con el cabeceo dado. */
    private fun face(pitch: Float): Detection {
        val pts = HashMap<String, com.photomed.app.align.Pt>()
        for (i in 0 until 36) { val a = 2 * Math.PI * i / 36; pts["oval_$i"] = com.photomed.app.align.Pt(0.5f + 0.25f * kotlin.math.sin(a).toFloat(), 0.6f - 0.32f * kotlin.math.cos(a).toFloat()) }
        pts[com.photomed.app.align.Landmarks.EYE_R] = com.photomed.app.align.Pt(0.42f, 0.55f); pts[com.photomed.app.align.Landmarks.EYE_L] = com.photomed.app.align.Pt(0.58f, 0.55f)
        pts["nose_bottom_1"] = com.photomed.app.align.Pt(0.5f, 0.68f)
        return Detection(pts, yaw = 0f, pitch = pitch, roll = 0f)
    }

    @Test
    fun frontalTiltedScalpViewAsksForTheForwardTilt() {
        val z = Zone.fromId("cuero_cabelludo_frontal")
        assertEquals(Zone.SCALP_FRONTAL, z)
        assertEquals(ZoneCategory.CAPILAR, z!!.category)
        assertEquals(Detector.FACE, ZoneAlignment.of(z).detector)
        assertTrue(com.photomed.app.data.BuiltInProtocols.ALL.any { Zone.SCALP_FRONTAL in it.second })
        // con la cabeza a nivel pide bajar la barbilla (inclinarla hacia delante)
        val level = Standardization.evaluate(z, face(0f), null)
        assertTrue(level.hints.toString(), level.hints.any { it.startsWith("Barbilla: bajar") })
        // inclinada ≈ 30° hacia delante: sin indicaciones de cabeza ni de mirada (los ojos miran abajo)
        val tilted = Standardization.evaluate(z, face(-30f), null)
        assertTrue(tilted.hints.toString(), tilted.hints.none { it.startsWith("Barbilla") || it.startsWith("Mirada") || it.startsWith("Expresión") })
        // demasiado inclinada: pide subirla
        assertTrue(Standardization.evaluate(z, face(-55f), null).hints.any { it.startsWith("Barbilla: subir") })
        // el rostro frontal sigue pidiendo la cabeza a nivel
        assertTrue(Standardization.evaluate(Zone.FACE_FRONTAL, face(-30f), null).hints.any { it.startsWith("Barbilla: bajar").not() && it.startsWith("Barbilla") })
    }

    @Test
    fun newZonesHaveIdsAndSides() {
        listOf(Zone.SCALP_CROWN, Zone.SCALP_PARIETAL_R, Zone.SCALP_PARIETAL_L).forEach { assertEquals(ZoneCategory.CAPILAR, it.category) }
        assertTrue(Zone.SCALP_PARIETAL_L.mirrored && !Zone.SCALP_PARIETAL_R.mirrored)
        assertTrue(Zone.EYE_L.mirrored && !Zone.EYE_R.mirrored)
        assertEquals(Zone.EYE_L, Zone.fromId("ojo_izq"))
        assertTrue(Zone.entries.map { it.id }.toSet().size == Zone.entries.size)
    }

    /** Toma capilar frontal: la guía del modelo 3D se encaja en la cara por los ojos de la malla. */
    @Test
    fun frontalTiltGuideFollowsDetectedEyes() {
        val fit = com.photomed.app.ui.camera.ScalpFaceFit
        val model = com.photomed.app.ui.camera.ScalpGuideArt.FRONTAL_TILT_EYES
        val a = com.photomed.app.ui.camera.ScalpFaceFit.ASPECT
        fun pts(r: androidx.compose.ui.geometry.Offset, l: androidx.compose.ui.geometry.Offset) = mapOf(
            com.photomed.app.align.Landmarks.EYE_R to com.photomed.app.align.Pt(r.x, r.y / a),
            com.photomed.app.align.Landmarks.EYE_L to com.photomed.app.align.Pt(l.x, l.y / a))
        // ojos donde los del modelo: identidad y cara encuadrada
        val same = fit.of(pts(model[0], model[1]))!!
        val m = com.photomed.app.ui.camera.ScalpGuideArt.FRONTAL_TILT_MARKS[0]
        assertEquals(m.x, same.map(m).x, 1e-4f); assertEquals(m.y, same.map(m).y, 1e-4f)
        assertTrue(com.photomed.app.align.FaceFraming.matches(fit.framing(pts(model[0], model[1])), false))
        // cara desplazada y más pequeña: la marca de la línea de implantación la sigue (mitad de la distancia a los ojos)
        val off = androidx.compose.ui.geometry.Offset(0.05f, 0.04f)
        val mid = androidx.compose.ui.geometry.Offset((model[0].x + model[1].x) / 2, (model[0].y + model[1].y) / 2)
        fun shrink(o: androidx.compose.ui.geometry.Offset) = mid + (o - mid) * 0.5f + off
        val small = fit.of(pts(shrink(model[0]), shrink(model[1])))!!
        val got = small.map(m); val want = shrink(m)
        assertEquals(want.x, got.x, 1e-3f); assertEquals(want.y, got.y, 1e-3f)
        // ojos intercambiados (otra convención de lado): la misma guía, sin darla la vuelta
        val swapped = fit.of(pts(model[1], model[0]))!!.map(m)
        assertEquals(m.x, swapped.x, 1e-4f); assertEquals(m.y, swapped.y, 1e-4f)
        assertTrue(!com.photomed.app.align.FaceFraming.matches(fit.framing(pts(shrink(model[0]), shrink(model[1]))), false))
    }

    /** Alturas de la frente con la cabeza inclinada: llevadas a las de una toma a nivel. */
    @Test
    fun hairlineHeightsCorrectedForHeadTilt() {
        val f = com.photomed.app.align.FeatureMetrics
        assertEquals(1f, f.hairTiltScale(0f), 1e-5f)
        // a 30° la frente se ve casi de frente (más larga en la imagen): factor < 1
        val k = f.hairTiltScale(30f)
        assertTrue("k=$k", k in 0.93f..0.97f)
        // fuera de rango: acotado
        assertEquals(f.hairTiltScale(60f), f.hairTiltScale(90f), 1e-6f)
    }

    /** Visor: en la toma capilar inclinada solo contorno (sin medidas), línea del pelo, ojos y cejas. */
    @Test
    fun frontalTiltViewerKeepsHairLayersOnly() {

        val met = com.photomed.app.align.LineMetrics("x", features = listOf("a" to 1f))
        val p = listOf(com.photomed.app.align.Pt(0f, 0f), com.photomed.app.align.Pt(1f, 1f))
        val all = listOf(L.CONTOUR, L.HAIRLINE, L.EYES, L.FOREHEAD, L.CROWS_FEET, L.FOLDS, L.GLABELLA, L.NOSE, L.LIPS)
            .map { com.photomed.app.align.AnnotLine(it, p, false, metrics = met) }
        val out = com.photomed.app.align.PhotoAnnotator.scalpFrontLines(all, measured = true)
        assertEquals(listOf(L.CONTOUR, L.HAIRLINE, L.EYES), out.map { it.layer })
        assertEquals(null, out[0].metrics)
        assertNotNull(out[1].metrics)
        assertTrue(com.photomed.app.align.PhotoAnnotator.scalpFrontLines(all, measured = false).all { it.metrics == null })
    }

    /** Cabeza inclinada: la distancia punta nasal–mentón (relativa a la interpupilar) es criterio de peso. */
    @Test
    fun noseChinDistanceGatesTheTiltedScalpView() {
        fun face(noseChin: Float): Map<String, com.photomed.app.align.Pt> {
            val ipd = 0.2f
            return mapOf(com.photomed.app.align.Landmarks.EYE_R to com.photomed.app.align.Pt(0.4f, 0.5f),
                com.photomed.app.align.Landmarks.EYE_L to com.photomed.app.align.Pt(0.6f, 0.5f),
                "nose_bridge_1" to com.photomed.app.align.Pt(0.5f, 0.6f), "oval_18" to com.photomed.app.align.Pt(0.5f, 0.6f + noseChin * ipd))
        }
        val ok = com.photomed.app.align.MatchEval(MatchStatus.MATCH, 1f, emptyList(), threshold = 1f)
        // a nivel (1,42): no se dispara, pide inclinar más; la indicación del cabeceo se sustituye
        val level = Standardization.withNoseChin(ok.copy(hints = listOf("Barbilla: bajar 30° (Frankfort)")), face(1.42f), null)
        assertTrue(level.status != MatchStatus.MATCH)
        assertEquals(listOf("Cabeza: inclinar más hacia delante (nariz–mentón)"), level.hints)
        // a 30° (0,97): sin cambios
        assertEquals(ok, Standardization.withNoseChin(ok, face(0.97f), null))
        // demasiado inclinada
        assertTrue(Standardization.withNoseChin(ok, face(0.7f), null).hints.single().startsWith("Cabeza: inclinar menos"))
        // con la referencia del paciente (1,10): 0,97 ya no vale (−12 %), 1,12 sí
        val ref = face(1.10f)
        assertTrue(Standardization.withNoseChin(ok, face(0.97f), ref).hints.single().startsWith("Cabeza: inclinar menos"))
        assertEquals(ok, Standardization.withNoseChin(ok, face(1.12f), ref))
        assertEquals(1.10f, Standardization.noseChinRatio(ref)!!, 1e-4f)
    }
}
