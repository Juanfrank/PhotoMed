package com.photomed.app

import com.photomed.app.align.HeadPose
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Pose de la cabeza (cabeceo, guiñada, alabeo) por ajuste al modelo 3D canónico. */
class HeadPoseTest {
    private val data = JSONObject(javaClass.getResourceAsStream("/eval/face_matching.json")!!.bufferedReader().readText())
    private val faces = data.getJSONObject("faces")

    private fun pts(o: JSONObject): Map<String, Pt> {
        val p = o.getJSONObject("pts")
        return p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    /** Proyección sintética del modelo con una pose dada (convención del modelo: cabeceo + = mira abajo). */
    private fun synth(yaw: Double, pitch: Double, roll: Double, s: Float = 0.025f): Map<String, Pt> {
        val y = Math.toRadians(yaw); val p = Math.toRadians(pitch); val r = Math.toRadians(roll)
        val cy = cos(y); val sy = sin(y); val cp = cos(p); val sp = sin(p); val cr = cos(r); val sr = sin(r)
        val m = doubleArrayOf(cr * cy, cr * sy * sp - sr * cp, cr * sy * cp + sr * sp, sr * cy, sr * sy * sp + cr * cp, sr * sy * cp - cr * sp)
        val model = Class.forName("com.photomed.app.align.HeadModel").getDeclaredField("INSTANCE").get(null).let { inst ->
            @Suppress("UNCHECKED_CAST")
            inst.javaClass.getDeclaredMethod("getPOINTS").invoke(inst) as Map<String, FloatArray>
        }
        return model.mapValues { (_, v) ->
            val x = m[0] * v[0] + m[1] * v[1] + m[2] * v[2]; val yy = m[3] * v[0] + m[4] * v[1] + m[5] * v[2]
            Pt((0.5 + s * x).toFloat(), (0.66 - s * yy).toFloat())
        }
    }

    @Test
    fun recoversSyntheticPoses() {
        for ((yaw, pitch, roll) in listOf(Triple(0.0, 0.0, 0.0), Triple(0.0, 15.0, 0.0), Triple(0.0, -20.0, 5.0), Triple(25.0, 10.0, -4.0), Triple(-30.0, -12.0, 3.0))) {
            val f = HeadPose.fit(synth(yaw, pitch, roll))!!
            // salida calibrada y en convención ML Kit (+ = barbilla arriba)
            assertEquals("cabeceo ($yaw,$pitch,$roll)", -((pitch - 2.44) / 0.85).toFloat(), f.pitch, 0.4f)
            assertEquals("guiñada", (yaw / 1.16).toFloat(), f.yaw, 0.6f)
            assertEquals("alabeo", roll.toFloat(), f.roll, 0.3f)
            assertTrue(f.rms < 0.05f)
        }
        // cambio relativo entre dos tomas de la misma cabeza
        val up = HeadPose.fit(synth(0.0, -10.0, 0.0))!!.pitch; val down = HeadPose.fit(synth(0.0, 10.0, 0.0))!!.pitch
        assertEquals(20f / 0.85f, up - down, 0.5f)
    }

    @Test
    fun agreesWithMediaPipePoseOnRealFaces() {
        val g = mutableListOf<Float>(); val m = mutableListOf<Float>()
        for (k in faces.keys()) {
            val o = faces.getJSONObject(k); val e = o.optJSONArray("euler") ?: continue
            val f = HeadPose.fit(pts(o)) ?: continue
            g += f.pitch; m += -e.getDouble(1).toFloat()   // MediaPipe → convención ML Kit
        }
        val n = g.size; val mg = g.average(); val mm = m.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0; var mae = 0.0
        for (i in 0 until n) { sxy += (g[i] - mg) * (m[i] - mm); sxx += (g[i] - mg) * (g[i] - mg); syy += (m[i] - mm) * (m[i] - mm); mae += kotlin.math.abs(g[i] - m[i]) }
        val r = sxy / kotlin.math.sqrt(sxx * syy)
        println("Cabeceo geométrico vs MediaPipe: $n rostros, r = %.3f, error medio %.2f°".format(r, mae / n))
        assertTrue("r=$r", n > 100 && r > 0.93)
        assertTrue("mae", mae / n < 3.5)
    }
}
