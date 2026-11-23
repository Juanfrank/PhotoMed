package com.photomed.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneCriteria
import com.photomed.app.data.Zone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Criterios clínicos de cada zona del cuerpo ([ZoneCriteria]) con DIBUJOS PROPIOS (generados aquí,
 * sin fotos de terceros): para cada zona, la toma correcta (debe aceptarse) y tomas incorrectas
 * (deben rechazarse con su indicación): dedo doblado, una sola mano, mano cortada, palma en vez de
 * dorso, pie cortado, abdomen con ropa, brazos tapando, codo doblado, brazo a otro ángulo…
 *
 * Los puntos del cuerpo y de las manos salen de la geometría del dibujo; la piel y la ropa se
 * clasifican a partir de los PÍXELES del dibujo (como haría la segmentación), de modo que también se
 * prueba la medida de «zona descubierta». Imágenes: build/reports/zone_criteria/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoneCriteriaDrawingsTest {
    private val W = 300; private val H = 400
    /** Resolución de las clases por píxel (como la máscara de la segmentación de un cuadro de la cámara). */
    private val CW = 192; private val CH = 256
    private val BG = Color.rgb(205, 215, 228); private val SKIN = Color.rgb(222, 170, 140)
    private val FACE = Color.rgb(230, 182, 152); private val CLOTH = Color.rgb(52, 84, 160); private val NAIL = Color.rgb(245, 225, 220)
    private val L = Landmarks

    /** Un caso: dibujo, escena (puntos de la geometría, clases de los píxeles) y si debe aceptarse. */
    class Case(val zone: Zone, val name: String, val ok: Boolean, val expect: String?, val bmp: Bitmap, val scene: ZoneCriteria.Scene)

    // ---------- utilidades de dibujo ----------
    private fun canvas(): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(BG); return b to c
    }
    private fun fill(col: Int) = Paint().apply { color = col; style = Paint.Style.FILL; isAntiAlias = false }
    private fun stroke(col: Int, w: Float) = Paint().apply { color = col; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; isAntiAlias = false }
    /** Punto en px → unidades del ancho. */
    private fun u(x: Float, y: Float) = Pt(x / W, y / W)

    /** Clases por píxel a partir del color del dibujo (piel, piel del rostro, ropa, fondo), a 96×128. */
    private fun classes(b: Bitmap): ZoneCriteria.Classes {
        // misma proporción que el dibujo (vertical u horizontal si está girado)
        val (CW, CH) = if (b.width <= b.height) CW to CH else CH to CW
        val s = Bitmap.createScaledBitmap(b, CW, CH, false)
        val px = IntArray(CW * CH).also { s.getPixels(it, 0, CW, 0, 0, CW, CH) }
        val pal = listOf(BG to FaceLabels.BG, SKIN to FaceLabels.BODY_SKIN, NAIL to FaceLabels.BODY_SKIN, FACE to FaceLabels.FACE_SKIN, CLOTH to FaceLabels.CLOTHES)
        val out = ByteArray(px.size) { i ->
            val c = px[i]
            pal.minBy { (p, _) -> Math.abs(Color.red(p) - Color.red(c)) + Math.abs(Color.green(p) - Color.green(c)) + Math.abs(Color.blue(p) - Color.blue(c)) }.second.toByte()
        }
        return ZoneCriteria.Classes(CW, CH, out, px)
    }

    // ---------- figura humana de frente / de espaldas / de lado ----------
    /**
     * Cuerpo de pie, de frente ([back] = de espaldas, sin cara visible), con [tiltDeg] de inclinación
     * de hombros, brazos separados [armAbd]° (codo flexionado [elbowBend]°), rodillas flexionadas
     * [kneeBend]°, pies a [feetGap] px, y ropa: [shirt] (tronco), [pants] (piernas), [sleeve] (brazos).
     */
    private fun figure(c: Canvas, cx: Float = 150f, top: Float = 40f, scale: Float = 1f, back: Boolean = false, tiltDeg: Float = 0f,
                       armAbd: Float = 25f, elbowBend: Float = 0f, armsCover: Boolean = false, kneeBend: Float = 0f, feetGap: Float = 40f,
                       shirt: Boolean = false, pants: Boolean = false, shorts: Boolean = true, sleeve: Boolean = false): Map<String, Pt> {
        val k = scale
        val t = Math.toRadians(tiltDeg.toDouble())
        val shW = 110f * k; val hipW = 80f * k
        val shY = top + 70f * k; val hipY = shY + 150f * k
        val sl = Pt(cx + shW / 2 * cos(t).toFloat(), shY + shW / 2 * sin(t).toFloat())      // hombro izquierdo del paciente (derecha de la imagen)
        val sr = Pt(cx - shW / 2 * cos(t).toFloat(), shY - shW / 2 * sin(t).toFloat())
        val hl = Pt(cx + hipW / 2, hipY); val hr = Pt(cx - hipW / 2, hipY)
        // cabeza y cuello
        c.drawRect(cx - 14 * k, top + 40 * k, cx + 14 * k, shY + 5, fill(SKIN))
        c.drawOval(cx - 28 * k, top, cx + 28 * k, top + 62 * k, fill(if (back) Color.rgb(70, 50, 40) else FACE))
        // tronco
        val torso = Path().apply { moveTo(sr.x, sr.y); lineTo(sl.x, sl.y); lineTo(hl.x + 6, hl.y); lineTo(hr.x - 6, hr.y); close() }
        c.drawPath(torso, fill(if (shirt) CLOTH else SKIN))
        val kneeY = hipY + 95f * k
        // pelvis (glúteos de espaldas) rellena hasta el arranque de los muslos
        val pelvis = Path().apply { moveTo(hr.x - 8, hipY - 4); lineTo(hl.x + 8, hipY - 4); lineTo(hl.x + 4, hipY + 45 * k); lineTo(hr.x - 4, hipY + 45 * k); close() }
        c.drawPath(pelvis, fill(if (pants) CLOTH else SKIN))
        // piernas rectas de la cadera al tobillo (pies a [feetGap]); la rodilla flexiona la pierna [kneeBend]°
        val pts = HashMap<String, Pt>()
        fun leg(h: Pt, sign: Float, keyK: String, keyA: String) {
            val ankTarget = Pt(cx + sign * feetGap / 2, kneeY + 95f * k)
            val dx = ankTarget.x - h.x; val dy = ankTarget.y - h.y; val len = kotlin.math.hypot(dx, dy)
            val kn = Pt(h.x + dx / 2, h.y + dy / 2)
            val bend = Math.toRadians((sign * kneeBend).toDouble())
            val ux = dx / len; val uy = dy / len
            val rx = (ux * cos(bend) - uy * sin(bend)).toFloat(); val ry = (ux * sin(bend) + uy * cos(bend)).toFloat()
            val an = Pt(kn.x + rx * len / 2, kn.y + ry * len / 2)
            val legP = stroke(if (pants) CLOTH else SKIN, 38f * k)
            c.drawLine(h.x, h.y, kn.x, kn.y, legP); c.drawLine(kn.x, kn.y, an.x, an.y, legP)
            c.drawOval(an.x - 13 * k, an.y - 4, an.x + 13 * k, an.y + 14 * k, fill(SKIN))
            pts[keyK] = u(kn.x, kn.y); pts[keyA] = u(an.x, an.y)
        }
        leg(hl, 1f, L.KNEE_L, L.ANKLE_L); leg(hr, -1f, L.KNEE_R, L.ANKLE_R)
        if (shorts || pants) c.drawRect(hr.x - 18 * k, hipY - 12 * k, hl.x + 18 * k, hipY + 26 * k, fill(CLOTH))
        // brazos: separados [armAbd]° del tronco (desde la vertical), codo flexionado
        fun arm(s: Pt, sign: Float, keyE: String, keyW: String, keyI: String) {
            val a = Math.toRadians(armAbd.toDouble())
            val e = Pt(s.x + sign * 78f * k * sin(a).toFloat(), s.y + 78f * k * cos(a).toFloat())
            val b = Math.toRadians((armAbd - sign * 0f + elbowBend).toDouble())
            var w = Pt(e.x + sign * 72f * k * sin(Math.toRadians(armAbd.toDouble() - elbowBend)).toFloat(), e.y + 72f * k * cos(Math.toRadians(armAbd.toDouble() - elbowBend)).toFloat())
            if (armsCover) w = Pt(cx + sign * 8f, hipY - 40f * k)
            val armP = stroke(if (sleeve) CLOTH else SKIN, 22f * k)
            c.drawLine(s.x, s.y, e.x, e.y, armP); c.drawLine(e.x, e.y, w.x, w.y, armP)
            c.drawCircle(w.x, w.y + 8 * k, 11 * k, fill(SKIN))
            pts[keyE] = u(e.x, e.y); pts[keyW] = u(w.x, w.y); pts[keyI] = u(w.x, w.y + 18 * k)
            @Suppress("UNUSED_VARIABLE") val unused = b
        }
        arm(sl, 1f, L.ELBOW_L, L.WRIST_L, L.INDEX_L); arm(sr, -1f, L.ELBOW_R, L.WRIST_R, L.INDEX_R)
        pts[L.SHOULDER_L] = u(sl.x, sl.y); pts[L.SHOULDER_R] = u(sr.x, sr.y); pts[L.HIP_L] = u(hl.x, hl.y); pts[L.HIP_R] = u(hr.x, hr.y)
        if (!back) { pts[L.NOSE] = u(cx, top + 34 * k); pts[L.MOUTH_L] = u(cx + 8 * k, top + 46 * k); pts[L.MOUTH_R] = u(cx - 8 * k, top + 46 * k) }
        return pts
    }

    /** Cuerpo de perfil (abdomen lateral): hombros y caderas superpuestos; brazos subidos o colgando. */
    private fun side(c: Canvas, armsDown: Boolean = false, shirt: Boolean = false, frontal: Boolean = false): Map<String, Pt> {
        if (frontal) return figure(c)
        val cx = 150f; val top = 40f; val shY = 110f; val hipY = 260f
        c.drawOval(cx - 26, top, cx + 26, top + 62, fill(FACE))
        c.drawRect(cx - 12, top + 50, cx + 12, shY + 5, fill(SKIN))
        val torso = Path().apply { moveTo(cx - 30, shY); lineTo(cx + 34, shY); lineTo(cx + 48, hipY - 60); lineTo(cx + 36, hipY); lineTo(cx - 34, hipY); close() }
        c.drawPath(torso, fill(if (shirt) CLOTH else SKIN))
        c.drawRect(cx - 38, hipY - 10, cx + 40, hipY + 25, fill(CLOTH))
        val legP = stroke(SKIN, 34f); c.drawLine(cx, hipY, cx + 4, 350f, legP); c.drawLine(cx + 4, 350f, cx, 392f, legP)
        val pts = HashMap<String, Pt>()
        val armP = stroke(SKIN, 22f)
        val sh = Pt(cx, shY + 6)
        val (e, w) = if (armsDown) Pt(cx + 4, shY + 80) to Pt(cx + 6, shY + 150) else Pt(cx + 30, shY - 40) to Pt(cx + 10, top + 10)
        c.drawLine(sh.x, sh.y, e.x, e.y, armP); c.drawLine(e.x, e.y, w.x, w.y, armP)
        pts[L.SHOULDER_R] = u(sh.x, sh.y); pts[L.SHOULDER_L] = u(sh.x + 4, sh.y)
        pts[L.HIP_R] = u(cx - 4, hipY); pts[L.HIP_L] = u(cx + 4, hipY)
        pts[L.ELBOW_R] = u(e.x, e.y); pts[L.WRIST_R] = u(w.x, w.y)
        pts[L.NOSE] = u(cx + 26, top + 34)
        return pts
    }

    // ---------- manos (21 puntos de MediaPipe) ----------
    /**
     * Mano con los dedos hacia abajo ([up] = hacia arriba), muñeca en ([wx], [wy]). [thumbOut]: pulgar
     * hacia fuera de la imagen (−1 izquierda, +1 derecha). [bent]: índices de dedos doblados (0 pulgar…4 meñique).
     * [label]: etiqueta de MediaPipe. [nails]: uñas visibles (dorso).
     */
    private fun hand(c: Canvas, wx: Float, wy: Float, thumbSide: Float, bent: Set<Int> = emptySet(), up: Boolean = false, nails: Boolean = false,
                     spread: Float = 5f, lenK: Float = 1f, rotDeg: Float = 0f): List<Pt> {
        val dir = if (up) -1f else 1f
        val loc = ArrayList<Pt>(21)                     // en px, mano con los dedos hacia abajo
        loc += Pt(wx, wy)
        // pulgar: nace en el lateral de la palma y va hacia fuera y hacia los dedos
        val tb = Pt(wx + thumbSide * 22, wy + dir * 22)
        for (i in 0..3) loc += if (0 in bent && i >= 2) Pt(tb.x + thumbSide * 13f - thumbSide * 9f * (i - 1), tb.y + dir * (15f + 12f * (i - 1)))
            else Pt(tb.x + thumbSide * 13f * lenK * i, tb.y + dir * 15f * lenK * i)
        // cuatro dedos en abanico ([spread]° entre dedos vecinos), de longitud × [lenK]
        for (f in 1..4) {
            val base = Pt(wx + thumbSide * (21f - (f - 1) * 14f), wy + dir * 68)
            val seg = (if (f == 2) 21f else if (f == 4) 15f else 19f) * lenK
            val a = Math.toRadians((thumbSide * (2.5f - f) * spread).toDouble())
            val ux = sin(a).toFloat(); val uy = cos(a).toFloat()
            val js = (0..3).map { j -> Pt(base.x + ux * seg * j, base.y + dir * uy * seg * j) }.toMutableList()
            if (f in bent) { js[2] = Pt(base.x - thumbSide * 3, base.y + dir * seg * 1.1f); js[3] = Pt(base.x - thumbSide * 2, base.y + dir * seg * 0.4f) }
            loc += js
        }
        // giro de toda la mano alrededor de la muñeca
        val r = Math.toRadians(rotDeg.toDouble()); val cr = cos(r).toFloat(); val sr = sin(r).toFloat()
        val p = loc.map { q -> Pt(wx + (q.x - wx) * cr - (q.y - wy) * sr, wy + (q.x - wx) * sr + (q.y - wy) * cr) }
        // dibujo: muñeca, palma (muñeca, base del pulgar y nudillos) y cada falange
        val wv = Pt(-sr * dir, cr * dir)   // dirección de la mano
        c.drawLine(p[0].x, p[0].y, p[0].x - wv.x * 30, p[0].y - wv.y * 30, stroke(SKIN, 44f))
        val palm = Path().apply { moveTo(p[0].x, p[0].y); for (i in listOf(1, 2, 5, 9, 13, 17)) lineTo(p[i].x, p[i].y); close() }
        c.drawPath(palm, fill(SKIN)); c.drawPath(palm, stroke(SKIN, 22f))
        for (chain in listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8), listOf(9, 10, 11, 12), listOf(13, 14, 15, 16), listOf(17, 18, 19, 20))) {
            val w = if (chain[0] == 1) 17f else 13f
            for (i in 1 until chain.size) c.drawLine(p[chain[i - 1]].x, p[chain[i - 1]].y, p[chain[i]].x, p[chain[i]].y, stroke(SKIN, w))
            val fi = (chain[0] - 1) / 4
            if (nails && fi !in bent) c.drawCircle(p[chain.last()].x, p[chain.last()].y, 4f, fill(NAIL))
        }
        return p.map { u(it.x, it.y) }
    }

    /** Dos manos lado a lado; [palm]: palmas a la cámara (pulgares hacia fuera) o dorsos (pulgares hacia dentro). */
    private fun handsScene(palm: Boolean, bent: Set<Int> = emptySet(), oneHand: Boolean = false, shiftY: Float = 0f, up: Boolean = false,
                           spread: Float = 5f, lenK: Float = 1f, rotDeg: Float = 0f): Pair<Bitmap, List<ZoneCriteria.Hand>> {
        val (b, c) = canvas()
        val wy = (if (up) 300f else 110f) + shiftY
        // mano derecha del paciente a la izquierda de la imagen; MediaPipe la etiqueta «Left» (supone espejo)
        val leftImgThumb = if (palm) -1f else 1f
        val hs = ArrayList<ZoneCriteria.Hand>()
        hs += ZoneCriteria.Hand(hand(c, if (oneHand) 150f else 80f, wy, leftImgThumb, bent, up, nails = !palm, spread = spread, lenK = lenK, rotDeg = -rotDeg), "Left")
        if (!oneHand) hs += ZoneCriteria.Hand(hand(c, 220f, wy, -leftImgThumb, emptySet(), up, nails = !palm, spread = spread, lenK = lenK, rotDeg = rotDeg), "Right")
        return b to hs
    }

    // ---------- pies ----------
    /** Dos pies vistos desde arriba (dorso) o la planta ([sole]), dedo gordo hacia dentro. */
    private fun feetScene(cut: Boolean = false, one: Boolean = false, sole: Boolean = false, strap: Boolean = false,
                          sock: Boolean = false, toeLen: Float = 0f, flat: Boolean = false): Bitmap {
        val (b, c) = canvas()
        val dy = if (cut) 95f else 0f
        val shade = Color.rgb(206, 152, 124)
        fun foot(x: Float, inward: Float) {
            // pierna (o tobillo) desde arriba
            if (!sole) c.drawRect(x - 24, 0f, x + 24, 120f + dy, fill(SKIN)) else c.drawRect(x - 22, 0f, x + 22, 60f + dy, fill(SKIN))
            // pie: talón estrecho, se ensancha hacia el antepié, borde interno más recto
            val f = Path().apply {
                moveTo(x - 26, 95f + dy); lineTo(x + 26, 95f + dy)
                quadTo(x + 42 - inward * 6, 210f + dy, x + 40 - inward * 2, 300f + dy)
                lineTo(x - 40 - inward * 2, 300f + dy)
                quadTo(x - 38 - inward * 6, 210f + dy, x - 26, 95f + dy); close()
            }
            c.drawPath(f, fill(SKIN))
            // cinco dedos: el gordo (más grande) del lado interno, los demás decreciendo hacia fuera
            // dedos que sobresalen del antepié (borde en «nube»: una curva por dedo) con el surco entre dedos
            // algo más oscuro, como en un pie real; [flat]: sin dedos distinguibles (borde liso)
            val rad = floatArrayOf(10f, 7f, 6.5f, 6f, 5.5f)
            val cx = floatArrayOf(31f, 11f, -6f, -22f, -36f)
            if (!flat) for (i in 0..4) {
                val tx = x + inward * cx[i]; val ty = 300f + dy + rad[i] * 1.3f + toeLen * (1f - i * 0.15f) + (if (i == 0) 3f else i * -2.5f)
                c.drawLine(tx, 292f + dy, tx, ty, stroke(SKIN, rad[i] * 2))
                c.drawCircle(tx, ty, rad[i], fill(SKIN))
                if (!sole) c.drawCircle(tx, ty + rad[i] * 0.15f, rad[i] * 0.62f, fill(NAIL))
            }
            if (!flat) for (i in 0..3) {
                val gx = x + inward * (cx[i] + cx[i + 1]) / 2
                c.drawLine(gx, 282f + dy, gx, 304f + dy + toeLen * 0.5f, stroke(shade, 2f))
            }
            // calcetín: tapa el antepié y los dedos
            if (sock) c.drawRect(x - 46, 240f + dy, x + 46, 330f + dy + toeLen, fill(CLOTH))
            if (sole) {
                // almohadillas del talón y del antepié, arco plantar
                c.drawOval(x - 24, 105f + dy, x + 24, 165f + dy, stroke(shade, 3f))
                c.drawOval(x - 34, 245f + dy, x + 34, 290f + dy, stroke(shade, 3f))
                c.drawLine(x + inward * 16, 170f + dy, x + inward * 22, 240f + dy, stroke(shade, 3f))
            }
        }
        // pie derecho del paciente a la izquierda de la imagen: dedo gordo hacia dentro (+1)
        foot(if (one) 150f else 90f, 1f); if (!one) foot(210f, -1f)
        // tira de sandalia que cruza el pie (lo parte en dos manchas de piel)
        if (strap) c.drawRect(0f, 200f + dy, W.toFloat(), 228f + dy, fill(CLOTH))
        return b
    }

    // ---------- casos ----------
    /**
     * Caso de una zona del cuerpo. Con [center], el dibujo se desplaza para que la parte del cuerpo de
     * la zona quede en el centro del cuadro (como se encuadra con la guía).
     */
    private fun case(z: Zone, name: String, ok: Boolean, expect: String?, center: Boolean = true, single: Boolean = false,
                     pose: (Map<String, Pt>) -> Map<String, Pt> = { it }, draw: (Canvas) -> Map<String, Pt>): Case {
        var dx = 0f; var dy = 0f
        if (center) {
            val (b0, c0) = canvas(); val p0 = pose(draw(c0))
            ZoneCriteria.partBox(z, ZoneCriteria.Scene(p0, null, classes(b0), single = single))?.let { (bx, axes) ->
                if (axes[0]) dx = (0.5f - (bx[0] + bx[2]) / 2) * W
                if (axes[1]) dy = (H / W.toFloat() / 2 - (bx[1] + bx[3]) / 2) * W
            }
        }
        val (b, c) = canvas(); c.translate(dx, dy)
        val p = pose(draw(c)).mapValues { (_, q) -> Pt(q.x + dx / W, q.y + dy / W) }
        return Case(z, name, ok, expect, b, ZoneCriteria.Scene(p, null, classes(b), single = single))
    }

    private fun cases(): List<Case> {
        val out = ArrayList<Case>()
        // manos
        for ((z, palm) in listOf(Zone.HANDS_PALMAR to true, Zone.HANDS_DORSAL to false)) {
            fun hc(name: String, ok: Boolean, expect: String?, r: Pair<Bitmap, List<ZoneCriteria.Hand>>) = out.add(Case(z, name, ok, expect, r.first, ZoneCriteria.Scene(emptyMap(), r.second, classes(r.first))))
            hc("correcta", true, null, handsScene(palm))
            hc("dedos más abiertos (se ajusta)", true, null, handsScene(palm, spread = 16f))
            hc("dedos más largos (se ajusta)", true, null, handsScene(palm, lenK = 1.25f))
            hc("dedos más cortos (se ajusta)", true, null, handsScene(palm, lenK = 0.85f))
            hc("manos giradas 30° (se ajusta)", true, null, handsScene(palm, rotDeg = 30f))
            hc("anular recogido", false, "falanges", handsScene(palm, bent = setOf(3)))
            hc("pulgar recogido", false, "pulgar", handsScene(palm, bent = setOf(0)))
            hc("puño", false, "falanges", handsScene(palm, bent = setOf(0, 1, 2, 3, 4)))
            hc("una sola mano", false, "dos manos", handsScene(palm, oneHand = true))
            // modo de una sola mano (selector Derecha / Izquierda): basta una, y debe cumplir igual
            fun hc1(name: String, ok: Boolean, expect: String?, r: Pair<Bitmap, List<ZoneCriteria.Hand>>) = out.add(Case(z, name, ok, expect, r.first, ZoneCriteria.Scene(emptyMap(), r.second, classes(r.first), single = true)))
            // sin los puntos del detector de manos (primeros planos, algunas luces): por la piel
            fun hs(name: String, ok: Boolean, expect: String?, b: Bitmap, single: Boolean = false) =
                out.add(Case(z, "sin puntos: $name", ok, expect, b, ZoneCriteria.Scene(emptyMap(), null, classes(b), single = single)))
            hs("correcta", true, null, handsScene(palm).first)
            hs("dedos más abiertos", true, null, handsScene(palm, spread = 16f).first)
            hs("puño", false, "dedos", handsScene(palm, bent = setOf(0, 1, 2, 3, 4)).first)
            hs("una sola mano", false, "dos manos", handsScene(palm, oneHand = true).first)
            hs("dedos fuera del cuadro", false, "entera dentro del cuadro", handsScene(palm, shiftY = 215f).first)
            hs("son los pies", false, "dedos", feetScene())
            hc1("modo una mano: una mano", true, null, handsScene(palm, oneHand = true))
            hc1("modo una mano: anular recogido", false, "falanges", handsScene(palm, oneHand = true, bent = setOf(3)))
            hc("dedos fuera del cuadro", false, "entera dentro del cuadro", handsScene(palm, shiftY = 215f))
            hc("dedos hacia arriba", false, "dedos hacia abajo", handsScene(palm, up = true))
            hc1("modo una mano: descentrada", false, "Centre", run { val (b, hs) = handsScene(palm, oneHand = true); val s2 = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                Canvas(s2).apply { drawColor(BG); drawBitmap(b, -75f, 0f, null) }; s2 to hs.map { h -> ZoneCriteria.Hand(h.points.map { Pt(it.x - 0.25f, it.y) }, h.label) } })
            hc(if (palm) "se ve el dorso" else "se ve la palma", false, if (palm) "muestre la palma" else "muestre el dorso", handsScene(!palm))
        }
        // pies
        for (z in listOf(Zone.FEET_DORSAL, Zone.FEET_PLANTAR)) {
            fun fc(name: String, ok: Boolean, expect: String?, b: Bitmap) = out.add(Case(z, name, ok, expect, b, ZoneCriteria.Scene(emptyMap(), null, classes(b))))
            val sole = z == Zone.FEET_PLANTAR
            fc("correcta", true, null, feetScene(sole = sole))
            fc("dedos fuera del cuadro", false, "completos", feetScene(cut = true, sole = sole))
            fc("un solo pie", false, "dos pies", feetScene(one = true, sole = sole))
            fc("un solo pie partido por una tira", false, "dos pies", feetScene(one = true, sole = sole, strap = true))
            fc("dedos más largos (se ajusta)", true, null, feetScene(sole = sole, toeLen = 12f))
            fun fc1(name: String, ok: Boolean, expect: String?, b: Bitmap) = out.add(Case(z, name, ok, expect, b, ZoneCriteria.Scene(emptyMap(), null, classes(b), single = true)))
            fc1("modo un pie: un pie", true, null, feetScene(one = true, sole = sole))
            fc1("modo un pie: partido por una tira", true, null, feetScene(one = true, sole = sole, strap = true))
            fc1("modo un pie: dedos tapados", false, "que se vean los dedos", feetScene(one = true, sole = sole, sock = true))
            fc1("modo un pie: descentrado", false, "Centre", Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { s2 -> Canvas(s2).apply { drawColor(BG); drawBitmap(feetScene(one = true, sole = sole), -80f, 0f, null) } })
            fc("dedos tapados (calcetín)", false, "que se vean los dedos", feetScene(sole = sole, sock = true))
            fc("sin dedos distinguibles", false, "que se vean los dedos", feetScene(sole = sole, flat = true))
            // la planta pedida y se ven las uñas (es el dorso)
            if (sole) fc("se ve el dorso (uñas)", false, "muestre la planta", feetScene(sole = false))
        }
        // abdomen frontal, tórax, espalda
        out += case(Zone.TORSO_FRONT, "correcta", true, null) { figure(it) }
        out += case(Zone.TORSO_FRONT, "con camiseta", false, "Descubra", draw = { figure(it, shirt = true) })
        out += case(Zone.TORSO_FRONT, "brazos sobre el abdomen", false, "Separe los brazos", draw = { figure(it, armsCover = true) })
        out += case(Zone.TORSO_FRONT, "hombros algo inclinados (5°, se acepta)", true, null) { figure(it, tiltDeg = 5f) }
        out += case(Zone.TORSO_FRONT, "hombros inclinados", false, "Hombros nivelados", draw = { figure(it, tiltDeg = 12f) })
        out += case(Zone.TORSO_FRONT, "de espaldas", false, "De frente", draw = { figure(it, back = true) })
        out += case(Zone.TORSO_FRONT, "descentrado", false, "Centre", center = false) { figure(it, cx = 230f) }
        out += case(Zone.BREASTS, "correcta", true, null) { figure(it) }
        out += case(Zone.BREASTS, "con camiseta", false, "Descubra", draw = { figure(it, shirt = true) })
        out += case(Zone.BACK, "correcta", true, null) { figure(it, back = true) }
        out += case(Zone.BACK, "con camiseta", false, "Descubra", draw = { figure(it, back = true, shirt = true) })
        out += case(Zone.BACK, "de frente", false, "De espaldas", draw = { figure(it) })
        // sin puntos del cuerpo (de espaldas el detector no ve a la persona): por la región de la guía
        fun noPose(z: Zone, name: String, ok: Boolean, expect: String?, draw: (Canvas) -> Unit) {
            val (b, c) = canvas(); draw(c); out += Case(z, name, ok, expect, b, ZoneCriteria.Scene(emptyMap(), null, classes(b)))
        }
        noPose(Zone.BACK, "sin puntos: espalda entera", true, null) { c -> figure(c, back = true, scale = 1.25f, top = -10f, cx = 150f) }
        noPose(Zone.BACK, "sin puntos: recorte que llena el cuadro", false, "espalda entera") { c -> c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), fill(SKIN)) }
        noPose(Zone.GLUTES, "sin puntos: recorte que llena el cuadro", false, "glúteos enteros") { c -> c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), fill(SKIN)) }
        // abdomen lateral
        for (z in listOf(Zone.TORSO_SIDE, Zone.TORSO_SIDE_L)) {
            out += case(z, "correcta (brazos arriba)", true, null) { side(it) }
            out += case(z, "brazos tapando el flanco", false, "brazos", draw = { side(it, armsDown = true) })
            out += case(z, "con camiseta", false, "Descubra", draw = { side(it, shirt = true) })
            out += case(z, "de frente", false, "de lado", draw = { side(it, frontal = true) })
        }
        // glúteos
        out += case(Zone.GLUTES, "correcta", true, null) { figure(it, back = true, shorts = false) }
        out += case(Zone.GLUTES, "con pantalón", false, "Descubra", draw = { figure(it, back = true, pants = true) })
        out += case(Zone.GLUTES, "de frente", false, "De espaldas", draw = { figure(it, shorts = false) })
        // piernas
        out += case(Zone.LEGS, "correcta", true, null) { figure(it, top = 10f, scale = 0.92f) }
        out += case(Zone.LEGS, "rodillas con flexión natural (se ajusta)", true, null) { figure(it, top = 10f, scale = 0.92f, kneeBend = 15f, feetGap = 70f) }
        out += case(Zone.LEGS, "rodilla flexionada", false, "Rodillas", draw = { figure(it, top = 10f, scale = 0.92f, kneeBend = 45f) })
        out += case(Zone.LEGS, "con pantalón", false, "Descubra", draw = { figure(it, top = 10f, scale = 0.92f, pants = true) })
        out += case(Zone.LEGS, "pies juntos", false, "Separe un poco los pies", draw = { figure(it, top = 10f, scale = 0.92f, feetGap = 0f) })
        out += case(Zone.LEGS, "pies fuera del cuadro", false, "Los pies dentro", center = false) { figure(it, top = 50f, scale = 0.98f) }
        out += case(Zone.LEGS, "descentradas", false, "Centre", center = false) { figure(it, top = 10f, scale = 0.92f, cx = 80f) }
        // modo de una sola pierna: el detector solo ve esa pierna
        fun oneLeg(name: String, ok: Boolean, expect: String?, draw: (Canvas) -> Map<String, Pt>) {
            out += case(Zone.LEGS, name, ok, expect, single = true, pose = { p -> p.filterKeys { it !in setOf(L.HIP_R, L.KNEE_R, L.ANKLE_R) } }, draw = draw)
        }
        oneLeg("modo una pierna: correcta", true, null) { figure(it, top = 10f, scale = 0.92f) }
        oneLeg("modo una pierna: rodilla flexionada", false, "Rodillas") { figure(it, top = 10f, scale = 0.92f, kneeBend = 45f) }
        oneLeg("modo una pierna: con pantalón", false, "Descubra") { figure(it, top = 10f, scale = 0.92f, pants = true) }
        // brazos
        for (z in listOf(Zone.ARM_R, Zone.ARM_L)) {
            out += case(z, "correcta (≈ 20°)", true, null) { figure(it, armAbd = 20f) }
            out += case(z, "brazo a 35° (se ajusta)", true, null) { figure(it, armAbd = 35f) }
            out += case(z, "codo con flexión natural (se ajusta)", true, null) { figure(it, armAbd = 20f, elbowBend = 18f) }
            out += case(z, "codo doblado", false, "Codo extendido", draw = { figure(it, armAbd = 20f, elbowBend = 50f) })
            out += case(z, "brazo pegado al cuerpo", false, "Separe un poco el brazo", draw = { figure(it, armAbd = 1f) })
            out += case(z, "brazo a 60°", false, "Baje el brazo", draw = { figure(it, armAbd = 60f) })
            out += case(z, "con manga", false, "Descubra", draw = { figure(it, armAbd = 20f, sleeve = true) })
            out += case(z, "brazo descentrado", false, "Centre", center = false) { figure(it, armAbd = 20f) }
            // primer plano del brazo sin ningún punto del cuerpo (el detector no lo ve): por la piel
            fun armClose(name: String, ok: Boolean, expect: String?, draw: (Canvas) -> Unit) {
                val (bm, cv) = canvas(); draw(cv); out += Case(z, "sin puntos: $name", ok, expect, bm, ZoneCriteria.Scene(emptyMap(), null, classes(bm)))
            }
            armClose("brazo recto", true, null) { it.drawLine(150f, 10f, 160f, 390f, stroke(SKIN, 56f)) }
            armClose("codo doblado", false, "Codo extendido") { it.drawLine(150f, 10f, 140f, 210f, stroke(SKIN, 56f)); it.drawLine(140f, 210f, 270f, 360f, stroke(SKIN, 50f)) }
            armClose("con manga", false, "brazo") { it.drawLine(150f, 10f, 160f, 390f, stroke(SKIN, 56f)); it.drawRect(100f, 0f, 210f, 230f, fill(CLOTH)) }
            armClose("solo un trozo", false, "brazo entero") { it.drawLine(150f, 150f, 152f, 260f, stroke(SKIN, 56f)) }
            // primer plano del brazo: sin caderas ni el otro brazo
            val keep = if (z == Zone.ARM_R) setOf(L.SHOULDER_R, L.ELBOW_R, L.WRIST_R, L.INDEX_R) else setOf(L.SHOULDER_L, L.ELBOW_L, L.WRIST_L, L.INDEX_L)
            out += case(z, "solo ese brazo (sin cadera)", true, null, pose = { p -> p.filterKeys { it in keep } }) { figure(it, armAbd = 20f) }
            // de espaldas el detector invierte izquierda y derecha: solo se ve entero el otro «lado»
            val swap = mapOf(L.SHOULDER_L to L.SHOULDER_R, L.ELBOW_L to L.ELBOW_R, L.WRIST_L to L.WRIST_R, L.INDEX_L to L.INDEX_R)
                .let { m -> m + m.entries.associate { (a, b) -> b to a } }
            out += case(z, "solo ese brazo, lado invertido por el detector", true, null,
                pose = { p -> p.filterKeys { it in keep }.mapKeys { swap[it.key] ?: it.key } }) { figure(it, armAbd = 20f) }
        }
        // cuello
        out += case(Zone.NECK, "correcta", true, null) { figure(it, top = 60f, scale = 1.6f) }
        out += case(Zone.NECK, "hombros inclinados", false, "Hombros nivelados", draw = { figure(it, top = 60f, scale = 1.6f, tiltDeg = 12f) })
        out += case(Zone.NECK, "con jersey de cuello alto", false, "Descubra", draw = { c -> figure(c, top = 60f, scale = 1.6f).also { c.drawRect(120f, 120f, 180f, 200f, fill(CLOTH)); c.drawRect(60f, 170f, 240f, 260f, fill(CLOTH)) } })
        return out
    }

    /** Profundidad de los surcos entre dedos: mano (dedos largos) frente a pie (dedos cortos), sin puntos de la mano. */
    @Test
    fun digitDepthSeparatesHandsFromFeet() {
        val hand = ZoneCriteria.feetOf(classes(handsScene(true).first)).map { it.digitDepth }
        val foot = ZoneCriteria.feetOf(classes(feetScene())).map { it.digitDepth }
        println("PROFUNDIDAD manos $hand pies $foot")
        assertTrue("manos $hand", hand.isNotEmpty() && hand.all { it >= 0.3f })
        assertTrue("pies $foot", foot.isNotEmpty() && foot.all { it < 0.3f })
    }

    /** El contorno del esquema de la mano sigue el borde real de la mano dibujada (no el eje de los dedos). */
    @Test
    fun handSchemaOutlineFollowsTheHand() {
        for (variant in listOf(mapOf<String, Float>(), mapOf("spread" to 16f), mapOf("rot" to 30f), mapOf("len" to 1.25f))) {
            val (b, hs) = handsScene(true, spread = variant["spread"] ?: 5f, rotDeg = variant["rot"] ?: 0f, lenK = variant["len"] ?: 1f)
            val c = classes(b)
            for (h in hs) {
                val o = ZoneCriteria.handOutline(c, h)
                assertTrue("sin contorno de la mano ($variant)", o != null && o.size >= 20)
                val p = h.points
                val palm = kotlin.math.hypot(p[0].x - p[9].x, p[0].y - p[9].y)
                // cada yema y cada nudillo, dentro del contorno o a menos de un dedo de su borde
                for (i in listOf(4, 8, 12, 16, 20, 5, 9, 13, 17)) {
                    val near = o!!.minOf { kotlin.math.hypot(it.x - p[i].x, it.y - p[i].y) }
                    assertTrue("punto $i fuera del contorno ($variant)", inside(o, p[i]) || near < palm * 0.15f)
                }
                // el contorno no se va de la mano: su caja apenas excede la de los puntos (más el grosor del dedo)
                val m = palm * 0.35f
                assertTrue("contorno más ancho que la mano ($variant): ${o!!.minOf { it.x } - p.minOf { it.x }} ${o.maxOf { it.x } - p.maxOf { it.x }} m=$m", o!!.minOf { it.x } > p.minOf { it.x } - m && o.maxOf { it.x } < p.maxOf { it.x } + m)
                assertTrue("contorno más largo que la mano ($variant)", o.maxOf { it.y } < p.maxOf { it.y } + m)
            }
        }
    }

    /** Dibujo girado 90° en sentido horario (paciente de lado en la cámara) con sus puntos. */
    private fun rotCw(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(90f) }, true)
    private fun rotCw(p: Pt, a: Float) = Pt((a - p.y) / a, p.x / a)

    /**
     * Guía girada 90° (botón «Girar 90°»): manos y pies girados igual en la cámara cumplen; sin girar la
     * guía, no (los dedos ya no van hacia abajo). El esquema vuelve a caer sobre la mano del cuadro.
     */
    @Test
    fun criteriaFollowTheGuideRotatedBy90() {
        val a = H / W.toFloat()
        for ((z, palm) in listOf(Zone.HANDS_PALMAR to true, Zone.HANDS_DORSAL to false)) {
            val (b, hs) = handsScene(palm)
            val rb = rotCw(b)
            val rhs = hs.map { ZoneCriteria.Hand(it.points.map { p -> rotCw(p, a) }, it.label) }
            val scene = ZoneCriteria.Scene(emptyMap(), rhs, classes(rb), rb.height / rb.width.toFloat())
            val inGuide = with(ZoneCriteria) { scene.rotatedCcw(1) }
            assertTrue("${z.id} girada con la guía: ${ZoneCriteria.hints(z, inGuide)}", ZoneCriteria.hints(z, inGuide).isEmpty())
            assertTrue("${z.id} girada sin girar la guía debería pedir los dedos hacia abajo", ZoneCriteria.hints(z, scene).any { "abajo" in it })
            // el esquema calculado en la orientación de la guía vuelve a las coordenadas del cuadro
            val sch = com.photomed.app.align.BodySchema.of(z, inGuide)!!
            val back = with(com.photomed.app.align.BodySchema) { sch.map { ZoneCriteria.unrotate(it, 1, scene.aspect) } }
            for (tip in rhs.flatMap { h -> listOf(4, 8, 12, 16, 20).map { h.points[it] } })
                assertTrue("yema fuera del esquema devuelto", back.dots.any { kotlin.math.hypot(it.x - tip.x, it.y - tip.y) < 0.01f })
        }
        for (z in listOf(Zone.FEET_DORSAL, Zone.FEET_PLANTAR)) {
            val rb = rotCw(feetScene(sole = z == Zone.FEET_PLANTAR))
            val scene = ZoneCriteria.Scene(emptyMap(), null, classes(rb), rb.height / rb.width.toFloat())
            val h = ZoneCriteria.hints(z, with(ZoneCriteria) { scene.rotatedCcw(1) })
            assertTrue("${z.id} girada con la guía: $h", h.isEmpty())
        }
        // ida y vuelta de un punto con cualquier número de giros
        for (q in 0..3) for (pt in listOf(Pt(0.1f, 0.2f), Pt(0.8f, 1.2f))) {
            val sc = with(ZoneCriteria) { ZoneCriteria.Scene(mapOf("x" to pt), null, null, a).rotatedCcw(q) }
            val r = ZoneCriteria.unrotate(sc.pose["x"]!!, q, a)
            assertTrue("giro $q: $pt → $r", kotlin.math.abs(r.x - pt.x) < 1e-4f && kotlin.math.abs(r.y - pt.y) < 1e-4f)
        }
    }

    /** Puntos de referencia adicionales de las zonas del cuerpo (manos, pies, bordes de miembros y tronco). */
    @Test
    fun bodyZonesHaveManyReferencePoints() {
        val bp = com.photomed.app.align.BodyPoints
        val (hb, hs) = handsScene(true)
        val hp = bp.of(Zone.HANDS_PALMAR, ZoneCriteria.Scene(emptyMap(), hs, classes(hb)))
        assertTrue("manos: ${hp.size} puntos", hp.size == 42)
        val fb = feetScene()
        val fp = bp.of(Zone.FEET_DORSAL, ZoneCriteria.Scene(emptyMap(), null, classes(fb)))
        assertTrue("pies: ${fp.keys}", fp.size >= 10)
        // la punta de cada pie, abajo (en los dedos)
        assertTrue("punta del pie: ${fp["pie_a_punta"]}", (fp["pie_a_punta"]?.y ?: 0f) > 0.95f)
        for ((z, min) in listOf(Zone.ARM_R to 8, Zone.LEGS to 18, Zone.TORSO_FRONT to 4)) {
            val c = case(z, "puntos", true, null) { figure(it, armAbd = 20f, top = if (z == Zone.LEGS) 10f else 40f, scale = if (z == Zone.LEGS) 0.92f else 1f) }
            val extra = bp.of(z, c.scene)
            assertTrue("${z.id}: ${extra.size} puntos de borde (${extra.keys})", extra.size >= min)
            // todos en el borde: dentro del cuadro y dentro de la clase cuerpo justo antes
            assertTrue("${z.id}: claves de la alineación", extra.keys.all { it in com.photomed.app.align.ZoneAlignment.of(z).landmarks })
        }
    }

    private fun inside(poly: List<Pt>, q: Pt): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    @Test
    fun drawnCasesFollowTheClinicalCriteria() {
        val cs = cases()
        val dir = File("build/reports/zone_criteria").apply { mkdirs() }
        val bad = ArrayList<String>()
        cs.groupBy { it.zone }.forEach { (z, list) ->
            val tw = 225; val th = 300 + 46; val cols = 4; val rows = (list.size + cols - 1) / cols
            val sheet = Bitmap.createBitmap(tw * cols, th * rows + 34, Bitmap.Config.ARGB_8888)
            val sc = Canvas(sheet); sc.drawColor(Color.WHITE)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
            sc.drawText("${z.id}: criterios con dibujos propios", 8f, 26f, tp)
            list.forEachIndexed { i, cs0 ->
                val hints = ZoneCriteria.hints(cs0.zone, cs0.scene)
                val accepted = hints.isEmpty()
                val right = accepted == cs0.ok && (cs0.ok || cs0.expect == null || hints.any { it.contains(cs0.expect, ignoreCase = true) })
                if (!right) bad += "${z.id} · ${cs0.name}: esperado ${if (cs0.ok) "aceptar" else "rechazar con «${cs0.expect}»"}, obtenido ${if (accepted) "aceptada" else hints}"
                val x = (i % cols) * tw; val y = 34 + (i / cols) * th
                sc.drawBitmap(Bitmap.createScaledBitmap(cs0.bmp, tw, 300, true), x.toFloat(), y.toFloat(), null)
                // esquema de la guía ajustado a lo detectado (blanco) y puntos (rosa)
                com.photomed.app.align.BodySchema.of(cs0.zone, cs0.scene)?.let { sch ->
                    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
                    fun X(p: Pt) = x + p.x * tw
                    fun Y(p: Pt) = y + p.y * tw
                    sch.polygons.forEach { poly -> val path = Path(); poly.forEachIndexed { k, q -> if (k == 0) path.moveTo(X(q), Y(q)) else path.lineTo(X(q), Y(q)) }; path.close(); sc.drawPath(path, sp) }
                    sch.capsules.forEach { cp ->
                        val dx = (cp.b.x - cp.a.x) * tw; val dy = (cp.b.y - cp.a.y) * tw; val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f)
                        val nx = -dy / len * cp.r * tw; val ny = dx / len * cp.r * tw
                        sc.drawLine(X(cp.a) + nx, Y(cp.a) + ny, X(cp.b) + nx, Y(cp.b) + ny, sp); sc.drawLine(X(cp.a) - nx, Y(cp.a) - ny, X(cp.b) - nx, Y(cp.b) - ny, sp)
                        sc.drawCircle(X(cp.b), Y(cp.b), cp.r * tw, sp)
                    }
                    sch.dots.forEach { q -> sc.drawCircle(X(q), Y(q), 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }) }
                    sch.lines.forEach { l -> l.zipWithNext { a, b -> sc.drawLine(X(a), Y(a), X(b), Y(b), sp) } }
                }
                // puntos de referencia adicionales (amarillo)
                val yel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 214, 0) }
                com.photomed.app.align.BodyPoints.of(cs0.zone, cs0.scene).values.forEach { q -> sc.drawCircle(x + q.x * tw, y + q.y * tw, 2.5f, yel) }
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 64, 129) }
                cs0.scene.pose.values.forEach { p -> sc.drawCircle(x + p.x * tw, y + p.y * tw, 3.5f, dot) }
                cs0.scene.hands?.forEach { h -> h.points.forEach { p -> sc.drawCircle(x + p.x * tw, y + p.y * tw, 2.5f, dot) } }
                val col = if (!right) Color.rgb(255, 160, 0) else if (accepted) Color.rgb(46, 125, 50) else Color.rgb(183, 28, 28)
                sc.drawRect(x.toFloat(), (y + 300).toFloat(), (x + tw).toFloat(), (y + th).toFloat(), Paint().apply { color = col })
                val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f }
                sc.drawText(cs0.name.take(30), x + 5f, y + 318f, wp)
                sc.drawText((if (accepted) "Dispara" else hints.first()).take(32), x + 5f, y + 338f, wp)
            }
            File(dir, "${z.id}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        println(bad.joinToString("\n", "FALLOS:\n"))
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
