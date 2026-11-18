package com.photomed.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.Pt
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.GuideArtByZone
import com.photomed.app.ui.camera.GuideOverlay
import com.photomed.app.ui.camera.GuideWarp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GuideAdaptTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = true)

    private val faces = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText())

    private fun pts(name: String): Map<String, Pt> {
        val p = faces.getJSONObject(name).getJSONObject("pts")
        return p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    @Test
    fun warpMovesAnchorsToDetectedFeatures() {
        val art = GuideArtByZone["rostro_frontal"]!!
        val det = GuideWarp.detected(pts("face_1"))
        val w = GuideWarp.fit(art.anchors, det)!!
        for (k in listOf("cejas", "pupilas", "base nasal", "boca", "mentón")) {
            assertEquals(k, det[k]!!, w.y(art.anchors[k]!!), 1e-4f)
        }
        // escala horizontal por la distancia interpupilar
        assertEquals(det["x:pupila_der"]!!, w.x(art.anchors["x:pupila_der"]!!), 0.01f)
        assertEquals(det["x:pupila_izq"]!!, w.x(art.anchors["x:pupila_izq"]!!), 0.01f)
        // la línea pupilar de la guía se coloca a la altura detectada
        val pupil = art.lines.first { kotlin.math.abs(it.first.y - art.anchors["pupilas"]!!) < 0.004f }
        assertEquals(det["pupilas"]!!, w.line(pupil.first, pupil.second, art.anchors, det).first.y, 1e-4f)
    }

    @Test
    fun adaptedGuideOnFaces() {
        paparazzi.snapshot {
            Column(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("face_1", "face_2", "face_3").forEach { f ->
                    val bmp = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/$f.jpg"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false, true).forEach { adapt ->
                            Column(Modifier.width(150.dp)) {
                                Box(Modifier.aspectRatio(3f / 4f)) {
                                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                                    GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(),
                                        adaptTo = if (adapt) pts(f) else emptyMap())
                                }
                                Text(if (adapt) "Adaptada a la referencia" else "Guía estándar", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun rotated(p: Map<String, Pt>, deg: Float, c: Pt = Pt(0.5f, 0.6667f)): Map<String, Pt> {
        val t = Math.toRadians(deg.toDouble()); val cs = kotlin.math.cos(t).toFloat(); val sn = kotlin.math.sin(t).toFloat()
        return p.mapValues { (_, v) -> Pt(c.x + cs * (v.x - c.x) - sn * (v.y - c.y), c.y + sn * (v.x - c.x) + cs * (v.y - c.y)) }
    }

    @Test
    fun rollIsDetectedAndUndone() {
        val base = pts("face_1")
        val r0 = GuideWarp.roll(base)!!.first
        for (deg in listOf(-15f, -6f, 8f, 12f)) {
            val r = GuideWarp.roll(rotated(base, deg))!!.first
            assertEquals("roll $deg", r0 + deg, r, 0.3f)
            // anclas enderezadas ≈ las del rostro original (salvo el giro propio de la foto)
            val a = GuideWarp.analyze(rotated(base, deg)).anchors
            val b = GuideWarp.analyze(base).anchors
            for (k in listOf("pupilas", "boca", "mentón")) assertEquals("$k @ $deg°", b[k]!!, a[k]!!, 0.01f)
        }
    }

    @Test
    fun outlineFollowsDetectedOval() {
        val art = GuideArtByZone["rostro_frontal"]!!
        assertTrue("contorno de la ilustración", art.outline.size >= 30)
        for (f in listOf("face_1", "face_2", "face_3")) {
            val det = GuideWarp.analyze(pts(f))
            val w = GuideWarp.fit(art.anchors, det, art.outline)!!
            assertTrue(w.followsOutline)
            // a la altura de la boca, el borde de la guía cae sobre el óvalo detectado (±1,5 % del ancho)
            val ay = art.anchors["boca"]!!
            val k = (0 until art.outline.size / 3).minBy { kotlin.math.abs(art.outline[it * 3] - ay) }
            val (dl, dr) = GuideWarp.span(det.oval, w.y(art.outline[k * 3]))!!
            assertEquals("$f izq", dl, w.x(art.outline[k * 3 + 1], art.outline[k * 3]), 0.015f)
            assertEquals("$f der", dr, w.x(art.outline[k * 3 + 2], art.outline[k * 3]), 0.015f)
        }
    }

    @Test
    fun adaptedGuideOnTiltedFaces() {
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("face_1" to 10f, "face_2" to -12f).forEach { (f, deg) ->
                    val src = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/$f.jpg"))
                    val m = android.graphics.Matrix().apply { postRotate(deg, src.width / 2f, src.height * 0.5f) }
                    val bmp = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(bmp).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
                    // mismos puntos girados alrededor del centro de la imagen (y en unidades del ancho)
                    val p = rotated(pts(f), deg, Pt(0.5f, 0.5f * src.height / src.width))
                    Box(Modifier.width(190.dp).aspectRatio(3f / 4f)) {
                        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(), adaptTo = p)
                    }
                }
            }
        }
    }

    /** Puntos del modelo 3D canónico proyectados con un cabeceo (convención del modelo: + = mira abajo). */
    private fun synthPitch(pitchModel: Double): Map<String, Pt> {
        val p = Math.toRadians(pitchModel); val cp = kotlin.math.cos(p); val sp = kotlin.math.sin(p)
        val inst = Class.forName("com.photomed.app.align.HeadModel").getDeclaredField("INSTANCE").get(null)
        @Suppress("UNCHECKED_CAST")
        val model = inst.javaClass.getDeclaredMethod("getPOINTS").invoke(inst) as Map<String, FloatArray>
        return model.mapValues { (_, v) ->
            val y = cp * v[1] - sp * v[2]
            Pt((0.5 + 0.026 * v[0]).toFloat(), (0.70 - 0.026 * y).toFloat())
        }
    }

    @Test
    fun guideFollowsHeadPitch() {
        // barbilla arriba → el cráneo se acorta en la imagen (vértice más bajo respecto de los ojos)
        val art = GuideArtByZone["rostro_frontal"]!!
        val anchors = art.anchors + ("vértice" to art.outline[0])
        fun skull(pitchModel: Double): Float {
            val det = GuideWarp.analyze(synthPitch(pitchModel))
            val w = GuideWarp.fit(anchors, det, art.outline)!!
            return w.y(art.anchors["pupilas"]!!) - w.y(art.outline[0])   // alto del cráneo sobre los ojos
        }
        val up = skull(-20.0); val level = skull(0.0); val down = skull(20.0)
        assertTrue("barbilla arriba acorta el cráneo ($up < $level)", up < level)
        assertTrue("barbilla abajo lo alarga ($down > $level)", down > level)
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-20.0 to "Barbilla arriba 20°", 0.0 to "Neutro", 20.0 to "Barbilla abajo 20°").forEach { (pm, label) ->
                    val pts = synthPitch(pm)
                    Column(Modifier.width(118.dp)) {
                        Box(Modifier.aspectRatio(3f / 4f).background(Color(0xFF30303A))) {
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                pts.values.forEach { drawCircle(Color(0xFFFFD54F), 1.6f, androidx.compose.ui.geometry.Offset(it.x * size.width, it.y * size.width)) }
                            }
                            GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(), adaptTo = pts)
                        }
                        Text(label, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    @Test
    fun meshWarpPlacesEachFeature() {
        val art = GuideArtByZone["rostro_frontal"]!!
        val keys = com.photomed.app.ui.camera.FaceControlKeys
        for (f in listOf("face_1", "face_2", "face_3")) {
            val p = pts(f)
            val w = com.photomed.app.ui.camera.FaceMeshWarp.fit(keys, art.controls, p)!!
            assertTrue(w.controlCount > 100)
            fun artPt(k: String) = keys.indexOf(k).let { androidx.compose.ui.geometry.Offset(art.controls[it * 2], art.controls[it * 2 + 1]) }
            // pupilas, comisuras, base nasal, mentón, cejas y mejillas: cada rasgo en su sitio (±1,5 % del ancho)
            val cp = com.photomed.app.align.FaceKeys.canonical(p)
            // comisuras de ojos y boca, base nasal, mentón, mejillas y cejas (orden canónico)
            for (k in listOf("eye_l_0", "eye_l_8", "eye_r_0", "eye_r_8", "lip_upper_top_0", "lip_upper_top_10", "nose_bottom_1", "oval_18", "oval_8", "oval_28", "brow_l_top_2", "brow_r_top_2")) {
                val m = w.map(artPt(k)); val d = cp[k]!!
                assertEquals("$f $k x", d.x, m.x, 0.015f)
                assertEquals("$f $k y", d.y / (4f / 3f), m.y, 0.015f)
            }
            // orejas: entre los bordes del óvalo y por fuera de él, a la altura de los ojos–nariz
            val ear = w.map(artPt("ear_r_out")); val eye = cp["eye_r_0"]!!
            assertTrue("$f oreja por fuera del ojo", ear.x < eye.x)
        }
    }

    /** Silueta sintética a partir del contorno del dibujo, con anchos modificados por [widen](y). */
    private fun silhouetteOf(outline: FloatArray, widen: (Float) -> Float = { 1f }, shiftFront: (Float) -> Float = { 0f }): com.photomed.app.align.Silhouette {
        val n = outline.size / 3
        val rows = com.photomed.app.align.Silhouette.ROWS
        val l = FloatArray(rows) { Float.NaN }; val r = FloatArray(rows) { Float.NaN }
        for (i in 0 until rows) {
            val y = (i + 0.5f) / rows
            val k = (0 until n).minBy { kotlin.math.abs(outline[it * 3] - y) }
            if (kotlin.math.abs(outline[k * 3] - y) > 1.2f / 64) continue
            val c = (outline[k * 3 + 1] + outline[k * 3 + 2]) / 2; val f = widen(y)
            l[i] = c + (outline[k * 3 + 1] - c) * f; r[i] = c + (outline[k * 3 + 2] - c) * f + shiftFront(y)
        }
        return com.photomed.app.align.Silhouette(l, r)
    }

    @Test
    fun profileAnchorsMatchDrawingAlgorithm() {
        for (z in listOf("rostro_perfil_der", "rostro_perfil_izq")) {
            val art = GuideArtByZone[z]!!
            val sil = silhouetteOf(art.outline)
            val a = sil.profileAnchors(art.anchors["x:frente"]!! > 0.5f)
            assertEquals("$z punta nasal", art.anchors["punta nasal"]!!, a["punta nasal"]!!, 0.03f)
            assertEquals("$z pogonion", art.anchors["pogonion"]!!, a["pogonion"]!!, 0.04f)
        }
    }

    @Test
    fun bodyGuideFollowsSilhouette() {
        val art = GuideArtByZone["abdomen_frontal"]!!
        assertTrue(art.outline.size >= 48)
        // cuerpo más ancho de cintura hacia abajo (+25 %)
        val sil = silhouetteOf(art.outline, widen = { y -> if (y > 0.45f) 1.25f else 1f })
        val det = GuideWarp.analyze(emptyMap(), sil, art.anchors)
        // anclas verticales: las del propio dibujo (mismo encuadre), como si la pose las diera
        val anchors = art.anchors.filterKeys { !it.startsWith("x:") }
        val w = GuideWarp.fit(art.anchors, GuideWarp.Detected(anchors + det.anchors, emptyList(), 0f, androidx.compose.ui.geometry.Offset(0.5f, 0.5f), silhouette = sil), art.outline)!!
        assertTrue(w.followsOutline)
        val k = (0 until art.outline.size / 3).first { art.outline[it * 3] > 0.7f }
        val ay = art.outline[k * 3]
        val (dl, dr) = sil.span(w.y(ay))!!
        assertEquals("izq", dl, w.x(art.outline[k * 3 + 1], ay), 0.02f)
        assertEquals("der", dr, w.x(art.outline[k * 3 + 2], ay), 0.02f)
    }

    /** Puntos del modelo canónico con guiñada (rostro oblicuo sintético). */
    private fun synthYaw(yawDeg: Double): Map<String, Pt> {
        val t = Math.toRadians(yawDeg); val c = kotlin.math.cos(t); val sn = kotlin.math.sin(t)
        val inst = Class.forName("com.photomed.app.align.HeadModel").getDeclaredField("INSTANCE").get(null)
        @Suppress("UNCHECKED_CAST")
        val model = inst.javaClass.getDeclaredMethod("getPOINTS").invoke(inst) as Map<String, FloatArray>
        return model.filterKeys { !it.startsWith("ear_") && it != "vertex" }.mapValues { (_, v) ->
            val x = c * v[0] + sn * v[2]
            Pt((0.5 + 0.026 * x).toFloat(), (0.70 - 0.026 * v[1]).toFloat())
        }
    }

    @Test
    fun obliqueProfileAndBodyGuides() {
        val obl = GuideArtByZone["rostro_oblicuo_der"]!!
        assertTrue("controles oblicuos", obl.controls.count { !it.isNaN() } > 200)
        val p = synthYaw(40.0)
        val w = com.photomed.app.ui.camera.FaceMeshWarp.fit(com.photomed.app.ui.camera.FaceControlKeys, obl.controls, p)
        assertTrue("oblicuo: deformación rasgo a rasgo", w != null && w.controlCount > 80)
        val prof = GuideArtByZone["rostro_perfil_der"]!!
        val body = GuideArtByZone["abdomen_frontal"]!!
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                @androidx.compose.runtime.Composable
                fun cell(label: String, sil: com.photomed.app.align.Silhouette?, pts: Map<String, Pt>, zone: Zone) {
                    Column(Modifier.width(88.dp)) {
                        Box(Modifier.aspectRatio(3f / 4f).background(Color(0xFF30303A))) {
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                sil?.let { s -> for (i in 0 until s.rows) if (!s.left[i].isNaN())
                                    drawRect(Color(0xFF5A5470), androidx.compose.ui.geometry.Offset(s.left[i] * size.width, i * size.height / s.rows),
                                        androidx.compose.ui.geometry.Size((s.right[i] - s.left[i]) * size.width, size.height / s.rows + 1)) }
                                pts.values.forEach { drawCircle(Color(0xFFFFD54F), 1.4f, androidx.compose.ui.geometry.Offset(it.x * size.width, it.y * size.width)) }
                            }
                            GuideOverlay(zone, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(), adaptTo = pts, silhouette = sil)
                        }
                        Text(label, color = Color.White, fontSize = 9.sp)
                    }
                }
                cell("Oblicuo 40°", null, p, Zone.FACE_OBLIQUE_R)
                cell("Perfil: nariz +", silhouetteOf(prof.outline, shiftFront = { y -> if (y in 0.48f..0.6f) 0.05f else 0f }), emptyMap(), Zone.FACE_PROFILE_R)
                val ab = body.anchors.filterKeys { !it.startsWith("x:") }
                cell("Abdomen ancho", silhouetteOf(body.outline, widen = { y -> if (y > 0.45f) 1.25f else 1f }),
                    mapOf(com.photomed.app.align.Landmarks.HIP_L to Pt(0.62f, (ab["caderas"] ?: 0.8f) * 4f / 3f), com.photomed.app.align.Landmarks.HIP_R to Pt(0.38f, (ab["caderas"] ?: 0.8f) * 4f / 3f)),
                    Zone.TORSO_FRONT)
                cell("Abdomen estrecho", silhouetteOf(body.outline, widen = { y -> if (y > 0.3f) 0.8f else 1f }),
                    mapOf(com.photomed.app.align.Landmarks.HIP_L to Pt(0.62f, (ab["caderas"] ?: 0.8f) * 4f / 3f), com.photomed.app.align.Landmarks.HIP_R to Pt(0.38f, (ab["caderas"] ?: 0.8f) * 4f / 3f)),
                    Zone.TORSO_FRONT)
            }
        }
    }
}
