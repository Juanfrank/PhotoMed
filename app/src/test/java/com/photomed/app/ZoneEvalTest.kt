package com.photomed.app

import com.photomed.app.align.AutoCapture
import com.photomed.app.align.Detection
import com.photomed.app.align.Detector
import com.photomed.app.align.LandmarkDetector
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.Standardization
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

/**
 * Evaluación con fotos etiquetadas a mano (de internet, solo para evaluar; carpetas locales
 * `stock/zones/eval_*` con su landmarks.json y `stock/zones/eval_labels.json`: «carpeta/foto» → zonas que
 * muestra, con «!» delante si la foto muestra la zona pero NO cumple la toma, p. ej. con ropa). Para cada
 * foto y zona: si la zona dispararía (criterios clínicos y, en el rostro, la estandarización) y, si no, por
 * qué; y qué otras zonas la aceptarían por error. Informe: build/reports/zone_eval.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class ZoneEvalTest {
    private val root = File("src/test/resources/stock/zones")

    private class S(val w: Int, val h: Int, val face: Detection, val pose: Detection, val hands: List<ZoneCriteria.Hand>, val classes: ZoneCriteria.Classes?)

    private fun load(dir: String): Map<String, S> {
        val js = File(root, "$dir/landmarks.json"); if (!js.exists()) return emptyMap()
        val o = JSONObject(js.readText())
        return o.keys().asSequence().associateWith { n ->
            val e = o.getJSONObject(n); val w = e.getInt("w"); val h = e.getInt("h")
            val face = e.optJSONObject("face")?.let { f ->
                val mesh = f.getJSONArray("mesh")
                val m = f.optJSONArray("matrix")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
                LandmarkDetector.finishFace(com.photomed.app.align.FaceMeshMap.detectionOf({ i -> mesh.getJSONArray(i).let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() } },
                    mesh.length(), w, h, m))
            } ?: Detection.EMPTY
            val pose = e.optJSONArray("pose")?.let { a ->
                Detection(LandmarkDetector.poseKeys((0 until a.length()).associateWith { i -> a.getJSONArray(i).let {
                    Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w, it.getDouble(2).toFloat()) } }))
            } ?: Detection.EMPTY
            val hands = e.optJSONArray("hands")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { ho ->
                val pts = ho.getJSONArray("points")
                ZoneCriteria.Hand((0 until pts.length()).map { j -> pts.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w) } }, ho.getString("label"))
            } } }.orEmpty()
            val classes = e.optString("classes", "").takeIf { it.length == 96 * 128 }?.let { str ->
                // la máscara se guardó a 96×128 sea cual sea la foto: se reescala a la proporción de la foto
                // (en la cámara el cuadro y la máscara tienen la misma)
                val hh = (96f * h / w).toInt().coerceAtLeast(1)
                ZoneCriteria.Classes(96, hh, ByteArray(96 * hh) { i -> (str[((i / 96) * 128 / hh) * 96 + i % 96] - '0').toByte() })
            }
            S(w, h, face, pose, hands, classes)
        }
    }

    /** Por qué no dispararía la zona [z] con la foto [s] (vacío: dispararía). */
    private fun verdict(z: Zone, s: S): List<String> {
        val spec = ZoneAlignment.of(z)
        val crit = if (ZoneCriteria.applies(z)) ZoneCriteria.hints(z, ZoneCriteria.Scene(
            if (spec.detector == Detector.POSE) s.pose.points else emptyMap(),
            // como en la cámara: el detector de manos solo en las zonas de manos
            if (ZoneCriteria.needsHands(z)) s.hands.ifEmpty { null } else null, s.classes, s.h / s.w.toFloat(), face = s.face.points)) else emptyList()
        val std = if (spec.detector == Detector.FACE && s.face.points.size >= 4) Standardization.evaluate(z, s.face, null).let { e ->
            if (AutoCapture.ready(e.copy(status = if (e.hints.isEmpty()) MatchStatus.MATCH else e.status))) emptyList() else e.hints } else emptyList()
        // «Centre la zona» es una indicación de encuadre que se corrige en vivo (no un incumplimiento de la vista)
        return (crit + std).filter { "Centre la zona" !in it }
    }

    @Test
    fun labeledPhotos() {
        val lf = File(root, "eval_labels.json")
        assumeTrue("sin fotos etiquetadas locales", lf.exists())
        val labels = JSONObject(lf.readText())
        val cache = HashMap<String, Map<String, S>>()
        data class R(val zone: Zone, val photo: String, val expectOk: Boolean, val why: List<String>)
        val rows = ArrayList<R>(); val falseAccepts = ArrayList<String>()
        val zones = Zone.entries.filter { it != Zone.FREE && it != Zone.FACE_FRONTAL }
        // por toma: fotos de otras tomas (deben rechazarse) y si las aceptó
        val crossNeg = HashMap<Zone, Pair<Int, Int>>()
        for (key in labels.keys().asSequence().sorted()) {
            val dir = key.substringBefore('/'); val name = key.substringAfter('/')
            // con copia girada 180° (r_…): fotos hechas al revés que la guía (la guía se puede girar); vale la mejor
            val all = cache.getOrPut(dir) { load(dir) }
            val s0 = all[name] ?: continue
            val sr = all["r_$name"]
            val arr = labels.getJSONArray(key)
            val labeled = (0 until arr.length()).map { arr.getString(it) }
            val labeledZones = labeled.mapNotNull { l -> Zone.fromId(l.removePrefix("!"))?.let { it to !l.startsWith("!") } }
            fun best(z: Zone) = listOfNotNull(s0, sr).map { verdict(z, it) }.minBy { it.size }
            val s = s0
            labeledZones.forEach { (z, ok) -> rows += R(z, key, ok, best(z)) }
            // otras zonas que la aceptarían (falsos disparos)
            zones.filter { z -> labeledZones.none { it.first.guide == z.guide } }.forEach { z ->
                val acc = best(z).isEmpty() && (ZoneAlignment.of(z).detector != Detector.FACE || s.face.points.size >= 4) && ZoneCriteria.applies(z)
                if (acc) falseAccepts += "${z.id} ← $key"
                val (n, fa) = crossNeg[z] ?: (0 to 0); crossNeg[z] = (n + 1) to (fa + if (acc) 1 else 0)
            }
        }
        val md = StringBuilder("# Evaluación con fotos etiquetadas\n\n| Zona | Cumplen (esperado) | Aceptadas | Muestran la zona sin cumplir (esperado rechazo) | Rechazadas | Motivos de rechazo de las que cumplen |\n|---|---|---|---|---|---|\n")
        rows.groupBy { it.zone }.toSortedMap(compareBy { it.ordinal }).forEach { (z, rs) ->
            val pos = rs.filter { it.expectOk }; val neg = rs.filter { !it.expectOk }
            val reasons = pos.filter { it.why.isNotEmpty() }.flatMap { it.why }.groupingBy { it.take(50) }.eachCount().entries.sortedByDescending { it.value }.take(4)
                .joinToString("; ") { "${it.key} (${it.value})" }
            md.append("| ${z.id} | ${pos.size} | ${pos.count { it.why.isEmpty() }} | ${neg.size} | ${neg.count { it.why.isNotEmpty() }} | $reasons |\n")
        }
        // exactitud equilibrada por toma: (aceptadas de las que cumplen + rechazadas de las que no) / 2; las que no
        // cumplen: las de la toma sin cumplir («!») y las de otras tomas
        md.append("\n## Precisión por toma (verificación de la IA)\n\n| Toma | Cumplen | Aceptadas | No cumplen (misma toma + otras) | Rechazadas | Exactitud equilibrada |\n|---|---|---|---|---|---|\n")
        val summary = JSONObject()
        Zone.entries.filter { it != Zone.FREE }.forEach { z ->
            val rs = rows.filter { it.zone == z }
            val pos = rs.filter { it.expectOk }; val neg = rs.filter { !it.expectOk }
            val (cn, cfa) = crossNeg[z] ?: (0 to 0)
            val tp = pos.count { it.why.isEmpty() }; val nN = neg.size + cn; val tn = neg.count { it.why.isNotEmpty() } + (cn - cfa)
            val bal = if (pos.isEmpty() || nN == 0) null else (tp.toFloat() / pos.size + tn.toFloat() / nN) / 2
            md.append("| ${z.id} | ${pos.size} | $tp | $nN | $tn | ${bal?.let { "%.0f %%".format(it * 100) } ?: "—"} |\n")
            summary.put(z.id, JSONObject().put("pos", pos.size).put("tp", tp).put("neg", nN).put("tn", tn).put("bal", (bal ?: -1f).toDouble()))
        }
        File("build/reports").mkdirs(); File("build/reports/zone_eval_summary.json").writeText(summary.toString(1))
        // medidas de expresión de las fotos del rostro frontal (para calibrar la expresión neutra)
        md.append("\n## Expresión medida (rostro frontal)\n\n| Foto | Cumple | Ojos | Boca abierta | Ancho boca | Comisuras | Cejas |\n|---|---|---|---|---|---|---|\n")
        for (key in labels.keys().asSequence().sorted()) {
            val arr = labels.getJSONArray(key); val l0 = arr.optString(0, "")
            if (l0.removePrefix("!") != "rostro_frontal") continue
            val s0 = cache[key.substringBefore('/')]?.get(key.substringAfter('/')) ?: continue
            val ex = com.photomed.app.align.Expression.measure(s0.face.points) ?: continue
            md.append("| $key | ${if (l0.startsWith("!")) "no" else "sí"} | %.2f | %.2f | %.2f | %.2f | %.2f |\n".format(ex.eyes, ex.mouthOpen, ex.mouthWidth, ex.smileLift, ex.brows))
        }
        md.append("\n## Rechazos de fotos que cumplen\n\n")
        rows.filter { it.expectOk && it.why.isNotEmpty() }.forEach { md.append("- ${it.zone.id} · ${it.photo}: ${it.why.joinToString(" | ")}\n") }
        md.append("\n## Aceptadas sin cumplir\n\n")
        rows.filter { !it.expectOk && it.why.isEmpty() }.forEach { md.append("- ${it.zone.id} · ${it.photo}\n") }
        md.append("\n## Otras zonas que las aceptan (${falseAccepts.size})\n\n")
        falseAccepts.forEach { md.append("- $it\n") }
        File("build/reports").mkdirs(); File("build/reports/zone_eval.md").writeText(md.toString())
        println(md)
    }
}
