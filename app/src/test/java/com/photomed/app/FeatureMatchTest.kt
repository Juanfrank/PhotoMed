package com.photomed.app

import com.photomed.app.align.FeatureMatch
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validación de la coincidencia por rasgo (ojos, cejas, nariz, labios, contorno) tras alinear. */
class FeatureMatchTest {
    private val face: Map<String, Pt> = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText()).let { o ->
        val p = o.getJSONObject(o.keys().next()).getJSONObject("pts")
        p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    @Test
    fun localChangeShowsOnlyInThatFeature() {
        // la misma cara desplazada y escalada: tras deshacer la semejanza, todo coincide al 100 %
        val s = Similarity(1.1f, 0.05f, 0.02f, -0.01f)
        val moved = face.mapValues { s.apply(it.value) }
        val all = FeatureMatch.scores(face, moved, s.inverse())
        assertEquals(setOf("ojos", "cejas", "nariz", "labios", "contorno"), all.map { it.first }.toSet())
        assertTrue(all.toString(), all.all { it.second > 0.99f })
        // labios cambiados (p. ej. sonrisa o relleno): solo baja "labios"
        val lips = face.mapValues { (k, p) -> if (k.startsWith("lip_")) Pt(p.x * 1.08f - 0.04f, p.y + 0.012f) else p }
        val sc = FeatureMatch.scores(face, lips, Similarity.IDENTITY).toMap()
        assertTrue(sc.toString(), sc.getValue("labios") < FeatureMatch.WARN)
        assertTrue(sc.toString(), listOf("ojos", "cejas", "nariz").all { sc.getValue(it) > 0.99f })
        // con la malla completa (claves m<i>) se usan sus rasgos
        assertTrue(Landmarks.meshRegion("m1") == "nariz" && Landmarks.meshRegion("m33") == "ojos" && Landmarks.meshRegion("m61") == "labios")
    }
}
