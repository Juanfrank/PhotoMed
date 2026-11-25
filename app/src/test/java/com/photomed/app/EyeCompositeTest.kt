package com.photomed.app

import com.photomed.app.align.EyeComposite
import com.photomed.app.align.Pt
import com.photomed.app.align.usesFaceAnalysis
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ojo individual: foto + espejo para usar la sección del ojo de la malla facial (ver EyeComposite). */
class EyeCompositeTest {
    @Test fun pointsGoToTheCompositeAndBack() {
        for (mirrored in listOf(false, true)) {
            val l = EyeComposite.Layout(300, 400, mirrored)
            assertEquals(600, l.width); assertEquals(400 + 360, l.height)
            val p = Pt(0.3f, 0.9f)
            val c = l.toComposite(p)
            val back = l.toPhoto(c)
            assertEquals(p.x, back.x, 1e-5f); assertEquals(p.y, back.y, 1e-5f)
            // la foto real ocupa la mitad izquierda (ojo derecho) o la derecha (ojo izquierdo)
            assertTrue(if (mirrored) c.x > 0.5f else c.x < 0.5f)
        }
    }

    @Test fun onlyLinesOfTheRealEyeAreKept() {
        val l = EyeComposite.Layout(300, 400, mirrored = false)
        assertTrue(l.isReal(listOf(Pt(0.1f, 0.3f), Pt(0.4f, 0.3f))))        // mitad izquierda: el ojo real
        assertFalse(l.isReal(listOf(Pt(0.6f, 0.3f), Pt(0.9f, 0.3f))))       // su espejo
        val r = EyeComposite.Layout(300, 400, mirrored = true)
        assertTrue(r.isReal(listOf(Pt(0.6f, 0.3f), Pt(0.9f, 0.3f))))
    }

    @Test fun eyeZonesUseTheFaceAnalysis() {
        assertTrue(Zone.EYE_R.usesFaceAnalysis()); assertTrue(Zone.EYE_L.usesFaceAnalysis())
        assertTrue(Zone.FACE_FRONTAL.usesFaceAnalysis()); assertFalse(Zone.HANDS_DORSAL.usesFaceAnalysis())
    }
}
