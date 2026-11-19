package com.photomed.app

import com.photomed.app.align.Detection
import com.photomed.app.align.Detector
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Standardization
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.SessionTemplates
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
        assertTrue(SessionTemplates.BUILT_IN.any { Zone.SCALP_VERTEX in it.zones })
    }

    @Test
    fun noFaceHintOnScalp() {
        // la cabeza vista desde arriba no tiene rostro: no debe pedir "no se detecta el rostro"
        val e = Standardization.evaluate(Zone.SCALP_VERTEX, Detection.EMPTY, null)
        assertEquals(MatchStatus.SEARCHING, e.status)
        assertTrue(e.hints.isEmpty())
    }

    @Test
    fun overheadCameraCueIsAnIcon() {
        val c = HintCues.of("Cámara: cenital, inclinar 20°")
        assertNotNull(c)
        assertEquals("20°", c!!.value)
    }
}
