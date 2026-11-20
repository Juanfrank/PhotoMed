package com.photomed.app

import com.photomed.app.align.BrowRefine
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.GrayImage
import com.photomed.app.align.Pt
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Ajuste de las cejas a la imagen: se mueven a la franja de pelo solo con evidencia clara. */
class BrowRefineTest {
    private val w = 400
    /** Malla mínima: pupilas a 120 px (interpupilar) y las dos cejas a y = 0,38 (grosor 8 px). */
    private fun mesh(): List<Pt> {
        val m = MutableList(478) { Pt(0.5f, 0.6f) }
        m[468] = Pt(0.35f, 0.5f); m[473] = Pt(0.65f, 0.5f)
        for ((top, bot, x0) in listOf(Triple(BrowRefine.TOP_R, BrowRefine.BOT_R, 0.22f), Triple(BrowRefine.TOP_L, BrowRefine.BOT_L, 0.58f)))
            for (k in 0 until 5) { val x = x0 + k * 0.05f; m[top[k]] = Pt(x, 0.38f); m[bot[k]] = Pt(x, 0.40f) }
        return m
    }

    /** Piel lisa (con algo de ruido) y, si [browAt] no es null, una franja oscura con pelo desde esa fila. */
    private fun image(browAt: Int?): GrayImage {
        val r = Random(7)
        return GrayImage(w, w, FloatArray(w * w) { i ->
            val y = i / w; val x = i % w
            val inBrow = browAt != null && y in browAt until browAt + 8 && (x in 80..190 || x in 225..335)
            if (inBrow) 0.32f + (r.nextFloat() - 0.5f) * 0.2f else 0.62f + (r.nextFloat() - 0.5f) * 0.012f
        })
    }

    @Test fun movesTheBrowOntoTheHairBand() {
        val m = mesh()
        val r = BrowRefine.refineMesh(m, image(152 + 5))
        for (i in BrowRefine.TOP_R + BrowRefine.TOP_L) assertEquals("punto $i", 0.38f + 5f / w, r[i].y, 1.5f / w)
        for (i in BrowRefine.BOT_R + BrowRefine.BOT_L) assertEquals(0.40f + 5f / w, r[i].y, 1.5f / w)
    }

    @Test fun leavesTheMeshWithoutEvidence() {
        val m = mesh()
        assertEquals(m, BrowRefine.refineMesh(m, image(null)))
        // la franja oscura es una montura de gafas (segmentación: accesorio): no cuenta
        val labels = FaceLabels(w, w, ByteArray(w * w) { i -> if (i / w in 150..170) FaceLabels.OTHER.toByte() else FaceLabels.FACE_SKIN.toByte() })
        assertEquals(m, BrowRefine.refineMesh(m, image(157), labels))
    }
}
