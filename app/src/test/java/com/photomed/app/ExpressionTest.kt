package com.photomed.app

import com.photomed.app.align.Expression
import com.photomed.app.align.Pt
import com.photomed.app.data.Zone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Control de expresión: misma sonrisa, labios, ojos y cejas que la referencia. */
class ExpressionTest {
    private fun faces(res: String): Map<String, Map<String, Pt>> {
        val json = javaClass.getResourceAsStream(res)?.bufferedReader()?.readText() ?: return emptyMap()
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { k ->
            val p = o.getJSONObject(k).getJSONObject("pts")
            p.keys().asSequence().associateWith { kk -> p.getJSONArray(kk).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        }
    }

    @Test
    fun sameExpressionRotatedGivesNoHints() {
        // retomas reales giradas ±10° (mismo gesto): ninguna indicación de expresión
        val stock = faces("/stock/stock_faces.json")
        assumeTrue("sin retratos de stock", stock.isNotEmpty())
        var pairs = 0; val wrong = ArrayList<String>()
        for (k in stock.keys.filter { it.endsWith("_r0") }) for (r in listOf("_r10", "_r-10")) {
            val a = stock[k.removeSuffix("_r0") + r] ?: continue
            val h = Expression.compare(Expression.measure(stock[k]!!)!!, Expression.measure(a)!!)
            pairs++; if (h.isNotEmpty()) wrong += "$k$r: $h"
        }
        println("EXPRESIÓN: $pairs retomas, falsos avisos ${wrong.size} ${wrong.take(3)}")
        assertTrue(wrong.size <= pairs / 20)
    }

    @Test
    fun neutralRuleSeparatesSmilesFromNeutralFaces() {
        val stock = faces("/stock/stock_faces.json")
        assumeTrue("sin retratos de stock", stock.isNotEmpty())
        // sonrisas amplias (blendshape de MediaPipe ≥ 0,9) y rostros neutros (≤ 0,05)
        val smiles = listOf("p_1222271", "p_2379004", "p_774909", "u_photo-1580489944761-")
        val neutral = listOf("u_photo-1438761681033-", "u_photo-1506794778202-", "u_photo-1534528741775-", "p_733872")
        fun hints(prefix: String) = stock.entries.first { it.key.startsWith(prefix) && it.key.endsWith("_r0") }.value
            .let { Expression.neutral(Zone.FACE_FRONTAL, Expression.measure(it)!!) }
        smiles.forEach { assertTrue(it, "Expresión: relajar la sonrisa" in hints(it)) }
        neutral.forEach { assertEquals(it, emptyList<String>(), hints(it)) }
    }

    @Test
    fun widerMouthAgainstNeutralReference() {
        // sintético con la cara de demostración: comisuras 6 % más separadas y elevadas = sonrisa
        val ref = faces("/demo/faces.json").values.first()
        val cx = (ref["lip_upper_top_0"]!!.x + ref["lip_upper_top_10"]!!.x) / 2
        val cur = ref.mapValues { (k, p) ->
            if (k == "lip_upper_top_0" || k == "lip_upper_top_10" || k == "mouth_l" || k == "mouth_r")
                Pt(cx + (p.x - cx) * 1.25f, p.y - 0.01f) else p
        }
        val h = Expression.compare(Expression.measure(ref)!!, Expression.measure(cur)!!)
        assertTrue(h.toString(), "Expresión: relajar la sonrisa" in h)
        assertEquals(listOf("Expresión: sonreír como en la referencia"),
            Expression.compare(Expression.measure(cur)!!, Expression.measure(ref)!!))
    }
}
