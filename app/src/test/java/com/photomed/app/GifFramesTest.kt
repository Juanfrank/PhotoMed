package com.photomed.app

import com.photomed.app.ui.compare.CompareExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GifFramesTest {
    @Test
    fun gifHasFullBeforeAndAfterFrames() {
        // dos fotos lisas: debe haber cuadros 100 % «antes» y 100 % «después» (sin franja ni línea)
        val red = android.graphics.Bitmap.createBitmap(60, 80, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF0000.toInt()) }
        val blue = android.graphics.Bitmap.createBitmap(60, 80, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF0000FF.toInt()) }
        for (mode in CompareExport.GifMode.entries) {
            val frames = CompareExport.gifFrames(listOf(red to "", blue to ""), mode, CompareExport.GifTiming(1000, 700))
            fun allOf(px: IntArray, c: Int) = px.all { (it and 0xFFFFFF) == (c and 0xFFFFFF) }
            assertTrue("$mode: cuadro todo antes", frames.any { allOf(it.first, 0xFF0000) })
            assertTrue("$mode: cuadro todo después", frames.any { allOf(it.first, 0x0000FF) })
            // el resto son transiciones: tienen de las dos
            assertEquals(2, frames.count { allOf(it.first, 0xFF0000) || allOf(it.first, 0x0000FF) })
        }
    }

}
