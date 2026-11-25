package com.photomed.app

import com.photomed.app.align.ColorMatch
import com.photomed.app.align.Lab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alineador de color: la referencia de piel solo con píxeles de piel natural (sin ropa, brillos, sombras ni rojeces). */
class NaturalSkinTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun referenceIgnoresClothesHighlightsShadowsAndRedness() {
        val skin = rgb(205, 160, 135)
        val px = IntArray(1000) { i -> when {
            i < 500 -> skin
            i < 700 -> rgb(40, 70, 160)       // ropa azul dentro de la silueta
            i < 780 -> rgb(255, 255, 255)     // brillo especular
            i < 860 -> rgb(25, 18, 15)        // sombra profunda
            else -> rgb(200, 90, 95)          // rojez / lesión
        } }
        val s = ColorMatch.stats(px, BooleanArray(px.size) { true })!!
        val lab = DoubleArray(3); Lab.fromRgb(skin, lab)
        assertEquals(lab[0], s.meanL, 1.0); assertEquals(lab[1], s.meanA, 1.0); assertEquals(lab[2], s.meanB, 1.0)
    }

    @Test fun naturalSkinRangeAcrossPhototypes() {
        val lab = DoubleArray(3)
        for (c in listOf(rgb(240, 205, 185), rgb(205, 160, 135), rgb(150, 100, 75), rgb(95, 60, 45))) {
            Lab.fromRgb(c, lab); assertTrue("piel $c", ColorMatch.isNaturalSkin(lab[0], lab[1], lab[2]))
        }
        for (c in listOf(rgb(40, 70, 160), rgb(128, 128, 128), rgb(60, 140, 60), rgb(255, 255, 255))) {
            Lab.fromRgb(c, lab); assertFalse("no piel $c", ColorMatch.isNaturalSkin(lab[0], lab[1], lab[2]))
        }
    }
}
