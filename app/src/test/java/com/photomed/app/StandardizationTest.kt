package com.photomed.app

import com.photomed.app.align.Detection
import com.photomed.app.align.FaceMeshMap
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.Standardization
import com.photomed.app.data.Zone
import com.photomed.app.ui.compare.captureConditionsWarning
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class StandardizationTest {
    private val data = JSONObject(javaClass.getResourceAsStream("/eval/face_matching.json")!!.bufferedReader().readText())
    private val faces = data.getJSONObject("faces")

    private fun det(o: JSONObject, yaw: Float? = null, pitch: Float? = null, roll: Float? = null): Detection {
        val p = o.getJSONObject("pts")
        val pts = p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val e = o.optJSONArray("euler")
        return Detection(pts, yaw ?: e?.getDouble(0)?.toFloat(), pitch ?: e?.getDouble(1)?.toFloat(), roll ?: e?.getDouble(2)?.toFloat())
    }

    @Test
    fun meshMappingAndPoseFromMatrix() {
        val pts = FaceMeshMap.toKeys({ i -> i / 1000f to i / 2000f }, 478, 360, 480)
        assertEquals(36 + 20 + 32 + 38 + 5 + 5, pts.size)
        assertEquals(0.152f, pts["oval_18"]!!.x, 1e-6f)            // índice 152 = mentón
        assertEquals(0.076f * 480 / 360, pts["oval_18"]!!.y, 1e-6f) // normalizado por el ancho
        assertEquals(0.473f, pts["eye_l"]!!.x, 1e-6f)              // iris izquierdo
        // matriz de rotación en Y de 30° (column-major) → guiñada 30°
        val t = Math.toRadians(30.0); val c = cos(t).toFloat(); val s = sin(t).toFloat()
        val r = arrayOf(floatArrayOf(c, 0f, s), floatArrayOf(0f, 1f, 0f), floatArrayOf(-s, 0f, c))
        val m = FloatArray(16).also { for (i in 0..2) for (j in 0..2) it[j * 4 + i] = r[i][j]; it[15] = 1f }
        val (yaw, pitch, roll) = FaceMeshMap.euler(m)
        assertEquals(30f, yaw, 0.01f); assertEquals(0f, pitch, 0.01f); assertEquals(0f, roll, 0.01f)
    }

    @Test
    fun facingSideAgreesWithHeadPoseOnRealFaces() {
        // En LFW: el sentido geométrico (nariz vs óvalo) debe concordar con el signo de la guiñada 3D.
        var agree = 0; var n = 0
        for (k in faces.keys()) {
            val o = faces.getJSONObject(k); val e = o.optJSONArray("euler") ?: continue
            val yaw = e.getDouble(0)
            if (kotlin.math.abs(yaw) < 10) continue
            val side = Standardization.facingSide(det(o).points) ?: continue
            n++; if ((side > 0) == (yaw > 0)) agree++
        }
        val rate = maxOf(agree, n - agree) / n.toFloat()
        println("Sentido del giro: $n rostros girados ≥ 10°, concordancia %.3f".format(rate))
        assertTrue("concordancia $rate", n > 100 && rate >= 0.95f)
    }

    @Test
    fun standardViewsFrankfortAndLighting() {
        val frontal = faces.keys().asSequence().map { faces.getJSONObject(it) }.first {
            val e = it.getJSONArray("euler"); kotlin.math.abs(e.getDouble(0)) < 3 && kotlin.math.abs(e.getDouble(1)) < 4 && kotlin.math.abs(e.getDouble(2)) < 3
        }
        val ok = Standardization.evaluate(Zone.FACE_FRONTAL, det(frontal), Standardization.Lighting(0.6f, 0.62f, 0.6f))
        assertEquals(MatchStatus.MATCH, ok.status)
        val turned = Standardization.evaluate(Zone.FACE_FRONTAL, det(frontal, yaw = 14f), null)
        assertTrue(turned.hints.any { it.startsWith("Cabeza: girar") })
        val chin = Standardization.evaluate(Zone.FACE_FRONTAL, det(frontal, pitch = 12f), null)
        assertTrue(chin.hints.any { it.startsWith("Barbilla:") })
        val light = Standardization.evaluate(Zone.FACE_FRONTAL, det(frontal), Standardization.Lighting(0.4f, 0.7f, 0.55f))
        assertTrue(light.hints.any { "izquierdo" in it })
        // oblicuo: frontal a 0° → hay que girar
        val obl = Standardization.evaluate(Zone.FACE_OBLIQUE_R, det(frontal), null)
        assertTrue(obl.status != MatchStatus.MATCH)
        // sin rostro
        assertEquals(MatchStatus.SEARCHING, Standardization.evaluate(Zone.FACE_FRONTAL, Detection.EMPTY, null).status)
    }

    @Test
    fun captureConditionsComparability() {
        val a = mapOf("HeadYaw" to "2", "DistanceCm" to "150", "Engine" to "MediaPipe Face Landmarker", "Status" to "MATCH")
        assertNull(captureConditionsWarning(a, a + ("HeadYaw" to "5")))
        val w = captureConditionsWarning(a, mapOf("HeadYaw" to "15", "DistanceCm" to "60", "Engine" to "Google ML Kit", "Status" to "CLOSE"))!!
        assertTrue("giro" in w && "distancia" in w && "motor" in w && "sin encuadre" in w)
    }
}
