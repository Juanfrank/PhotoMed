package com.photomed.app

import android.graphics.BitmapFactory
import android.graphics.Color
import com.photomed.app.align.BodyPoints
import com.photomed.app.align.Detection
import com.photomed.app.align.LiveDecision
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.align.ZoneCriteria
import com.photomed.app.data.Zone
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Estabilidad de la detección del pie entre cuadros casi iguales (paciente quieto: ruido, ±8 % de luz,
 * desplazamientos de medio por ciento, recompresión), con fotos reales de pies segmentadas con el mismo
 * modelo que la app (carpeta local `stock/zones/pies_jitter`, fuera del repositorio). Mide cuánto cambian
 * el veredicto de los criterios, los dedos contados y los puntos de referencia del pie, y si la coincidencia
 * con el primer cuadro como referencia da «coincide».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FootStabilityTest {
    private val stats = HashMap<Boolean, Triple<Float, Float, Float>>()
    private class Frame(val classes: ZoneCriteria.Classes, val dx: Float, val dy: Float, val sc: Float, val ang: Float)

    /** Punto de un cuadro (unidades del ancho) llevado a la imagen base (deshace el desplazamiento aplicado). */
    private fun toBase(p: Pt, f: Frame, w: Int): Pt {
        val x = p.x * w - 180 - f.dx; val y = p.y * w - 240 - f.dy
        val a = Math.toRadians(f.ang.toDouble()); val c = cos(a).toFloat() / f.sc; val s = sin(a).toFloat() / f.sc
        return Pt((c * x + s * y + 180) / w, (-s * x + c * y + 240) / w)
    }

    @Test
    fun footDetectionIsStableBetweenFrames() = run(fused = false)

    /** Como en la cámara: la máscara de cada cuadro es la mayoría de las tres últimas segmentaciones. */
    @Test
    fun footDetectionIsStableWithFusedMasks() = run(fused = true)

    private fun run(fused: Boolean) {
        val dir = File("src/test/resources/stock/zones/pies_jitter")
        val tf = File(dir, "transforms.json")
        assumeTrue("sin cuadros de pies locales", tf.exists())
        val meta = JSONObject(tf.readText())
        val report = StringBuilder("ESTABILIDAD DEL PIE" + (if (fused) " (máscaras fusionadas)" else " (cuadro a cuadro)") + "\n")
        var flips = 0; var verdicts = 0; var matches = 0; var pairs = 0
        val jit = ArrayList<Float>()
        for (name in meta.keys().asSequence().sorted()) {
            val arr = meta.getJSONArray(name)
            val raw = (0 until arr.length()).mapNotNull { k ->
                val img = BitmapFactory.decodeFile(File(dir, "${name}_$k.png").path) ?: return@mapNotNull null
                val mk = BitmapFactory.decodeFile(File(dir, "${name}_${k}_mask.png").path) ?: return@mapNotNull null
                val w = img.width; val h = img.height
                val rgb = IntArray(w * h).also { img.getPixels(it, 0, w, 0, 0, w, h) }
                val m = IntArray(w * h).also { mk.getPixels(it, 0, w, 0, 0, w, h) }
                val o = arr.getJSONObject(k)
                Frame(ZoneCriteria.Classes(w, h, ByteArray(w * h) { Color.red(m[it]).toByte() }, rgb),
                    o.getDouble("dx").toFloat(), o.getDouble("dy").toFloat(), o.getDouble("sc").toFloat(), o.getDouble("ang").toFloat())
            }
            if (raw.size < 3) continue
            val frames = if (!fused) raw else {
                val fu = com.photomed.app.align.ClassesFusion()
                // se arranca con los dos primeros cuadros (la cámara ya lleva un rato analizando)
                fu.add(raw[0].classes); fu.add(raw[1].classes)
                raw.drop(2).map { f -> Frame(fu.add(f.classes), f.dx, f.dy, f.sc, f.ang) }
            }
            val w = frames[0].classes.width; val aspect = frames[0].classes.height / w.toFloat()
            val zone = if ("planta" in name) Zone.FEET_PLANTAR else Zone.FEET_DORSAL
            val two = ZoneCriteria.feetOf(frames[0].classes).size >= 2
            val scenes = frames.map { ZoneCriteria.Scene(emptyMap(), null, it.classes, aspect, single = !two) }
            val hints = scenes.map { ZoneCriteria.hints(zone, it).filter { h -> "Centre" !in h } }
            val toes = frames.map { ZoneCriteria.feetOf(it.classes).sortedBy { f -> f.box.x0 }.map { f -> f.toeCount ?: -1 } }
            val pts = scenes.mapIndexed { i, s -> BodyPoints.of(zone, s).mapValues { (_, p) -> toBase(p, frames[i], w) } }
            // veredicto (cumple / no cumple) de cada cuadro y cambios entre cuadros seguidos
            val ok = hints.map { it.isEmpty() }
            flips += ok.zipWithNext().count { (a, b) -> a != b }; verdicts += ok.size - 1
            // desplazamiento de cada punto respecto al primer cuadro, en anchos de pie
            val fw = ZoneCriteria.feetOf(frames[0].classes).maxOfOrNull { (it.box.x1 - it.box.x0).toFloat() / w } ?: 0.2f
            val keyJit = pts[0].keys.associateWith { k -> pts.drop(1).mapNotNull { p -> p[k]?.let { q -> hypot(q.x - pts[0][k]!!.x, q.y - pts[0][k]!!.y) / fw } } }
            keyJit.values.flatten().let { jit.addAll(it) }
            // coincidencia de cada cuadro con el primero como referencia (puntos, como en la cámara)
            val spec = ZoneAlignment.of(zone)
            val ref = Detection(BodyPoints.of(zone, scenes[0]))
            val st = scenes.drop(1).map { s -> LiveDecision.evaluate(spec, ref, Detection(BodyPoints.of(zone, s)), com.photomed.app.align.LiveMatch.DEFAULT_THRESHOLD, false).status }
            matches += st.count { it == MatchStatus.MATCH }; pairs += st.size
            report.append("$name: veredictos ${ok.joinToString("") { if (it) "✓" else "·" }}  dedos ${toes.joinToString(" ")}  " +
                "coincide ${st.count { it == MatchStatus.MATCH }}/${st.size}  puntos: " +
                keyJit.entries.sortedByDescending { e -> e.value.maxOrNull() ?: 0f }.take(3).joinToString { (k, v) -> "$k %.2f".format(v.maxOrNull() ?: 0f) } +
                "\n   avisos: " + hints.map { it.joinToString(" | ") }.distinct().joinToString(" ‖ ") + "\n")
        }
        jit.sort()
        report.append("TOTAL: cambios de veredicto $flips/$verdicts, coincidencias $matches/$pairs, " +
            "desplazamiento de puntos (anchos de pie) mediana %.3f p90 %.3f máx %.3f\n".format(
                jit.getOrElse(jit.size / 2) { 0f }, jit.getOrElse((jit.size * 0.9).toInt()) { 0f }, jit.lastOrNull() ?: 0f))
        println(report)
        File("build/reports/zone_criteria").apply { mkdirs() }.resolve(if (fused) "estabilidad_pie_fusion.txt" else "estabilidad_pie.txt").writeText(report.toString())
        // la decisión no cambia entre cuadros de la misma escena (con la máscara fusionada) y la gran mayoría
        // de los cuadros coincide con el primero (las fotos con el pie cortado por el borde, diminuto en la
        // arena o con las plantas cruzadas son las que fallan: ver el informe)
        if (fused) {
            // (la fusión se anula cuando la piel cambia mucho, como al mover la cámara: así el contorno no va por
            // detrás; en las fotos con la segmentación caótica, pies diminutos en arena, se nota)
            org.junit.Assert.assertTrue("cambios de veredicto con máscaras fusionadas $flips/$verdicts\n$report", flips <= 0.1f * verdicts)
            org.junit.Assert.assertTrue("coincidencias $matches/$pairs\n$report", matches >= 0.7f * pairs)
        }
        stats[fused] = Triple(flips.toFloat() / verdicts.coerceAtLeast(1), matches.toFloat() / pairs.coerceAtLeast(1), jit.getOrElse((jit.size * 0.9).toInt()) { 0f })
    }
}
