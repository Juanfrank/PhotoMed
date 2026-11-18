package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.ColorAdjust
import com.photomed.app.align.ColorMatch
import com.photomed.app.align.MaskOps
import com.photomed.app.align.Pt
import com.photomed.app.align.SkinStats
import com.photomed.app.align.rgb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.math.hypot

class ColorMatchTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = true)

    private fun skin(w: Int, h: Int): BooleanArray {
        val p = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText())
            .getJSONObject("face_1").getJSONObject("pts")
        val oval = (0 until 36).map { i -> p.getJSONArray("oval_$i").let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        return MaskOps.erode(MaskOps.polygon(oval, w, h), w / 16).data
    }

    private fun bmp(px: IntArray, w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }

    @Test
    fun skinColorIsRestored() {
        val src = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/face_1.jpg"))
        val w = src.width; val h = src.height
        val orig = src.rgb(); val mask = skin(w, h)
        val ref = ColorMatch.stats(orig, mask)!!
        // otra sesión: más oscura, más contrastada y más cálida (luz incandescente)
        val session = ColorAdjust(1.25, 1.2, 0.0, 9.0, 3.0, 100 * Math.pow(ref.meanL / 100, 1.25), ref.meanL - 6).apply(orig)
        val sess = ColorMatch.stats(session, mask)!!
        val fixed = ColorMatch.fit(sess, ref).apply(session)
        val fix = ColorMatch.stats(fixed, mask)!!
        fun dE(a: SkinStats, b: SkinStats) = hypot(a.meanL - b.meanL, hypot(a.meanA - b.meanA, a.meanB - b.meanB))
        println("ΔE piel: sesión %.1f → corregida %.1f; σL %.1f/%.1f/%.1f".format(dE(sess, ref), dE(fix, ref), ref.stdL, sess.stdL, fix.stdL))
        assertEquals(0.0, dE(fix, ref), 1.5)
        assertEquals(ref.stdL, fix.stdL, 1.0)
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Antes (referencia)" to orig, "Otra sesión (luz cálida)" to session, "Color igualado" to fixed).forEach { (t, px) ->
                    Column(Modifier.width(118.dp)) {
                        Image(bmp(px, w, h).asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.aspectRatio(3f / 4f))
                        Text(t, color = Color.White, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}
