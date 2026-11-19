package com.photomed.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Plantilla de sesión: zonas a fotografiar en orden (una secuencia guiada en la cámara). */
data class SessionTemplate(val name: String, val zones: List<Zone>, val builtIn: Boolean = false)

/** Plantillas predefinidas y las guardadas por el usuario (preferencias, JSON). */
object SessionTemplates {
    private const val KEY = "session_templates"

    val BUILT_IN = listOf(
        SessionTemplate("Rostro 5 vistas", listOf(Zone.FACE_FRONTAL, Zone.FACE_OBLIQUE_R, Zone.FACE_OBLIQUE_L, Zone.FACE_PROFILE_R, Zone.FACE_PROFILE_L), true),
        SessionTemplate("Rostro 3 vistas", listOf(Zone.FACE_FRONTAL, Zone.FACE_OBLIQUE_R, Zone.FACE_OBLIQUE_L), true),
        SessionTemplate("Tercios faciales", listOf(Zone.FOREHEAD, Zone.PERIOCULAR, Zone.LIPS), true),
        SessionTemplate("Tronco", listOf(Zone.TORSO_FRONT, Zone.TORSO_SIDE, Zone.TORSO_SIDE_L, Zone.BACK), true),
        SessionTemplate("Manos y pies", listOf(Zone.HANDS_DORSAL, Zone.HANDS_PALMAR, Zone.FEET_DORSAL, Zone.FEET_PLANTAR), true),
    )

    fun load(prefs: SharedPreferences): List<SessionTemplate> = BUILT_IN + custom(prefs)

    fun custom(prefs: SharedPreferences): List<SessionTemplate> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val z = o.getJSONArray("zones").let { arr -> (0 until arr.length()).mapNotNull { Zone.fromId(arr.getString(it)) } }
            if (z.isEmpty()) null else SessionTemplate(o.getString("name"), z)
        }
    }.getOrDefault(emptyList())

    fun save(prefs: SharedPreferences, t: SessionTemplate) {
        val list = custom(prefs).filter { it.name != t.name } + t
        write(prefs, list)
    }

    fun delete(prefs: SharedPreferences, name: String) = write(prefs, custom(prefs).filter { it.name != name })

    private fun write(prefs: SharedPreferences, list: List<SessionTemplate>) {
        val a = JSONArray()
        list.forEach { t -> a.put(JSONObject().put("name", t.name).put("zones", JSONArray(t.zones.map { it.id }))) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }
}
