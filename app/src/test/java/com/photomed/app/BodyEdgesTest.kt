package com.photomed.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.photomed.app.align.AlignOptions
import com.photomed.app.align.Aligner
import com.photomed.app.align.BodyRegion
import com.photomed.app.align.Detection
import com.photomed.app.align.FreeContour
import com.photomed.app.align.Mask
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Zone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/** La detección de bordes solo usa los bordes del cuerpo, no los del fondo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyEdgesTest {
    private val W = 360; private val H = 480

    /** Fondo con azulejos (bordes fuertes y fijos) y un "cuerpo" (óvalo con rasgos) desplazado [dx],[dy]. */
    private fun scene(dx: Float, dy: Float): Pair<Bitmap, Mask> {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.rgb(200, 200, 205))
        p.color = Color.rgb(60, 60, 70)
        for (x in 0 until W step 40) c.drawRect(x.toFloat(), 0f, x + 4f, H.toFloat(), p)
        for (y in 0 until H step 40) c.drawRect(0f, y.toFloat(), W.toFloat(), y + 4f, p)
        val body = RectF(90f + dx, 80f + dy, 270f + dx, 400f + dy)
        p.color = Color.rgb(205, 160, 135); c.drawOval(body, p)
        p.color = Color.rgb(90, 60, 50)
        c.drawOval(RectF(140f + dx, 190f + dy, 170f + dx, 205f + dy), p)
        c.drawOval(RectF(190f + dx, 190f + dy, 220f + dx, 205f + dy), p)
        c.drawRect(150f + dx, 300f + dy, 210f + dx, 310f + dy, p)
        val m = Mask(W, H)
        val cx = (body.left + body.right) / 2; val cy = (body.top + body.bottom) / 2
        for (y in 0 until H) for (x in 0 until W) {
            val u = (x - cx) / (body.width() / 2); val v = (y - cy) / (body.height() / 2)
            m.data[y * W + x] = u * u + v * v <= 1f
        }
        return bmp to m
    }

    @Test
    fun backgroundEdgesAreDiscarded() {
        val (bmp, m) = scene(0f, 0f)
        val all = FreeContour.edgesOf(bmp)!!
        val body = FreeContour.edgesOf(bmp, m)!!
        val gate = BodyRegion.edgeGate(m, body.edgeW, body.edgeH)
        val outside = body.edges!!.indices.count { body.edges!![it] && !gate[it] }
        val allOutside = all.edges!!.indices.count { all.edges!![it] && !gate[it] }
        val inside = body.edges!!.count { it }
        println("BORDES fuera del cuerpo: sin máscara $allOutside, con máscara $outside; dentro $inside")
        assertTrue(allOutside > 50)
        assertTrue(outside == 0)
        assertTrue(inside > 40)
        assertTrue(body.source == "bordes del cuerpo")
    }

    @Test
    fun contourAlignmentFollowsTheBodyNotTheBackground() {
        // el paciente se movió (12 px, 9 px) pero el fondo (pared con azulejos) quedó igual
        val (b, mb) = scene(0f, 0f); val (a, ma) = scene(12f, 9f)
        val spec = ZoneAlignment.of(Zone.FREE)
        val opts = AlignOptions(points = false, contours = true, gaze = false)
        val plain = Aligner.alignCore(spec, b, a, Detection.EMPTY, Detection.EMPTY, 0f, opts)
        val masks = mapOf(b to mb, a to ma)
        val body = Aligner.alignCore(spec, b, a, Detection.EMPTY, Detection.EMPTY, 0f, opts,
            bodyOf = { bmp -> masks.entries.firstOrNull { it.key.sameAs(bmp) }?.value })
        // error en px de un punto del cuerpo: después → antes debe deshacer el desplazamiento
        fun err(t: com.photomed.app.align.Similarity): Float {
            val p = t.apply(Pt((180f + 12f) / W, (240f + 9f) / W))
            return hypot(p.x * W - 180f, p.y * W - 240f)
        }
        println("ALINEACIÓN error (px): todos los bordes %.1f · solo cuerpo %.1f".format(err(plain.transform), err(body.transform)))
        assertTrue(err(body.transform) < 4f)
        assertTrue(err(body.transform) < err(plain.transform))
    }
}
