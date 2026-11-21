package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.MeasureGuide
import com.photomed.app.align.Pt
import com.photomed.app.ui.compare.hitPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tocar la foto: la línea, y con la línea ya elegida, el tramo (guía punteada) tocado. */
class HitPickTest {
    private val nose = AnnotLine(AnnotLayer.NOSE, listOf(Pt(0.4f, 0.5f), Pt(0.6f, 0.5f)), false,
        metrics = LineMetrics("Nariz"),
        guides = listOf(MeasureGuide("Ancho alar", Pt(0.4f, 0.55f), Pt(0.6f, 0.55f), key = "Ancho alar"),
            MeasureGuide("Largo", Pt(0.5f, 0.3f), Pt(0.5f, 0.5f), key = "Largo")))

    @Test
    fun picksLineThenSegment() {
        val layers = setOf(AnnotLayer.NOSE)
        // sin selección: la línea entera
        assertEquals("Nariz" to null, hitPick(listOf(nose), layers, Pt(0.45f, 0.51f), 0.03f, null)?.let { it.first.metrics?.name to it.second })
        // con la línea elegida: el tramo más cercano
        assertEquals("Largo", hitPick(listOf(nose), layers, Pt(0.505f, 0.4f), 0.03f, "Nariz")?.second)
        assertEquals("Ancho alar", hitPick(listOf(nose), layers, Pt(0.55f, 0.552f), 0.03f, "Nariz")?.second)
        // lejos de todo: nada
        assertNull(hitPick(listOf(nose), layers, Pt(0.9f, 0.9f), 0.03f, "Nariz"))
    }
}
