package com.photomed.app.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Internacionalización. El texto de origen (español) queda en el código dentro de [tr]; el script
 * `tools/i18n/extract.py` lo extrae a recursos Android estándar (`values/strings_i18n.xml`, español, y
 * `values-<idioma>/strings_i18n.xml` con las traducciones de `tools/i18n/<idioma>/*.json`). Así Android
 * elige el idioma (también el idioma por aplicación de Android 13+) y añadir otro idioma es añadir su
 * catálogo. Sin contexto (pruebas JVM) o sin traducción, se usa el texto de origen.
 */
object I18n {
    @Volatile private var context: Context? = null
    private val ids = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Contexto con el idioma de la interfaz (se llama al crear la actividad). */
    fun init(c: Context) { context = c; ids.clear() }

    /** Nombre del recurso de un texto de origen: `t_` + FNV-1a de 64 bits en hexadecimal (12 cifras). */
    fun key(source: String): String {
        var h = -0x340d631b7bdddcdbL          // 0xcbf29ce484222325
        for (b in source.toByteArray(Charsets.UTF_8)) { h = h xor (b.toLong() and 0xFF); h *= 0x100000001b3L }
        return "t_" + java.lang.Long.toHexString(h).padStart(16, '0').takeLast(12)
    }

    fun lookup(source: String): String {
        val c = context ?: return source
        val id = ids.getOrPut(source) { c.resources.getIdentifier(key(source), "string", c.packageName) }
        return if (id == 0) source else runCatching { c.getString(id) }.getOrDefault(source)
    }

    /** Idioma de la interfaz (para formatear números). */
    val locale: Locale get() = context?.resources?.configuration?.let {
        if (Build.VERSION.SDK_INT >= 24) it.locales[0] else @Suppress("DEPRECATION") it.locale } ?: Locale.getDefault()
}

/**
 * Texto traducible: [source] en español (con `%1$s`, `%2$d`… si lleva datos, que se rellenan con
 * [args]). Devuelve la traducción al idioma de la interfaz.
 */
fun tr(source: String, vararg args: Any?): String {
    val s = I18n.lookup(source)
    return if (args.isEmpty()) s else runCatching { String.format(I18n.locale, s, *args) }.getOrElse { String.format(Locale.ROOT, source, *args) }
}

/**
 * Marca un texto para traducirlo más tarde sin traducirlo ahora (etiquetas de enum y constantes:
 * se guardan en español y se muestran con [tr]).
 */
fun trn(source: String): String = source

/** Idioma elegido en Ajustes: el del sistema, español o inglés. */
enum class AppLanguage(val tag: String?, val label: String) {
    SYSTEM(null, trn("Según el sistema")), ES("es", "Español"), EN("en", "English");

    companion object {
        const val PREF = "app_language"

        fun current(context: Context): AppLanguage = runCatching {
            valueOf(context.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString(PREF, SYSTEM.name)!!)
        }.getOrDefault(SYSTEM)

        /**
         * Cambia el idioma. Android 13+: idioma por aplicación del sistema (recrea la actividad);
         * antes, se guarda y se aplica al recrear ([wrap]).
         */
        fun set(activity: android.app.Activity, lang: AppLanguage) {
            activity.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString(PREF, lang.name).commit()
            if (Build.VERSION.SDK_INT >= 33) {
                activity.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                    if (lang.tag == null) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(lang.tag)
            } else activity.recreate()
        }

        /** Antes de Android 13: contexto con el idioma elegido (en attachBaseContext). */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= 33) return base
            val tag = current(base).tag ?: return base
            val loc = Locale.forLanguageTag(tag)
            Locale.setDefault(loc)
            val conf = Configuration(base.resources.configuration).apply { setLocale(loc) }
            return base.createConfigurationContext(conf)
        }
    }
}
