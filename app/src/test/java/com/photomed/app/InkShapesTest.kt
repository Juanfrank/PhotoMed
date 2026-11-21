package com.photomed.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import com.photomed.app.align.Annotations
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.InkRenderer
import com.photomed.app.align.InkSet
import com.photomed.app.align.InkStroke
import com.photomed.app.align.Pt
import com.photomed.app.align.TraceFrame
import com.photomed.app.align.rotateSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Anotaciones: símbolos con texto y formas arrastradas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkShapesTest {
    private val sym = InkStroke(Color.RED, 0.01f, listOf(Pt(0.5f, 0.5f)), kind = InkStroke.SYMBOL, shape = "triangle", text = "Bótox 4U", side = "top", size = 0.06f)
    // círculo arrastrado: de una esquina a la otra del recuadro (centro 0,3; radio 0,1)
    private val circle = InkStroke(Color.GREEN, 0.01f, listOf(Pt(0.2f, 0.2f), Pt(0.4f, 0.4f)), kind = InkStroke.SYMBOL, shape = "circle")

    @Test fun savedWithTheirKindTextAndSide() {
        val back = Annotations.read(Annotations.write(InkSet(4f / 3f, listOf(sym, circle))))!!
        assertEquals(sym, back.strokes[0]); assertEquals(circle, back.strokes[1])
        // girada 90° a la derecha, el texto «arriba» queda a la derecha del punto; con espejo, a la izquierda
        assertEquals("right", rotateSide("top", ImageEdit(rot = 1)))
        assertEquals("left", rotateSide("top", ImageEdit(rot = 1, mirror = true)))
        val v = back.inView(ImageEdit(rot = 1), TraceFrame.FULL)[0]
        assertEquals("right", v.side); assertTrue(v.size > 0f)
        // y al revés, el lado visto se guarda en la original (ida y vuelta)
        for (e in listOf(ImageEdit(rot = 1), ImageEdit(rot = 3, mirror = true), ImageEdit(mirror = true)))
            for (s in listOf("top", "right", "bottom", "left"))
                assertEquals(s, rotateSide(com.photomed.app.ui.gallery.sideInOriginal(s, e), e))
    }

    @Test fun strokeEraserTouchesShapeOutlines() {
        val s = InkSet(1f, listOf(sym, circle))
        // tocando el borde del círculo o su interior se quita
        assertEquals(listOf(sym), Annotations.eraseStrokes(s, listOf(Pt(0.4f, 0.3f)), 0.01f).strokes)
        assertEquals(listOf(sym), Annotations.eraseStrokes(s, listOf(Pt(0.3f, 0.3f)), 0.005f).strokes)
        // el borrador rápido (dos puntos lejanos) cruza una línea fina: se mide como segmento, no por sus puntos
        val line = InkStroke(Color.BLUE, 0.004f, listOf(Pt(0.1f, 0.8f), Pt(0.9f, 0.8f)), kind = InkStroke.SYMBOL, shape = "line")
        assertEquals(emptyList<InkStroke>(), Annotations.eraseStrokes(InkSet(1f, listOf(line)), listOf(Pt(0.5f, 0.7f), Pt(0.5f, 0.9f)), 0.002f).strokes)
        // las formas de 2.32 se leen como símbolos arrastrados
        val legacy = """{"v":1,"aspect":1,"strokes":[{"c":-1,"w":0.01,"e":false,"p":[0.1,0.1,0.3,0.2],"k":"shape","s":"rect"}]}"""
        assertEquals(listOf("symbol", "square"), Annotations.read(legacy)!!.strokes.single().let { listOf(it.kind, it.shape) })
        // el símbolo se borra tocándolo
        assertEquals(listOf(circle), Annotations.eraseStrokes(s, listOf(Pt(0.51f, 0.5f)), 0.005f).strokes)
    }

    @Test fun drawsSymbolsShapesAndText() {
        val b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val shapes = listOf("line", "arrow", "circle", "square", "star").mapIndexed { i, sh -> InkStroke(Color.BLUE, 0.01f, listOf(Pt(0.05f, 0.05f + i * 0.08f), Pt(0.3f, 0.1f + i * 0.08f)), kind = InkStroke.SYMBOL, shape = sh) }
        val syms = InkStroke.SYMBOLS.take(7).mapIndexed { i, sh -> sym.copy(points = listOf(Pt(0.6f, 0.1f + i * 0.12f)), shape = sh, side = "right", text = if (i % 2 == 0) "x$i" else null) }
        InkRenderer.draw(Canvas(b), shapes + syms, 400f, { p -> PointF(p.x * 400, p.y * 400) })
        // el texto va en la misma línea que el marcador, a su lado (sin fondo: blanco sobre gris)
        val y = (0.1f * 400).toInt()
        assertTrue((250 until 400).any { x -> b.getPixel(x, y + 2).let { Color.red(it) > 230 && Color.green(it) > 230 && Color.blue(it) > 230 } })
        assertTrue((0 until 400).any { x -> Color.red(b.getPixel(x, 40)) > 200 && Color.green(b.getPixel(x, 40)) < 80 })
    }
}
