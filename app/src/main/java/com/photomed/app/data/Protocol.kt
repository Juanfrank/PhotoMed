package com.photomed.app.data

import com.photomed.app.i18n.tr
import com.photomed.app.align.isFaceZone
import com.photomed.app.align.Pt
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Protocolo fotográfico: lista ordenada de tomas que un profesional define, comparte (archivo JSON) y
 * otro ejecuta en la cámara, paso a paso, con las mismas exigencias.
 *
 * Un paso puede llevar una **referencia sin imagen** ([ProtocolRef]): los puntos anatómicos, la pose
 * de la cabeza, la línea del pelo, el perfil y los contornos y surcos de una foto existente, para
 * exigir que la toma coincida con ella sin enviar la foto.
 */
data class Protocol(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val steps: List<ProtocolStep> = emptyList(),
) {
    /** Total de fotos pedidas (suma de las cantidades de cada paso). */
    val totalShots: Int get() = steps.sumOf { it.count }
}

/** Valores por defecto de luz de una toma nueva (rostro). */
object ProtocolDefaults {
    /** Luz mínima en el rostro (como el aviso «Poca luz» de la estandarización). */
    const val MIN_LIGHT = 0.35f
    /** Diferencia máxima de luz entre mejillas. */
    const val MAX_LIGHT_ASYM = 0.15f

    /** Toma nueva con la coincidencia, la luz y el disparo automático por defecto. */
    fun step(zone: Zone, expression: FacialExpression = FacialExpression.NEUTRAL, ref: ProtocolRef? = null, threshold: Float? = null) =
        withRefLight(ProtocolStep(zone = zone, expression = expression, threshold = threshold, autoCapture = true,
            minLight = MIN_LIGHT.takeIf { zone.isFaceZone() }, maxLightAsym = MAX_LIGHT_ASYM.takeIf { evenLightApplies(zone) }, ref = ref))

    /**
     * La luz pareja entre mejillas se pide por defecto solo en vistas frontales del rostro: en las
     * oblicuas y los perfiles una mejilla queda de lado y su luz es distinta por la forma de la cara.
     */
    fun evenLightApplies(zone: Zone) = zone == Zone.FACE_FRONTAL

    /**
     * Ajusta la luz pedida a la de la referencia, si se midió: mínima y máxima ±15 puntos alrededor de
     * la luz de la foto y diferencia entre mejillas con 8 puntos de margen sobre la de la foto.
     */
    fun withRefLight(s: ProtocolStep): ProtocolStep {
        val l = s.ref?.light ?: return s
        val a = s.ref.lightAsym
        return s.copy(minLight = (l - 0.15f).coerceIn(0.1f, 0.8f), maxLight = (l + 0.15f).coerceIn(0.3f, 0.98f),
            maxLightAsym = if (!evenLightApplies(s.zone)) s.maxLightAsym
                else a?.let { (it + 0.08f).coerceIn(0.08f, 0.4f) } ?: s.maxLightAsym ?: MAX_LIGHT_ASYM)
    }
}

/** Una toma (o varias iguales) del protocolo. Los campos null usan el ajuste del teléfono. */
data class ProtocolStep(
    val id: String = UUID.randomUUID().toString().take(8),
    val zone: Zone,
    val expression: FacialExpression = FacialExpression.NEUTRAL,
    /** Fotos que deben tomarse en este paso. */
    val count: Int = 1,
    /** Título del paso (si no, la zona y el gesto). */
    val label: String? = null,
    /** Indicaciones para quien toma la foto o para el paciente. */
    val instructions: String? = null,
    /** % mínimo de puntos coincidentes (0..1) con la referencia o la guía. */
    val threshold: Float? = null,
    /** Disparo automático al coincidir. */
    val autoCapture: Boolean? = null,
    /** Luz mínima y máxima en el rostro (luminancia media 0..1) y diferencia máxima entre mejillas (0..1). */
    val minLight: Float? = null,
    val maxLight: Float? = null,
    val maxLightAsym: Float? = null,
    /** Exigir la mirada al frente. */
    val gaze: Boolean? = null,
    /** Cámara: "back" (trasera) o "front" (frontal); null = la elegida. */
    val lens: String? = null,
    /** Referencia sin imagen (de una foto existente). */
    val ref: ProtocolRef? = null,
) {
    val title: String get() = label?.takeIf { it.isNotBlank() }
        ?: (zone.label + if (expression != FacialExpression.NEUTRAL) " · ${expression.label}" else "")
    /** [title] en español (el que se guarda en `resultados.json`). */
    val titleEs: String get() = label?.takeIf { it.isNotBlank() }
        ?: (zone.labelEs + if (expression != FacialExpression.NEUTRAL) " · ${expression.labelEs}" else "")
}

/**
 * Características de una foto de referencia, sin la imagen: coordenadas en el encuadre 3:4 de la foto
 * (x, y en unidades de su ancho), como las usa la cámara.
 */
data class ProtocolRef(
    /** Puntos anatómicos (nombre → punto). */
    val points: Map<String, Pt> = emptyMap(),
    val yaw: Float? = null, val pitch: Float? = null, val roll: Float? = null,
    /** Dirección de la mirada. */
    val gaze: Pt? = null,
    /** Línea de implantación del pelo. */
    val hairline: List<Pt>? = null,
    /** Puntos del perfil por silueta (vistas de perfil). */
    val profile: Map<String, Pt>? = null,
    /** Contornos, rasgos y surcos de la foto (para dibujarlos como guía). */
    val lines: List<RefLine> = emptyList(),
    /** Condiciones de la toma original. */
    val distanceCm: Float? = null,
    val zoom: Float? = null,
    /** Fecha de la foto de la que sale (solo informativa). */
    val sourceTakenAt: Long? = null,
    /** Luz medida en el rostro de la foto (luminancia media 0..1) y diferencia relativa entre mejillas. */
    val light: Float? = null,
    val lightAsym: Float? = null,
)

/** Línea de la referencia: capa (contorno, surcos…), nombre de la medida si tiene, puntos y si es cerrada. */
data class RefLine(val layer: String, val name: String?, val points: List<Pt>, val closed: Boolean = false)

/** Formato del archivo de protocolo (`.photomed.json`). */
object ProtocolJson {
    const val FORMAT = "photomed.protocol"
    const val VERSION = 1
    /** Precisión de las coordenadas (1/10 000 del ancho: < 0,1 px en una foto de 1 000 px). */
    private fun r(v: Float) = (v * 10000f).roundToInt() / 10000.0

    fun write(p: Protocol): String = JSONObject()
        .put("format", FORMAT).put("version", VERSION)
        .put("id", p.id).put("name", p.name).put("author", p.author).put("notes", p.notes).put("createdAt", p.createdAt)
        .put("steps", JSONArray().apply { p.steps.forEach { put(stepJson(it)) } })
        .toString(2)

    private fun pts(l: List<Pt>) = JSONArray().apply { l.forEach { put(JSONArray().put(r(it.x)).put(r(it.y))) } }
    private fun ptMap(m: Map<String, Pt>) = JSONObject().apply { m.forEach { (k, v) -> put(k, JSONArray().put(r(v.x)).put(r(v.y))) } }

    private fun stepJson(s: ProtocolStep): JSONObject = JSONObject().apply {
        put("id", s.id); put("zone", s.zone.id); put("expression", s.expression.tag); put("count", s.count)
        s.label?.let { put("label", it) }; s.instructions?.let { put("instructions", it) }
        JSONObject().apply {
            s.threshold?.let { put("threshold", r(it)) }; s.autoCapture?.let { put("autoCapture", it) }; s.gaze?.let { put("gaze", it) }
        }.takeIf { it.length() > 0 }?.let { put("match", it) }
        JSONObject().apply {
            s.minLight?.let { put("min", r(it)) }; s.maxLight?.let { put("max", r(it)) }; s.maxLightAsym?.let { put("maxAsymmetry", r(it)) }
        }.takeIf { it.length() > 0 }?.let { put("lighting", it) }
        s.lens?.let { put("camera", JSONObject().put("lens", it)) }
        s.ref?.let { put("reference", refJson(it)) }
    }

    private fun refJson(f: ProtocolRef): JSONObject = JSONObject().apply {
        put("points", ptMap(f.points))
        f.yaw?.let { put("yaw", r(it)) }; f.pitch?.let { put("pitch", r(it)) }; f.roll?.let { put("roll", r(it)) }
        f.gaze?.let { put("gaze", JSONArray().put(r(it.x)).put(r(it.y))) }
        f.hairline?.let { put("hairline", pts(it)) }
        f.profile?.let { put("profile", ptMap(it)) }
        if (f.lines.isNotEmpty()) put("lines", JSONArray().apply {
            f.lines.forEach { l -> put(JSONObject().put("layer", l.layer).put("points", pts(l.points)).apply {
                l.name?.let { put("name", it) }; if (l.closed) put("closed", true) }) }
        })
        f.distanceCm?.let { put("distanceCm", r(it)) }; f.zoom?.let { put("zoom", r(it)) }
        f.sourceTakenAt?.let { put("sourceTakenAt", it) }
        f.light?.let { put("light", r(it)) }; f.lightAsym?.let { put("lightAsymmetry", r(it)) }
    }

    /** Lee un protocolo; error con un mensaje para el usuario si el archivo no lo es o no se entiende. */
    fun read(json: String): Protocol {
        val o = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException(tr("El archivo no es un protocolo de PhotoMed (JSON no válido).")) }
        if (o.optString("format") != FORMAT) throw IllegalArgumentException(tr("El archivo no es un protocolo de PhotoMed."))
        if (o.optInt("version", 1) > VERSION) throw IllegalArgumentException(tr("El protocolo es de una versión más nueva de PhotoMed: actualice la app."))
        val a = o.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until a.length()).map { i -> step(a.getJSONObject(i), i) }
        if (steps.isEmpty()) throw IllegalArgumentException(tr("El protocolo no tiene tomas."))
        return Protocol(o.optString("id").ifBlank { UUID.randomUUID().toString() }, o.optString("name").ifBlank { "Protocolo" },
            o.optString("author"), o.optString("notes"), o.optLong("createdAt", System.currentTimeMillis()), steps)
    }

    private fun f(o: JSONObject?, k: String): Float? = o?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k)?.toFloat()?.takeIf { !it.isNaN() }
    private fun ptOf(a: JSONArray) = Pt(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
    private fun ptsOf(a: JSONArray) = (0 until a.length()).map { ptOf(a.getJSONArray(it)) }
    private fun ptMapOf(o: JSONObject) = o.keys().asSequence().associateWith { ptOf(o.getJSONArray(it)) }

    private fun step(o: JSONObject, i: Int): ProtocolStep {
        val zone = Zone.fromId(o.optString("zone")) ?: throw IllegalArgumentException(tr("Toma %1\$d: zona desconocida «%2\$s».", i + 1, o.optString("zone")))
        val match = o.optJSONObject("match"); val light = o.optJSONObject("lighting")
        return ProtocolStep(o.optString("id").ifBlank { "s${i + 1}" }, zone, FacialExpression.ofTag(o.optString("expression", "reposo")),
            o.optInt("count", 1).coerceIn(1, 50), o.optString("label").ifBlank { null }, o.optString("instructions").ifBlank { null },
            f(match, "threshold")?.coerceIn(0.3f, 1f), match?.takeIf { it.has("autoCapture") }?.optBoolean("autoCapture"),
            f(light, "min")?.coerceIn(0f, 1f), f(light, "max")?.coerceIn(0f, 1f), f(light, "maxAsymmetry")?.coerceIn(0f, 1f),
            match?.takeIf { it.has("gaze") }?.optBoolean("gaze"),
            o.optJSONObject("camera")?.optString("lens")?.takeIf { it == "back" || it == "front" },
            o.optJSONObject("reference")?.let { ref(it) })
    }

    private fun ref(o: JSONObject) = ProtocolRef(
        o.optJSONObject("points")?.let(::ptMapOf).orEmpty(), f(o, "yaw"), f(o, "pitch"), f(o, "roll"),
        o.optJSONArray("gaze")?.let(::ptOf), o.optJSONArray("hairline")?.let(::ptsOf), o.optJSONObject("profile")?.let(::ptMapOf),
        o.optJSONArray("lines")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { l ->
            RefLine(l.optString("layer"), l.optString("name").ifBlank { null }, ptsOf(l.getJSONArray("points")), l.optBoolean("closed")) } } }.orEmpty(),
        f(o, "distanceCm"), f(o, "zoom"), o.takeIf { it.has("sourceTakenAt") }?.optLong("sourceTakenAt"),
        f(o, "light")?.coerceIn(0f, 1f), f(o, "lightAsymmetry")?.coerceIn(0f, 1f))
}
