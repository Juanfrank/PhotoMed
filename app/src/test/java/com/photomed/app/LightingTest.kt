package com.photomed.app

import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import com.photomed.app.align.Standardization
import com.photomed.app.data.ProtocolDefaults
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Medición de la luz del rostro: siempre hay medida (también en primeros planos). */
class LightingTest {
    private val w = 200; private val h = 266
    /** Imagen: mitad izquierda (de la imagen) a [left], derecha a [right] (luminancia 0..255). */
    private fun img(left: Int, right: Int) = IntArray(w * h) { i -> val v = if (i % w < w / 2) left else right; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }

    private val face = mapOf(Landmarks.EYE_L to Pt(0.65f, 0.5f), Landmarks.EYE_R to Pt(0.35f, 0.5f), "nose_bottom_1" to Pt(0.5f, 0.75f),
        "oval_8" to Pt(0.9f, 0.7f), "oval_28" to Pt(0.1f, 0.7f))

    @Test fun fullFaceUsesCheeks() {
        val l = Standardization.lighting(img(60, 200), w, h, face)!!
        assertFalse(l.regional)
        assertEquals(60 / 255f, l.leftCheek, 0.02f); assertEquals(200 / 255f, l.rightCheek, 0.02f)
    }

    @Test fun eyeContoursReplaceMissingPupils() {
        val noPupils = face - Landmarks.EYE_L - Landmarks.EYE_R +
            (0 until 4).associate { "eye_l_$it" to Pt(0.6f + it * 0.03f, 0.5f) } + (0 until 4).associate { "eye_r_$it" to Pt(0.3f + it * 0.03f, 0.5f) }
        val l = Standardization.lighting(img(100, 100), w, h, noPupils)!!
        assertFalse(l.regional)
    }

    @Test fun closeUpWithoutFaceStillMeasures() {
        // primer plano (labios): sin pupilas ni nariz, o sin ningún punto → medida por regiones, nunca null
        val lips = (0 until 10).associate { "lip_upper_top_$it" to Pt(0.2f + it * 0.06f, 0.6f) } + (0 until 10).associate { "lip_lower_bottom_$it" to Pt(0.2f + it * 0.06f, 0.8f) }
        val a = Standardization.lighting(img(120, 120), w, h, lips)
        assertNotNull(a); assertTrue(a!!.regional); assertEquals(120 / 255f, a.face, 0.02f)
        val b = Standardization.lighting(img(90, 90), w, h, emptyMap())!!
        assertTrue(b.regional); assertEquals(90 / 255f, b.face, 0.02f)
    }

    @Test fun evenCheekLightOnlyByDefaultInFrontalView() {
        assertNotNull(ProtocolDefaults.step(Zone.FACE_FRONTAL).maxLightAsym)
        assertNull(ProtocolDefaults.step(Zone.FACE_OBLIQUE_R).maxLightAsym)
        assertNull(ProtocolDefaults.step(Zone.LIPS).maxLightAsym)
        assertNotNull(ProtocolDefaults.step(Zone.LIPS).minLight)
    }
}
