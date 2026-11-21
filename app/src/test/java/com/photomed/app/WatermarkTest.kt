package com.photomed.app

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.export.Watermark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatermarkTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun logo() = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF00FF00.toInt()) }

    @Test
    fun logoGoesInTheChosenCorner() {
        val w = 1000; val h = 1300
        val r = Watermark.rect(w, h, logo(), Watermark.Position.BOTTOM_RIGHT)
        assertEquals(200f, r.width(), 0.5f); assertEquals(100f, r.height(), 0.5f)
        assertEquals(w - 30f, r.right, 0.5f); assertEquals(h - 30f, r.bottom, 0.5f)
        val tl = Watermark.rect(w, h, logo(), Watermark.Position.TOP_LEFT)
        assertEquals(30f, tl.left, 0.5f); assertEquals(30f, tl.top, 0.5f)
        // dibujado: píxel verde en la esquina elegida, la otra intacta
        val img = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF000000.toInt()) }
        val out = Watermark.apply(img, Watermark.Mark(logo(), Watermark.Position.BOTTOM_RIGHT))
        assertEquals(0x00FF00, out.getPixel(w - 130, h - 80) and 0xFFFFFF)
        assertEquals(0x000000, out.getPixel(130, 80) and 0xFFFFFF)
        assertEquals(0x000000, img.getPixel(w - 130, h - 80) and 0xFFFFFF)   // la original no cambia
    }

    @Test
    fun logoIsStoredAndPositionPersists() {
        val f = java.io.File(ctx.cacheDir, "logo_src.png")
        f.outputStream().use { logo().compress(Bitmap.CompressFormat.PNG, 100, it) }
        ctx.getSharedPreferences("photomed", 0).edit().clear().commit()
        Watermark.clear(ctx)
        assertNull(Watermark.mark(ctx))
        Watermark.set(ctx, android.net.Uri.fromFile(f))
        assertTrue(Watermark.has(ctx))
        assertEquals(Watermark.Position.BOTTOM_RIGHT, Watermark.position(ctx))
        Watermark.setPosition(ctx, Watermark.Position.TOP_LEFT)
        assertEquals(Watermark.Position.TOP_LEFT, Watermark.mark(ctx)!!.position)
        Watermark.setPosition(ctx, Watermark.Position.NONE)
        assertNull(Watermark.mark(ctx))
        assertNotNull(Watermark.logo(ctx))
        Watermark.clear(ctx)
    }
}
