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
}
