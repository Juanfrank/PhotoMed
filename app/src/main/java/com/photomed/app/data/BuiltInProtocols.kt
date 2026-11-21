package com.photomed.app.data

import android.content.Context
import com.photomed.app.align.LiveMatch
import org.json.JSONArray

/**
 * Protocolos predefinidos (las antiguas plantillas de sesión: todo es un protocolo). Se crean una sola
 * vez en la biblioteca, editables y eliminables como cualquier otro; las plantillas propias guardadas
 * por versiones anteriores se convierten en protocolos.
 */
object BuiltInProtocols {
    private const val SEEDED = "protocols_builtin_v1"
    private const val OLD_TEMPLATES = "session_templates"

    val ALL: List<Pair<String, List<Zone>>> = listOf(
        "Rostro 5 vistas" to listOf(Zone.FACE_FRONTAL, Zone.FACE_OBLIQUE_R, Zone.FACE_OBLIQUE_L, Zone.FACE_PROFILE_R, Zone.FACE_PROFILE_L),
        "Rostro 3 vistas" to listOf(Zone.FACE_FRONTAL, Zone.FACE_OBLIQUE_R, Zone.FACE_OBLIQUE_L),
        "Tercios faciales" to listOf(Zone.FOREHEAD, Zone.PERIOCULAR, Zone.LIPS),
        "Tricología" to listOf(Zone.SCALP_VERTEX, Zone.SCALP_CROWN, Zone.SCALP_PARIETAL_R, Zone.SCALP_PARIETAL_L, Zone.FOREHEAD),
        "Ojos" to listOf(Zone.EYE_R, Zone.EYE_L, Zone.PERIOCULAR),
        "Tronco" to listOf(Zone.TORSO_FRONT, Zone.TORSO_SIDE, Zone.TORSO_SIDE_L, Zone.BACK),
        "Manos y pies" to listOf(Zone.HANDS_DORSAL, Zone.HANDS_PALMAR, Zone.FEET_DORSAL, Zone.FEET_PLANTAR),
    )

    /** Protocolo de [zones] en orden, una foto por zona, con la coincidencia, la luz y el disparo por defecto. */
    fun of(name: String, zones: List<Zone>, id: String? = null): Protocol {
        val steps = zones.map { ProtocolDefaults.step(it, threshold = LiveMatch.DEFAULT_THRESHOLD) }
        return if (id != null) Protocol(id = id, name = name, steps = steps) else Protocol(name = name, steps = steps)
    }

    /** Crea los predefinidos y migra las plantillas propias (una sola vez por instalación). */
    fun seed(context: Context) {
        val prefs = context.getSharedPreferences("photomed", Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED, false)) return
        ALL.forEachIndexed { i, (name, zones) ->
            val id = "predefinido-${i + 1}"
            if (Protocols.get(context, id) == null) Protocols.save(context, of(name, zones, id))
        }
        migrate(context, prefs.getString(OLD_TEMPLATES, null))
        prefs.edit().putBoolean(SEEDED, true).remove(OLD_TEMPLATES).apply()
    }

    /** Plantillas propias antiguas (JSON: nombre y zonas) → protocolos. */
    fun migrate(context: Context, json: String?): Int {
        if (json.isNullOrBlank()) return 0
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return 0
        val existing = Protocols.list(context).map { it.name }.toSet()
        var n = 0
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val zones = o.optJSONArray("zones")?.let { arr -> (0 until arr.length()).mapNotNull { Zone.fromId(arr.optString(it)) } }.orEmpty()
            val name = o.optString("name")
            if (name.isBlank() || zones.isEmpty() || name in existing) continue
            Protocols.save(context, of(name, zones)); n++
        }
        return n
    }
}
