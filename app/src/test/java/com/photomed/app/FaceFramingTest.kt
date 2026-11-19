package com.photomed.app

import com.photomed.app.align.FaceFraming
import com.photomed.app.align.Pt
import com.photomed.app.ui.camera.FaceControlKeys
import com.photomed.app.ui.camera.GuideArtByZone
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Primera toma: la guía se ajusta a la cara cuando esta coincide con el marco del dibujo. */
class FaceFramingTest {
    private val art = GuideArtByZone.getValue("rostro_frontal")
    private val face: Map<String, Pt> = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText()).let { o ->
        val p = o.getJSONObject(o.keys().next()).getJSONObject("pts")
        p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    /** La cara llevada al marco del dibujo (mismo centro y ancho del óvalo), más un desplazamiento. */
    private fun framed(dx: Float, dy: Float, s: Float = 1f): Map<String, Pt> {
        val f0 = FaceFraming.of(art.controls, FaceControlKeys, face)!!
        val oval = (0 until 36).map { face["oval_$it"]!! }
        val cx = (oval.minOf { it.x } + oval.maxOf { it.x }) / 2; val cy = (oval.minOf { it.y } + oval.maxOf { it.y }) / 2
        val k = s / f0.scale
        // centro del óvalo del dibujo en unidades del ancho
        val tx = cx - f0.dx; val ty = cy - f0.dy / 0.75f
        return face.mapValues { (_, p) -> Pt(tx + (p.x - cx) * k + dx, ty + (p.y - cy) * k + dy / 0.75f) }
    }

    @Test
    fun matchesOnlyInsideTheFrameWithHysteresis() {
        val ok = FaceFraming.of(art.controls, FaceControlKeys, framed(0f, 0f))!!
        assertTrue("centrada: $ok", FaceFraming.matches(ok, false))
        assertFalse("desplazada 10 %", FaceFraming.matches(FaceFraming.of(art.controls, FaceControlKeys, framed(0.10f, 0f)), false))
        assertFalse("20 % más pequeña", FaceFraming.matches(FaceFraming.of(art.controls, FaceControlKeys, framed(0f, 0f, 0.8f)), false))
        // histéresis: a 6 % no entra, pero si ya coincidía, se mantiene
        val edge = FaceFraming.of(art.controls, FaceControlKeys, framed(0.06f, 0f))
        assertFalse(FaceFraming.matches(edge, false))
        assertTrue(FaceFraming.matches(edge, true))
    }
}
