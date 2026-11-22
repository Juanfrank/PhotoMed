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
        val s = Bitmap.createScaledBitmap(b, 96, 128, false)
        val px = IntArray(96 * 128).also { s.getPixels(it, 0, 96, 0, 0, 96, 128) }
        val pal = listOf(BG to FaceLabels.BG, SKIN to FaceLabels.BODY_SKIN, NAIL to FaceLabels.BODY_SKIN, FACE to FaceLabels.FACE_SKIN, CLOTH to FaceLabels.CLOTHES)
        val out = ByteArray(px.size) { i ->
            val c = px[i]
            pal.minBy { (p, _) -> Math.abs(Color.red(p) - Color.red(c)) + Math.abs(Color.green(p) - Color.green(c)) + Math.abs(Color.blue(p) - Color.blue(c)) }.second.toByte()
        }
        return ZoneCriteria.Classes(96, 128, out)
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
    private fun hand(c: Canvas, wx: Float, wy: Float, thumbSide: Float, bent: Set<Int> = emptySet(), up: Boolean = false, nails: Boolean = false): List<Pt> {
        val dir = if (up) -1f else 1f
        val pts = ArrayList<Pt>(21)
        // muñeca y palma redondeada, algo más ancha en los nudillos
        c.drawRect(wx - 22, wy - dir * 30, wx + 22, wy, fill(SKIN))
        val palm = Path().apply {
            moveTo(wx - 26, wy); lineTo(wx + 26, wy); lineTo(wx + 33, wy + dir * 66); quadTo(wx, wy + dir * 76, wx - 33, wy + dir * 66); close()
        }
        c.drawPath(palm, fill(SKIN))
        pts += u(wx, wy)
        // pulgar: nace en el lateral de la palma (lado [thumbSide]) y va hacia fuera y hacia los dedos
        val tb = Pt(wx + thumbSide * 22, wy + dir * 22)
        val thumbBent = 0 in bent
        val tj = (0..3).map { i ->
            if (thumbBent && i >= 2) Pt(tb.x + thumbSide * 13f * 1 - thumbSide * 9f * (i - 1), tb.y + dir * (15f * 1 + 12f * (i - 1)))
            else Pt(tb.x + thumbSide * 13f * i, tb.y + dir * 15f * i)
        }
        val tp = stroke(SKIN, 17f); for (i in 0 until 3) c.drawLine(tj[i].x, tj[i].y, tj[i + 1].x, tj[i + 1].y, tp)
        if (nails && !thumbBent) c.drawCircle(tj[3].x, tj[3].y, 4.5f, fill(NAIL))
        pts += tj.map { u(it.x, it.y) }
        // cuatro dedos, ligeramente abiertos en abanico, del lado del pulgar al meñique
        for (f in 1..4) {
            val off = thumbSide * (21f - (f - 1) * 14f)
            val spread = Math.toRadians((thumbSide * (f - 2.5f) * -5f).toDouble())
            val base = Pt(wx + off, wy + dir * 68)
            val seg = if (f == 2) 21f else if (f == 4) 15f else 19f
            val ux = sin(spread).toFloat(); val uy = cos(spread).toFloat()
            val js = (0..3).map { j -> Pt(base.x + ux * seg * j, base.y + dir * uy * seg * j) }.toMutableList()
            if (f in bent) { js[2] = Pt(base.x - thumbSide * 3, base.y + dir * seg * 1.1f); js[3] = Pt(base.x - thumbSide * 2, base.y + dir * seg * 0.4f) }
            val fp = stroke(SKIN, 13f); for (j in 0 until 3) c.drawLine(js[j].x, js[j].y, js[j + 1].x, js[j + 1].y, fp)
            if (nails && f !in bent) c.drawCircle(js[3].x, js[3].y - dir * 2, 4f, fill(NAIL))
            pts += js.map { u(it.x, it.y) }
        }
        return pts
    }

    /** Dos manos lado a lado; [palm]: palmas a la cámara (pulgares hacia fuera) o dorsos (pulgares hacia dentro). */
    private fun handsScene(palm: Boolean, bent: Set<Int> = emptySet(), oneHand: Boolean = false, shiftY: Float = 0f, up: Boolean = false): Pair<Bitmap, List<ZoneCriteria.Hand>> {
        val (b, c) = canvas()
        val wy = (if (up) 300f else 110f) + shiftY
        // mano derecha del paciente a la izquierda de la imagen; MediaPipe la etiqueta «Left» (supone espejo)
        val leftImgThumb = if (palm) -1f else 1f
        val hs = ArrayList<ZoneCriteria.Hand>()
        hs += ZoneCriteria.Hand(hand(c, 80f, wy, leftImgThumb, bent, up, nails = !palm), "Left")
        if (!oneHand) hs += ZoneCriteria.Hand(hand(c, 220f, wy, -leftImgThumb, emptySet(), up, nails = !palm), "Right")
        return b to hs
    }

    // ---------- pies ----------
    /** Dos pies vistos desde arriba (dorso) o la planta ([sole]), dedo gordo hacia dentro. */
    private fun feetScene(cut: Boolean = false, one: Boolean = false, sole: Boolean = false, strap: Boolean = false): Bitmap {
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
            val rad = floatArrayOf(13f, 9.5f, 9f, 8f, 7f)
            for (i in 0..4) {
                val tx = x + inward * (28f - i * 15f); val ty = 304f + dy + (if (i == 0) 4f else i * -2f)
                c.drawCircle(tx, ty, rad[i], fill(SKIN))
                if (!sole) c.drawCircle(tx, ty + rad[i] * 0.35f, rad[i] * 0.45f, fill(NAIL))
            }
            if (sole) {
                // almohadillas del talón y del antepié, arco plantar
                c.drawOval(x - 24, 105f + dy, x + 24, 165f + dy, stroke(shade, 3f))
                c.drawOval(x - 34, 245f + dy, x + 34, 290f + dy, stroke(shade, 3f))
                c.drawLine(x + inward * 16, 170f + dy, x + inward * 22, 240f + dy, stroke(shade, 3f))
            }
        }
        // pie derecho del paciente a la izquierda de la imagen: dedo gordo hacia dentro (+1)
        foot(90f, 1f); if (!one) foot(210f, -1f)
        // tira de sandalia que cruza el pie (lo parte en dos manchas de piel)
        if (strap) c.drawRect(0f, 200f + dy, W.toFloat(), 228f + dy, fill(CLOTH))
        return b
    }

    // ---------- casos ----------
    private fun case(z: Zone, name: String, ok: Boolean, expect: String?, draw: (Canvas) -> Map<String, Pt>): Case {
        val (b, c) = canvas(); val p = draw(c)
        return Case(z, name, ok, expect, b, ZoneCriteria.Scene(p, null, classes(b)))
    }

    private fun cases(): List<Case> {
        val out = ArrayList<Case>()
        // manos
        for ((z, palm) in listOf(Zone.HANDS_PALMAR to true, Zone.HANDS_DORSAL to false)) {
            fun hc(name: String, ok: Boolean, expect: String?, r: Pair<Bitmap, List<ZoneCriteria.Hand>>) = out.add(Case(z, name, ok, expect, r.first, ZoneCriteria.Scene(emptyMap(), r.second, classes(r.first))))
            hc("correcta", true, null, handsScene(palm))
            hc("anular doblado", false, "extienda los cinco dedos", handsScene(palm, bent = setOf(3)))
            hc("pulgar doblado", false, "pulgar", handsScene(palm, bent = setOf(0)))
            hc("una sola mano", false, "dos manos", handsScene(palm, oneHand = true))
            hc("dedos fuera del cuadro", false, "entera dentro del cuadro", handsScene(palm, shiftY = 215f))
            hc("dedos hacia arriba", false, "dedos hacia abajo", handsScene(palm, up = true))
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
        }
        // abdomen frontal, tórax, espalda
        out += case(Zone.TORSO_FRONT, "correcta", true, null) { figure(it) }
        out += case(Zone.TORSO_FRONT, "con camiseta", false, "Descubra", { figure(it, shirt = true) })
        out += case(Zone.TORSO_FRONT, "brazos sobre el abdomen", false, "Separe los brazos", { figure(it, armsCover = true) })
        out += case(Zone.TORSO_FRONT, "hombros inclinados", false, "Hombros nivelados", { figure(it, tiltDeg = 9f) })
        out += case(Zone.TORSO_FRONT, "de espaldas", false, "De frente", { figure(it, back = true) })
        out += case(Zone.BREASTS, "correcta", true, null) { figure(it) }
        out += case(Zone.BREASTS, "con camiseta", false, "Descubra", { figure(it, shirt = true) })
        out += case(Zone.BACK, "correcta", true, null) { figure(it, back = true) }
        out += case(Zone.BACK, "con camiseta", false, "Descubra", { figure(it, back = true, shirt = true) })
        out += case(Zone.BACK, "de frente", false, "De espaldas", { figure(it) })
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
            out += case(z, "brazos tapando el flanco", false, "brazos", { side(it, armsDown = true) })
            out += case(z, "con camiseta", false, "Descubra", { side(it, shirt = true) })
            out += case(z, "de frente", false, "de lado", { side(it, frontal = true) })
        }
        // glúteos
        out += case(Zone.GLUTES, "correcta", true, null) { figure(it, back = true, shorts = false) }
        out += case(Zone.GLUTES, "con pantalón", false, "Descubra", { figure(it, back = true, pants = true) })
        out += case(Zone.GLUTES, "de frente", false, "De espaldas", { figure(it, shorts = false) })
        // piernas
        out += case(Zone.LEGS, "correcta", true, null) { figure(it, top = 10f, scale = 0.92f) }
        out += case(Zone.LEGS, "rodilla flexionada", false, "Rodillas", { figure(it, top = 10f, scale = 0.92f, kneeBend = 35f) })
        out += case(Zone.LEGS, "con pantalón", false, "Descubra", { figure(it, top = 10f, scale = 0.92f, pants = true) })
        out += case(Zone.LEGS, "pies juntos", false, "Separe un poco los pies", { figure(it, top = 10f, scale = 0.92f, feetGap = 0f) })
        out += case(Zone.LEGS, "pies fuera del cuadro", false, "Los pies dentro", { figure(it, top = 50f, scale = 0.98f) })
        // brazos
        for (z in listOf(Zone.ARM_R, Zone.ARM_L)) {
            out += case(z, "correcta (≈ 20°)", true, null) { figure(it, armAbd = 20f) }
            out += case(z, "codo doblado", false, "Codo extendido", { figure(it, armAbd = 20f, elbowBend = 50f) })
            out += case(z, "brazo pegado al cuerpo", false, "Separe un poco el brazo", { figure(it, armAbd = 3f) })
            out += case(z, "brazo a 60°", false, "Baje el brazo", { figure(it, armAbd = 60f) })
            out += case(z, "con manga", false, "Descubra", { figure(it, armAbd = 20f, sleeve = true) })
        }
        // cuello
        out += case(Zone.NECK, "correcta", true, null) { figure(it, top = 60f, scale = 1.6f) }
        out += case(Zone.NECK, "hombros inclinados", false, "Hombros nivelados", { figure(it, top = 60f, scale = 1.6f, tiltDeg = 9f) })
        out += case(Zone.NECK, "con jersey de cuello alto", false, "Descubra", { c -> figure(c, top = 60f, scale = 1.6f).also { c.drawRect(120f, 120f, 180f, 200f, fill(CLOTH)); c.drawRect(60f, 170f, 240f, 260f, fill(CLOTH)) } })
        return out
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
                // dibujo de los puntos
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
