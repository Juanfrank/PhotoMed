package com.photomed.app

import com.photomed.app.align.BodySchema
import com.photomed.app.align.ProtocolReference
import com.photomed.app.align.Pt
import com.photomed.app.data.Protocol
import com.photomed.app.data.ProtocolJson
import com.photomed.app.data.ProtocolRef
import com.photomed.app.data.ProtocolStep
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El contorno del cuerpo (pie, mano, miembros) se guarda en la referencia del protocolo y vuelve al leerla. */
class ProtocolBodyRefTest {
    @Test
    fun bodyContourIsStoredAndRead() {
        val foot = (0 until 120).map { i -> val a = i / 120.0 * 2 * Math.PI; Pt(0.5f + 0.15f * kotlin.math.cos(a).toFloat(), 0.7f + 0.4f * kotlin.math.sin(a).toFloat()) }
        val schema = BodySchema.Schema(polygons = listOf(foot), lines = listOf(listOf(Pt(0.45f, 1.0f), Pt(0.44f, 0.9f))),
            capsules = listOf(BodySchema.Capsule(Pt(0.3f, 0.2f), Pt(0.3f, 0.6f), 0.04f)))
        val lines = ProtocolReference.bodyLines(schema)
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.layer == ProtocolReference.BODY_LAYER })
        assertTrue("silueta simplificada pero completa", lines[0].closed && lines[0].points.size in 40..80)
        assertTrue("separación entre dedos abierta", !lines[1].closed)
        assertTrue("miembro como contorno cerrado", lines[2].closed && lines[2].points.size == 4)
        val p = Protocol(name = "Pies", steps = listOf(ProtocolStep(zone = Zone.FEET_DORSAL, ref = ProtocolRef(lines = lines))))
        val back = ProtocolJson.read(ProtocolJson.write(p)).steps[0].ref!!
        assertEquals(lines.size, back.lines.size)
        assertEquals(ProtocolReference.BODY_LAYER, back.lines[0].layer)
        assertEquals(lines[0].points.size, back.lines[0].points.size)
    }
}
