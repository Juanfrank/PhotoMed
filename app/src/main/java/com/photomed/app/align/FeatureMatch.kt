package com.photomed.app.align

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Validación de la coincidencia por rasgo entre dos fotos alineadas: para ojos, cejas, nariz, labios
 * y contorno, el error medio (RMS) de sus puntos tras aplicar la alineación, relativo a la distancia
 * interpupilar. Usa la malla completa de MediaPipe si está en ambas fotos (claves "m<i>"); si no, los
 * contornos comunes a ML Kit. Coincidencia = 1 − error / [TOL] (0 % a partir de un error de [TOL]).
 * Un rasgo bajo con los demás altos indica un cambio local (expresión, edema, tratamiento) o una
 * pose distinta, no un error de encuadre.
 */
object FeatureMatch {
    const val TOL = 0.10f
    /** Por debajo de esto se avisa en Comparar. */
    const val WARN = 0.6f

    private val GROUPS: List<Pair<String, (String) -> Boolean>> = listOf(
        "ojos" to { k -> Landmarks.meshRegion(k) == "ojos" || k.startsWith("eye_") },
        "cejas" to { k -> Landmarks.meshRegion(k) == "cejas" || k.startsWith("brow_") },
        "nariz" to { k -> Landmarks.meshRegion(k) == "nariz" || k.startsWith("nose_") },
        "labios" to { k -> Landmarks.meshRegion(k) == "labios" || k.startsWith("lip_") },
        "contorno" to { k -> k.startsWith("oval_") },
    )

    fun scores(before: Map<String, Pt>, after: Map<String, Pt>, t: Similarity): List<Pair<String, Float>> {
        val el = before[Landmarks.EYE_L]; val er = before[Landmarks.EYE_R]
        val ipd = if (el != null && er != null) hypot(el.x - er.x, el.y - er.y) else return emptyList()
        if (ipd < 1e-4f) return emptyList()
        val useMesh = before.keys.any { it.startsWith("m") && Landmarks.meshRegion(it) != null } &&
            after.keys.any { it.startsWith("m") && Landmarks.meshRegion(it) != null }
        return GROUPS.mapNotNull { (name, inGroup) ->
            val keys = before.keys.filter { k -> k in after && inGroup(k) && (!useMesh || name == "contorno" || k.startsWith("m")) }
            if (keys.size < 3) return@mapNotNull null
            val ms = keys.map { k -> val q = t.apply(after[k]!!); val p = before[k]!!; (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y) }.average()
            val err = sqrt(ms).toFloat() / ipd
            name to (1f - err / TOL).coerceIn(0f, 1f)
        }
    }
}
