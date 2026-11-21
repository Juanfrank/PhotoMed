package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Expresión facial a partir de los puntos (igual con ML Kit y MediaPipe), para que la comparación
 * clínica sea con la misma expresión: una sonrisa, unas cejas elevadas o los ojos entrecerrados
 * cambian pliegues, arrugas y volúmenes tanto como el tratamiento.
 *
 * Rasgos (normalizados, invariantes a escala y giro):
 * - [eyes]: apertura del ojo más cerrado (alto medio de los párpados / ancho del ojo);
 * - [mouthOpen]: separación de los labios (bermellón interno) / ancho de la boca;
 * - [mouthWidth]: ancho de la boca / distancia interpupilar (la sonrisa la ensancha);
 * - [smileLift]: elevación de las comisuras sobre el centro de la boca / interpupilar;
 * - [brows]: altura de las cejas sobre los ojos / interpupilar.
 * Calibrado con las expresiones de MediaPipe en retratos de stock (`tools/eval/expression.py`):
 * r = 0,89 (sonrisa), 0,83 (boca abierta), −0,93 (ojos cerrados), 0,75 (cejas elevadas).
 */
data class Expression(val eyes: Float, val mouthOpen: Float, val mouthWidth: Float, val smileLift: Float, val brows: Float) {
    companion object {
        // Diferencias con la referencia a partir de las que se avisa
        const val D_WIDTH = 0.07f
        const val D_LIFT = 0.05f
        const val D_OPEN = 0.08f
        const val D_BROWS = 0.05f
        const val EYES_RATIO = 0.7f
        // Sin referencia (expresión neutra): límites absolutos
        const val EYES_MIN = 0.13f
        const val OPEN_MAX = 0.15f
        const val WIDTH_MAX = 0.97f
        const val LIFT_MAX = 0.10f
        const val BROWS_MAX = 0.27f

        fun measure(points: Map<String, Pt>): Expression? {
            val p = FaceKeys.canonical(points)
            val el = p[Landmarks.EYE_L] ?: return null; val er = p[Landmarks.EYE_R] ?: return null
            val ipd = dist(el, er).takeIf { it > 1e-4f } ?: return null
            // eje vertical del rostro: perpendicular a la línea de los ojos (hacia arriba)
            val ex = Pt((el.x - er.x) / ipd, (el.y - er.y) / ipd)
            val up = Pt(ex.y, -ex.x).let { if (it.y > 0) Pt(-it.x, -it.y) else it }
            fun dot(a: Pt, b: Pt) = a.x * b.x + a.y * b.y
            fun g(prefix: String, n: Int): List<Pt>? = (0 until n).map { p["$prefix$it"] ?: return null }
            val eyes = listOf("eye_l_", "eye_r_").map { k ->
                val e = g(k, 16) ?: return null
                val w = dist(e[0], e[8]).takeIf { it > 1e-4f } ?: return null
                (2..6).map { dist(e[it], e[16 - it]) }.average().toFloat() / w
            }.min()
            val ub = g("lip_upper_bottom_", 9) ?: return null; val lt = g("lip_lower_top_", 9) ?: return null
            val cl = p["lip_upper_top_0"] ?: return null; val cr = p["lip_upper_top_10"] ?: return null
            val mw = dist(cl, cr).takeIf { it > 1e-4f } ?: return null
            val centre = Pt((ub[4].x + lt[4].x) / 2, (ub[4].y + lt[4].y) / 2)
            val corners = Pt((cl.x + cr.x) / 2 - centre.x, (cl.y + cr.y) / 2 - centre.y)
            val brows = listOf("brow_l_bottom_" to "eye_l_", "brow_r_bottom_" to "eye_r_").map { (b, e) ->
                val bb = (g(b, 5) ?: return null).let { l -> Pt(l.map { it.x }.average().toFloat(), l.map { it.y }.average().toFloat()) }
                val ee = (g(e, 16) ?: return null).subList(1, 8).let { l -> Pt(l.map { it.x }.average().toFloat(), l.map { it.y }.average().toFloat()) }
                dot(Pt(bb.x - ee.x, bb.y - ee.y), up) / ipd
            }.average().toFloat()
            return Expression(eyes, dist(ub[4], lt[4]) / mw, mw / ipd, dot(corners, up) / ipd, brows)
        }

        private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

        /** Indicaciones para igualar la expresión de la referencia (vacía si coincide). */
        fun compare(ref: Expression, cur: Expression): List<String> = buildList {
            if (cur.eyes < ref.eyes * EYES_RATIO) add("Expresión: abrir los ojos")
            else if (ref.eyes < cur.eyes * EYES_RATIO) add("Expresión: entrecerrar los ojos como en la referencia")
            val dw = cur.mouthWidth - ref.mouthWidth; val dl = cur.smileLift - ref.smileLift
            if (dw > D_WIDTH || dl > D_LIFT) add("Expresión: relajar la sonrisa")
            else if (dw < -D_WIDTH || dl < -D_LIFT) add("Expresión: sonreír como en la referencia")
            val dOpen = cur.mouthOpen - ref.mouthOpen
            if (dOpen > D_OPEN) add("Expresión: juntar los labios")
            else if (dOpen < -D_OPEN) add("Expresión: separar los labios como en la referencia")
            val db = cur.brows - ref.brows
            if (db > D_BROWS) add("Expresión: relajar las cejas")
            else if (db < -D_BROWS) add("Expresión: subir las cejas como en la referencia")
        }

        /** Sin referencia: la expresión neutra de las vistas faciales completas (y nunca ojos cerrados). */
        fun neutral(zone: Zone, cur: Expression): List<String> = buildList {
            if (cur.eyes < EYES_MIN) add("Expresión: abrir los ojos")
            if (zone.guide != GuideType.FACE_FRONT && zone.guide != GuideType.FACE_OBLIQUE) return@buildList
            if (cur.mouthWidth > WIDTH_MAX || cur.smileLift > LIFT_MAX) add("Expresión: relajar la sonrisa")
            else if (cur.mouthOpen > OPEN_MAX) add("Expresión: juntar los labios")
            if (cur.brows > BROWS_MAX) add("Expresión: relajar las cejas")
        }

        /**
         * Con una expresión pedida (arrugas dinámicas): si los puntos permiten medirla, si se alcanza
         * (cejas arriba, sonrisa máxima, ojos apretados, labios fruncidos); el resto de gestos (ceño,
         * nariz, mentón, cuello) no se miden con fiabilidad con los puntos y no se pide nada.
         */
        fun target(e: com.photomed.app.data.FacialExpression, cur: Expression): List<String> = buildList {
            when (e) {
                com.photomed.app.data.FacialExpression.SURPRISE -> if (cur.brows < BROWS_MAX) add("Expresión: subir más las cejas")
                com.photomed.app.data.FacialExpression.SMILE -> if (cur.mouthWidth < WIDTH_MAX && cur.smileLift < LIFT_MAX) add("Expresión: sonreír al máximo")
                com.photomed.app.data.FacialExpression.SQUINT -> if (cur.eyes > EYES_MIN) add("Expresión: apretar los ojos")
                com.photomed.app.data.FacialExpression.PUCKER -> if (cur.mouthWidth > PUCKER_MAX) add("Expresión: fruncir más los labios")
                com.photomed.app.data.FacialExpression.FROWN -> if (cur.brows > FROWN_MAX) add("Expresión: fruncir más el ceño (bajar las cejas)")
                com.photomed.app.data.FacialExpression.SAD -> if (cur.smileLift > SAD_MAX) add("Expresión: bajar las comisuras de la boca")
                else -> {}
            }
            // salvo con los ojos apretados, los ojos abiertos
            if (e != com.photomed.app.data.FacialExpression.SQUINT && cur.eyes < EYES_MIN * 0.6f) add("Expresión: abrir los ojos")
        }
        /** Labios fruncidos: la boca se estrecha claramente (ancho / interpupilar). */
        const val PUCKER_MAX = 0.62f
        /** Ceño fruncido: las cejas bajan hacia los ojos (distancia ceja–ojo / interpupilar; en reposo 0,145–0,22). */
        const val FROWN_MAX = 0.14f
        /** Tristeza: las comisuras claramente por debajo del centro de la boca (en reposo quedan en ±0,01). */
        const val SAD_MAX = -0.02f

        /**
         * ¿Se cumple la expresión pedida? (reposo: siempre). null si no se puede medir (cara girada): en
         * ese caso no se da por cumplida.
         */
        fun meets(e: com.photomed.app.data.FacialExpression, points: Map<String, Pt>, yaw: Float?): Boolean {
            if (e == com.photomed.app.data.FacialExpression.NEUTRAL || e == com.photomed.app.data.FacialExpression.NECK) return true
            if (!usable(yaw)) return false
            val cur = measure(points) ?: return false
            return target(e, cur).none { it.startsWith("Expresión") && "abrir los ojos" !in it }
        }

        /** Solo se evalúa casi de frente (en perfil los rasgos del lado lejano no son fiables). */
        fun usable(yaw: Float?) = yaw == null || abs(yaw) < 30f
    }
}
