package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.json.JSONObject

/**
 * Edición no destructiva de una foto: giro en pasos de 90° ([rot] = 0..3, sentido horario),
 * espejo horizontal y recorte ([crop]: x0, y0, x1, y1 normalizados sobre la imagen ya girada y
 * espejada). El archivo original no se modifica: la edición se guarda aparte ([PhotoEdits]) y se
 * aplica al cargar la foto en cualquier pantalla (visor, alineación, comparar, exportar).
 */
data class ImageEdit(val rot: Int = 0, val mirror: Boolean = false, val crop: FloatArray? = null) {
    val isIdentity: Boolean get() = rot % 4 == 0 && !mirror && crop == null

    fun rotated() = copy(rot = (rot + 1) % 4, crop = null)
    fun mirrored() = copy(mirror = !mirror, crop = crop?.let { floatArrayOf(1f - it[2], it[1], 1f - it[0], it[3]) })

    /** Aplica giro → espejo → recorte. */
    fun apply(src: Bitmap): Bitmap {
        var b = src
        if (rot % 4 != 0 || mirror) {
            val m = Matrix()
            if (rot % 4 != 0) m.postRotate(90f * (rot % 4))
            if (mirror) m.postScale(-1f, 1f)
            b = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        val c = crop ?: return b
        val x0 = (c[0] * b.width).toInt().coerceIn(0, b.width - 2); val y0 = (c[1] * b.height).toInt().coerceIn(0, b.height - 2)
        val x1 = (c[2] * b.width).toInt().coerceIn(x0 + 2, b.width); val y1 = (c[3] * b.height).toInt().coerceIn(y0 + 2, b.height)
        return Bitmap.createBitmap(b, x0, y0, x1 - x0, y1 - y0)
    }

    fun toJson(): JSONObject = JSONObject().put("rot", rot).put("mirror", mirror).apply {
        crop?.let { c -> put("crop", org.json.JSONArray(c.map { it.toDouble() })) }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ImageEdit || rot != other.rot || mirror != other.mirror) return false
        val a = crop; val b = other.crop
        return if (a == null || b == null) a == b else a.contentEquals(b)
    }
    override fun hashCode() = rot * 31 + mirror.hashCode() * 7 + (crop?.contentHashCode() ?: 0)

    companion object {
        val NONE = ImageEdit()
        fun fromJson(o: JSONObject) = ImageEdit(o.optInt("rot", 0), o.optBoolean("mirror", false),
            o.optJSONArray("crop")?.let { a -> FloatArray(4) { a.getDouble(it).toFloat() } })
    }
}

/** Ediciones guardadas por foto (clave: URI de la foto), en las preferencias privadas de la app. */
object PhotoEdits {
    private const val PREFS = "photo_edits"
    @Volatile private var cache: MutableMap<String, ImageEdit>? = null

    private fun all(context: Context): MutableMap<String, ImageEdit> = cache ?: synchronized(this) {
        cache ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            runCatching { k to ImageEdit.fromJson(JSONObject(v as String)) }.getOrNull()
        }.toMap().toMutableMap().also { cache = it }
    }

    fun of(context: Context, key: String): ImageEdit = all(context)[key] ?: ImageEdit.NONE

    fun set(context: Context, key: String, edit: ImageEdit) {
        val m = all(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (edit.isIdentity) { m.remove(key); p.remove(key) } else { m[key] = edit; p.putString(key, edit.toJson().toString()) }
        p.apply()
    }

    /** Solo para pruebas. */
    fun clearCache() { cache = null }
}
