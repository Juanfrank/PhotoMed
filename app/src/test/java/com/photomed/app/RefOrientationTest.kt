package com.photomed.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.ImageOps
import com.photomed.app.align.Pt
import com.photomed.app.align.RefOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/** Giro de la referencia guardado en la toma: orientación de cada foto y semejanza previa para alinear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefOrientationTest {
    /** La semejanza previa coincide con girar la foto con [ImageEdit] y recortarla al centro en 3:4. */
    @Test
    fun priorMatchesRotateAndCenterCrop() {
        val w = 300; val h = 400
        for (k in 1..3) for (p in listOf(Pt(0.5f, 0.6f), Pt(0.42f, 0.7f), Pt(0.58f, 0.62f))) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(b).apply { drawColor(Color.BLACK); drawCircle(p.x * w, p.y * w, 4f, Paint().apply { color = Color.WHITE }) }
            val r = ImageOps.centerCrop34(ImageEdit(rot = k).apply(b), w)
            val px = IntArray(r.width * r.height).also { r.getPixels(it, 0, r.width, 0, 0, r.width, r.height) }
            var sx = 0.0; var sy = 0.0; var n = 0
            for (i in px.indices) if (Color.red(px[i]) > 128) { sx += i % r.width; sy += i / r.width; n++ }
            assertTrue("punto perdido al girar $k", n > 0)
            val got = Pt((sx / n / r.width).toFloat(), (sy / n / r.width).toFloat())
            val want = RefOrientation.prior(k).apply(p)
            assertTrue("giro $k: $p → esperado $want, obtenido $got", hypot(got.x - want.x, got.y - want.y) < 0.02f)
        }
    }

    /** La semejanza de encajado coincide con girar la foto y encajarla entera en 3:4. */
    @Test
    fun fitPriorMatchesRotateAndFit() {
        val w = 300; val h = 400
        for (k in 1..3) for (p in listOf(Pt(0.5f, 0.6f), Pt(0.3f, 0.4f), Pt(0.7f, 1.0f))) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(b).apply { drawColor(Color.BLACK); drawCircle(p.x * w, p.y * w, 5f, Paint().apply { color = Color.WHITE }) }
            val r = ImageOps.fit34(ImageEdit(rot = k).apply(b), w)
            val px = IntArray(r.width * r.height).also { r.getPixels(it, 0, r.width, 0, 0, r.width, r.height) }
            var sx = 0.0; var sy = 0.0; var n = 0
            for (i in px.indices) if (Color.red(px[i]) > 100) { sx += i % r.width; sy += i / r.width; n++ }
            assertTrue("punto perdido al girar $k", n > 0)
            val got = Pt((sx / n / r.width).toFloat(), (sy / n / r.width).toFloat())
            val want = RefOrientation.fitPrior(k).apply(p)
            assertTrue("giro $k: $p → esperado $want, obtenido $got", hypot(got.x - want.x, got.y - want.y) < 0.02f)
        }
    }

    /** Orientación por la cadena de referencias: cada toma suma su giro al de su referencia. */
    @Test
    fun orientationFollowsTheReferenceChain() {
        val infos = mapOf(
            "a" to emptyMap(),
            "b" to mapOf(RefOrientation.KEY_ROT to "90", RefOrientation.KEY_REF to "a"),
            "c" to mapOf(RefOrientation.KEY_ROT to "90", RefOrientation.KEY_REF to "b"),
            "d" to mapOf(RefOrientation.KEY_REF to "c"))
        assertEquals(1, RefOrientation.relative("a", "b", infos))
        assertEquals(2, RefOrientation.relative("a", "c", infos))
        assertEquals(2, RefOrientation.relative("a", "d", infos))
        assertEquals(0, RefOrientation.relative("c", "d", infos))
        assertEquals(3, RefOrientation.relative("b", "a", infos))
    }
}
