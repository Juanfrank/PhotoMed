package com.photomed.app

import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.Pt
import com.photomed.app.align.SkinLesions
import com.photomed.app.align.SkinLesions.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.hypot

/**
 * Hallazgos de la piel ([SkinLesions]): casos sintéticos (mancha, arañita, várice, piel limpia con sombra) y,
 * si están las fotos locales (no versionadas), lesiones con máscara de dermatólogo (IoU de las manchas),
 * fotos de DermNet por clase y fotos corporales. Informe: build/reports/skin_lesions.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkinLesionsTest {
    private val root = File("src/test/resources/stock/zones")
    private val skinRgb = rgb(222, 180, 155)

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Piel lisa con una sombra suave de izquierda a derecha (como un miembro iluminado de lado). */
    private fun skin(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val k = 1f - 0.18f * (i % w) / w
        rgb((222 * k).toInt(), (180 * k).toInt(), (155 * k).toInt())
    }

    @Test fun cleanSkinWithShadingHasNoFindings() {
        val w = 300; val h = 400
        val out = SkinLesions.detect(skin(w, h), w, h, null, Kind.entries.toSet())
        assertTrue("sin hallazgos en piel lisa: ${out.mapNotNull { it.metrics?.name }}",
            out.none { it.layer != AnnotLayer.CELLULITE || it.metrics?.features?.firstOrNull()?.second ?: 0f > 3f })
    }

    @Test fun brownSpotIsFoundWithItsSize() {
        val w = 300; val h = 400; val px = skin(w, h)
        for (y in 0 until h) for (x in 0 until w) if (hypot(x - 150f, y - 200f) < 15f) px[y * w + x] = rgb(150, 100, 70)
        val spots = SkinLesions.detect(px, w, h, null, setOf(Kind.SPOTS), mmPerUnit = 100f).filter { it.layer == AnnotLayer.SPOTS }
        assertEquals(1, spots.size)
        val f = spots[0].metrics!!.features.toMap()
        // 100 mm por ancho de 300 px → diámetro 30 px = 10 mm
        assertEquals(10f, f.getValue("Diámetro mayor, mm"), 1.5f)
        assertTrue(f.getValue("Contraste ΔL* frente a la piel vecina") > 10f)
        val c = spots[0].points.let { p -> Pt(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat()) }
        assertEquals(0.5f, c.x, 0.02f); assertEquals(200f / w, c.y, 0.02f)
    }

    @Test fun thinRedLineIsASpiderVeinAndBlueBandAVaricose() {
        val w = 300; val h = 400; val px = skin(w, h)
        for (x in 60..140) for (dy in 0..1) px[(100 + x / 4 + dy) * w + x] = rgb(215, 120, 120)     // trazo rojo fino
        for (y in 150..380) for (dx in -4..4) px[y * w + 220 + dx] = rgb(165, 150, 165)               // banda azulada
        val out = SkinLesions.detect(px, w, h, null, setOf(Kind.SPIDER, Kind.VARICES))
        assertEquals(1, out.count { it.layer == AnnotLayer.SPIDER_VEINS })
        val v = out.filter { it.layer == AnnotLayer.VARICES }
        assertEquals(1, v.size)
        val len = v[0].metrics!!.features.toMap().getValue("Longitud, % del ancho")
        assertTrue("longitud de la várice $len", len in 55f..85f)
    }

    @Test fun excludedPolygonsAreIgnored() {
        val w = 300; val h = 400; val px = skin(w, h)
        for (y in 0 until h) for (x in 0 until w) if (hypot(x - 150f, y - 200f) < 15f) px[y * w + x] = rgb(150, 100, 70)
        val ex = listOf(listOf(Pt(0.4f, 0.55f), Pt(0.6f, 0.55f), Pt(0.6f, 0.78f), Pt(0.4f, 0.78f)))
        assertTrue(SkinLesions.detect(px, w, h, null, setOf(Kind.SPOTS), exclude = ex).isEmpty())
    }

    private fun load(f: File): Triple<IntArray, Int, Int>? {
        val im = BitmapFactory.decodeFile(f.path) ?: return null
        val sc = minOf(1f, 384f / im.width)
        val w = (im.width * sc).toInt().coerceAtLeast(1); val h = (im.height * sc).toInt().coerceAtLeast(1)
        val s = Bitmap.createScaledBitmap(im, w, h, true)
        return Triple(IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }, w, h)
    }

    private val lmCache = HashMap<File, org.json.JSONObject>()

    /** Piel (cuerpo o cara) de la segmentación guardada en landmarks.json de la carpeta, en una rejilla w×h. */
    private fun skinOf(f: File, w: Int, h: Int): BooleanArray? {
        val js = lmCache.getOrPut(f.parentFile) { File(f.parentFile, "landmarks.json").takeIf { it.exists() }
            ?.let { org.json.JSONObject(it.readText()) } ?: org.json.JSONObject() }
        val str = js.optJSONObject(f.name)?.optString("classes", "")?.takeIf { it.length == 96 * 128 } ?: return null
        return BooleanArray(w * h) { i -> str[((i / w) * 128 / h) * 96 + (i % w) * 96 / w].let { it == '2' } }
    }

    @Test fun realPhotos() {
        val rep = StringBuilder("# Hallazgos de la piel (SkinLesions)\n\n")
        // 1) manchas frente a máscaras de dermatólogo (lesiones pigmentadas, toda la imagen es piel)
        val les = File(root, "eval_lesion").listFiles { f -> f.name.matches(Regex("les_\\d+\\.jpg")) }.orEmpty().sorted()
        if (les.isNotEmpty()) {
            val ious = les.mapNotNull { f ->
                val (px, w, h) = load(f) ?: return@mapNotNull null
                val gt = BitmapFactory.decodeFile(f.path.replace(".jpg", "_mask.png")) ?: return@mapNotNull null
                val pred = BooleanArray(w * h)
                SkinLesions.detect(px, w, h, BooleanArray(w * h) { true }, setOf(Kind.SPOTS), closeUp = true)
                    .forEach { l -> SkinLesions.fillPoly(pred, w, h, l.points.map { Pt(it.x * w, it.y * w) }) }
                var inter = 0; var uni = 0
                for (y in 0 until h) for (x in 0 until w) {
                    val g = (gt.getPixel(x * gt.width / w, y * gt.height / h) and 0xFF) > 127; val p = pred[y * w + x]
                    if (g && p) inter++; if (g || p) uni++
                }
                inter / uni.coerceAtLeast(1).toFloat()
            }.sorted()
            val mean = ious.average(); val med = ious[ious.size / 2]; val hit = ious.count { it > 0.5f }
            rep.append("## Manchas frente a máscaras (n=${ious.size})\n\nIoU media %.2f, mediana %.2f; IoU > 0,5: %d/%d\n\n".format(mean, med, hit, ious.size))
            assertTrue("IoU media de las manchas $mean", mean >= 0.45)
        }
        // 2) DermNet por clase: las fotos pigmentadas dan más manchas; las vasculares, más rojo en trazos
        val sk = File(root, "eval_skin")
        if (sk.isDirectory) {
            rep.append("## DermNet por clase (fotos con al menos un hallazgo)\n\n| clase | n | manchas | arañitas |\n|---|---|---|---|\n")
            for (c in listOf("pigmento", "nevus", "vascular", "acne")) {
                val fs = sk.listFiles { f -> f.name.startsWith("${c}_") }.orEmpty().sorted()
                var sp = 0; var ar = 0
                fs.forEach { f -> val (px, w, h) = load(f) ?: return@forEach
                    val o = SkinLesions.detect(px, w, h, null, setOf(Kind.SPOTS, Kind.SPIDER))
                    if (o.any { it.layer == AnnotLayer.SPOTS }) sp++; if (o.any { it.layer == AnnotLayer.SPIDER_VEINS }) ar++ }
                rep.append("| $c | ${fs.size} | $sp | $ar |\n")
            }
            rep.append("\n")
        }
        // 3) fotos corporales: cuántos hallazgos y tiempo (sin etiquetas: comprobación de cordura)
        rep.append("## Fotos corporales (piel de la segmentación guardada, 96×128)\n\n| carpeta | fotos | manchas | arañitas | várices | celulitis (grado medio) | ms/foto |\n|---|---|---|---|---|---|---|\n")
        for ((dir, kinds) in listOf("piernas" to SkinLesions.kindsFor(com.photomed.app.data.Zone.LEGS),
                "gluteos" to SkinLesions.kindsFor(com.photomed.app.data.Zone.GLUTES), "espalda" to setOf(Kind.SPOTS, Kind.SPIDER),
                "abdomen_frontal" to setOf(Kind.SPOTS, Kind.SPIDER), "brazo" to setOf(Kind.SPOTS, Kind.SPIDER))) {
            val fs = File(root, dir).listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }.orEmpty().sorted().take(20)
            if (fs.isEmpty()) continue
            var s = 0; var a = 0; var v = 0; val g = ArrayList<Float>(); var ms = 0L
            fs.forEach { f -> val (px, w, h) = load(f) ?: return@forEach
                val t0 = System.nanoTime()
                val o = SkinLesions.detect(px, w, h, skinOf(f, w, h) ?: return@forEach, kinds)
                ms += (System.nanoTime() - t0) / 1_000_000
                s += o.count { it.layer == AnnotLayer.SPOTS }; a += o.count { it.layer == AnnotLayer.SPIDER_VEINS }
                v += o.count { it.layer == AnnotLayer.VARICES }
                o.firstOrNull { it.layer == AnnotLayer.CELLULITE && it.metrics != null }?.metrics?.features?.toMap()?.get("Grado orientativo (0–3)")?.let { g += it }
            }
            rep.append("| $dir | ${fs.size} | $s | $a | $v | ${if (g.isEmpty()) "—" else "%.1f".format(g.average())} | ${ms / fs.size} |\n")
        }
        File("build/reports").mkdirs(); File("build/reports/skin_lesions.md").writeText(rep.toString())
        assumeTrue("sin fotos locales", les.isNotEmpty())
    }
}
