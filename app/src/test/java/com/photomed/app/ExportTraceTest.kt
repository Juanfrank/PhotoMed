package com.photomed.app

import android.graphics.Bitmap
import android.graphics.Color
import com.photomed.app.ui.compare.CompareExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exportaciones con trazos o medidas: franja de trazabilidad debajo de la foto, sin taparla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportTraceTest {
    @Test fun footerIsBelowThePhotoAndSaysOrientativeScaleAndVersion() {
        val text = CompareExport.traceFooter("iris")
        assertTrue(text, "orientativos" in text && "11,7 mm" in text && BuildConfig.VERSION_NAME in text)
        val b = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = CompareExport.withFooter(b, text)
        assertEquals(600, out.width); assertTrue(out.height > 800)
        // la foto queda intacta encima
        assertEquals(Color.WHITE, out.getPixel(300, 400))
        assertTrue(CompareExport.traceFooter(null).let { "escala" !in it })
    }
}
