package com.photomed.app

import com.photomed.app.align.MatchEval
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneCriteria
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.CameraGuidance
import com.photomed.app.ui.camera.HintCues
import com.photomed.app.ui.camera.isCameraMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mensajes de corrección de la cámara: se muestran, en el orden correcto y con la dirección correcta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class CorrectionMessagesTest {
    /** Los criterios de manos, pies y cuerpo no son movimientos de la cámara: se muestran (como texto). */
    @Test
    fun bodyCriteriaAreShownNotFilteredAsCameraMoves() {
        for (h in listOf("Mano a la izquierda de la imagen: que se vean las falanges de todos los dedos (anular)",
            "Pie a la derecha de la imagen: que se vean los dedos", "Codo extendido", "Descubra el brazo (se ve ropa o no se ve la piel)",
            "Separe un poco el brazo del cuerpo (≈ 20°)", "Rodillas extendidas", "Centre la zona en el cuadro"))
            assertTrue(h, !isCameraMove(h))
        assertNotNull("el centrado tiene icono", HintCues.of("Centre la zona en el cuadro"))
    }

    /** Una corrección real va antes que el estado («Comprobando la zona…», «Mantenga el teléfono quieto»). */
    @Test
    fun correctionsBeforeStatus() {
        val items = listOf("Comprobando la zona…", "Mantenga el teléfono quieto", "Codo extendido")
        assertEquals("Codo extendido", items.sortedBy(HintCues::priority).first())
    }

    /** Zona descentrada: la flecha indica hacia dónde mover el teléfono (cámara trasera). */
    @Test
    fun centeringLightsTheRightArrow() {
        // parte del cuerpo a la izquierda del cuadro: hay que mover el contenido a la derecha → teléfono a la izquierda
        val s = ZoneCriteria.Scene(mapOf("shoulder_r" to Pt(0.1f, 0.3f), "elbow_r" to Pt(0.12f, 0.6f), "wrist_r" to Pt(0.14f, 0.9f)), null, null)
        val (c, d) = ZoneCriteria.centerOffset(Zone.ARM_R, s)!!
        assertTrue("centro a la izquierda: $c", c.x < 0.3f && d.x > 0f)
        val g = CameraGuidance.of(MatchEval(MatchStatus.CLOSE, 0.5f, emptyList(), dx = d.x, dy = d.y), frontCamera = false, displayMirrored = false)!!
        assertEquals("teléfono a la izquierda", -1, g.moveX)
        // parte a la derecha: teléfono a la derecha
        val s2 = ZoneCriteria.Scene(mapOf("shoulder_r" to Pt(0.9f, 0.3f), "elbow_r" to Pt(0.88f, 0.6f), "wrist_r" to Pt(0.86f, 0.9f)), null, null)
        val d2 = ZoneCriteria.centerOffset(Zone.ARM_R, s2)!!.second
        assertEquals(1, CameraGuidance.of(MatchEval(MatchStatus.CLOSE, 0.5f, emptyList(), dx = d2.x, dy = d2.y), false, false)!!.moveX)
    }
}
