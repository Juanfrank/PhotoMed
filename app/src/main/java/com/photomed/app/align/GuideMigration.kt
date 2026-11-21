package com.photomed.app.align

import kotlinx.coroutines.sync.withLock

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Trazos confirmados en versiones anteriores, con sus tramos y filas:
 * - 2.25–2.26 guardaban los tramos sin la fila de la ficha que representan: se enlazan por su nombre
 *   corto («ancho alar», «puente»…) y, si no, por su valor;
 * - 2.23–2.24 no guardaban tramos: los de arrugas y surcos (ancho), ojeras (alto y ancho) y bolsas
 *   (altura y surco) se reconstruyen con el propio trazo guardado; los de los rasgos (ojos, nariz,
 *   labios, contorno, frente) necesitan la cara y se recuperan una vez con [adoptFeatureGuides].
 * Los trazos y las cifras confirmadas no cambian.
 */
object GuideMigration {
    /** (comienzo del nombre de la línea, nombre corto del tramo) → fila de la ficha. */
    private val SHORT: List<Triple<String, String, String>> = listOf(
        Triple("Nariz", "ancho alar", "Ancho alar"), Triple("Nariz", "puente", "Ancho del puente (raíz)"),
        Triple("Nariz", "dorso", "Ancho del dorso (mitad)"), Triple("Nariz", "longitud", "Longitud del dorso (raíz → punta)"),
        Triple("Nariz", "alto", "Alto (entre los ojos → subnasal)"),
        Triple("Ojo", "ancho", "Ancho hendidura"), Triple("Ojo", "alto", "Alto hendidura"),
        Triple("Ceja", "altura", "Altura sobre la pupila"),
        Triple("Labio superior", "ancho", "Ancho (comisuras)"), Triple("Labio superior", "alto", "Alto en el centro"),
        Triple("Labio superior", "filtrum", "Filtrum (subnasal → labio)"), Triple("Labio inferior", "alto", "Alto en el centro"),
        Triple("Contorno", "ancho", "Ancho de la cara"), Triple("Contorno", "tercio medio", "Tercio medio (cejas → subnasal)"),
        Triple("Contorno", "tercio inferior", "Tercio inferior (subnasal → mentón)"),
        Triple("Línea del pelo", "frente", "Frente central (cejas → pelo)"),
        Triple("Línea del pelo", "frente der.", "Sobre la ceja derecha (vertical de la pupila)"),
        Triple("Línea del pelo", "frente izq.", "Sobre la ceja izquierda (vertical de la pupila)"),
        Triple("Línea del pelo", "cola der.", "Sobre la cola de la ceja derecha (canto externo)"),
        Triple("Línea del pelo", "cola izq.", "Sobre la cola de la ceja izquierda (canto externo)"),
        Triple("Bolsa", "altura", "Altura (párpado → surco inferior)"), Triple("Bolsa", "surco", "Longitud del surco inferior"),
    )

    /** «ancho alar 34,2 mm» → «ancho alar». */
    fun shortOf(label: String) = label.substringBeforeLast(" ").substringBeforeLast(" ")

    private fun fmt(v: Float) = String.format(java.util.Locale.getDefault(), "%.1f mm", v)

    /** Fila de la ficha que representa el tramo [g] de la línea [t] ("" si ninguna). */
    fun keyFor(t: StoredTrace, g: MeasureGuide): String {
        if (g.key.isNotEmpty() || g.label.isEmpty()) return g.key
        val m = t.metrics ?: return ""
        val name = m.name; val short = shortOf(g.label)
        if (!m.isFeature) return if (short == "ancho" && m.widthMm != null && m.areaMm2 == null) "Ancho (a media profundidad)" else ""
        SHORT.firstOrNull { name.startsWith(it.first) && it.second == short }?.let { return it.third }
        // por el valor: la medida con la misma cifra
        val value = g.label.removePrefix(short).trim()
        return m.features.firstOrNull { (k, v) -> k.endsWith(", mm") && fmt(v) == value }?.first?.let { FeatureMetrics.rowKey(it) } ?: ""
    }

    /** Puntos de la original (x, y normalizadas) ↔ unidades del ancho de la original. */
    private fun iso(p: Pt, a0: Float) = Pt(p.x, p.y * a0)
    private fun norm(p: Pt, a0: Float) = Pt(p.x, p.y / a0)

    /** Línea de una versión anterior con sus tramos enlazados y, si faltan, los que salen del propio trazo. */
    fun migrate(t: StoredTrace, a0: Float): StoredTrace {
        val m = t.metrics ?: return t
        if (t.guides.isNotEmpty()) {
            val g2 = t.guides.map { if (it.key.isEmpty() && it.label.isNotEmpty()) it.copy(key = keyFor(t, it)) else it }
            return if (g2 == t.guides) t else t.copy(guides = g2)
        }
        val pts = t.points.map { iso(it, a0) }
        val guides: List<MeasureGuide> = when {
            // arruga o surco: ancho perpendicular en el centro (mm por unidad, de su longitud medida)
            !m.isFeature && !t.filled && m.lengthMm != null && pts.size >= 3 -> {
                val len = LineMeasure.length(pts).takeIf { it > 1e-6f } ?: return t
                FaceAnnotations.withWidthGuide(AnnotLine(t.layer, pts, t.closed, metrics = m), m.lengthMm / len).guides
            }
            // ojera: alto (donde es más profunda) y ancho, de su contorno (arriba + abajo)
            t.layer == AnnotLayer.DARK_CIRCLES && pts.size >= 6 && m.areaMm2 != null -> {
                val area = LineMeasure.area(pts).takeIf { it > 1e-9f } ?: return t
                val mm = sqrt(m.areaMm2 / area)
                val half = pts.size / 2; val top = pts.subList(0, half); val bot = pts.subList(half, pts.size).reversed()
                val i = top.indices.filter { it < bot.size }.maxByOrNull { hypot(top[it].x - bot[it].x, top[it].y - bot[it].y) } ?: return t
                listOf(MeasureGuide(FeatureMetrics.mmText("alto", hypot(top[i].x - bot[i].x, top[i].y - bot[i].y) * mm), top[i], bot[i]),
                    MeasureGuide(FeatureMetrics.mmText("ancho", hypot(top.last().x - top.first().x, top.last().y - top.first().y) * mm), top.first(), top.last()))
            }
            // bolsa: altura en el centro y el surco inferior (cifras confirmadas)
            t.layer == AnnotLayer.EYE_BAGS && pts.size >= 6 -> {
                val f = m.features.toMap()
                val half = pts.size / 2; val top = pts.subList(0, half); val bot = pts.subList(half, pts.size).reversed()
                val c = minOf(top.size, bot.size) / 2
                listOfNotNull(
                    f["Altura (párpado → surco inferior), mm"]?.let { MeasureGuide(FeatureMetrics.mmText("altura", it), top[c], bot[c], "Altura (párpado → surco inferior)") },
                    f["Longitud del surco inferior, mm"]?.let { MeasureGuide(FeatureMetrics.mmText("surco", it), bot.first(), bot.last(), "Longitud del surco inferior") })
            }
            else -> emptyList()
        }
        return if (guides.isEmpty()) t else t.copy(guides = guides.map { it.copy(a = norm(it.a, a0), b = norm(it.b, a0)) })
    }

    fun migrate(s: TraceSet): TraceSet {
        val t2 = s.traces.map { migrate(it, s.baseAspect) }; val r2 = s.removed.map { migrate(it, s.baseAspect) }
        return if (t2 == s.traces && r2 == s.removed) s else s.copy(traces = t2, removed = r2)
    }

    /** ¿Faltan los tramos de algún rasgo (necesitan la cara para recuperarlos)? */
    fun needsFeatureGuides(s: TraceSet) = s.traces.any { needs(it) }
    private fun needs(t: StoredTrace) = t.metrics?.isFeature == true && t.layer != AnnotLayer.EYE_BAGS && t.guides.none { it.label.isNotEmpty() }

    /**
     * Toma los tramos de los rasgos de un análisis nuevo de la foto ([fresh], ya en coordenadas de la
     * original) para las líneas confirmadas que no los tienen, con las cifras confirmadas en el texto.
     */
    fun adoptFeatureGuides(s: TraceSet, fresh: List<AnnotLine>): TraceSet = s.copy(traces = s.traces.map { t ->
        if (!needs(t)) return@map t
        val src = fresh.firstOrNull { it.metrics?.name == t.name } ?: return@map t
        val f = t.metrics!!.features.associate { (k, v) -> FeatureMetrics.rowKey(k) to v }
        t.copy(guides = src.guides.map { g ->
            val v = f[g.key]
            if (v == null || g.label.isEmpty()) g else g.copy(label = FeatureMetrics.mmText(shortOf(g.label), v))
        })
    })

    private val running = HashMap<String, kotlinx.coroutines.sync.Mutex>()

    /**
     * Recupera (una sola vez) los tramos de los rasgos de una foto con trazos confirmados sin ellos:
     * analiza la foto para ubicar la cara y guarda el conjunto con los tramos. Sirve igual desde el
     * visor y desde Comparar (una sola recuperación a la vez por foto). Devuelve el conjunto nuevo, o
     * null si no hacía falta o no se pudo.
     */
    suspend fun backfill(context: android.content.Context, uri: android.net.Uri, zone: com.photomed.app.data.Zone,
                         /** Líneas de la foto con su edición (encuadre completo); por defecto, el análisis de la app. */
                         analyze: (suspend (ImageEdit) -> List<AnnotLine>)? = null): TraceSet? {
        if (analyze == null && !PhotoAnnotator.enabled) return null
        val run = analyze ?: { e -> PhotoAnnotator(context.applicationContext).analyze(ImageOps.loadFull(context, uri, 1600, e), zone).first }
        val u = uri.toString()
        val lock = synchronized(running) { running.getOrPut(u) { kotlinx.coroutines.sync.Mutex() } }
        return lock.withLock {
            // otra pantalla pudo recuperarlos mientras se esperaba
            val set = ConfirmedTraces.of(context, u) ?: return@withLock null
            if (!needsFeatureGuides(set)) return@withLock null
            runCatching {
                val e = PhotoEdits.of(context, u)
                val fresh = run(e)
                val toOrig = { q: Pt -> TraceMap.fromView(e, set.baseAspect, TraceFrame.FULL, listOf(q)).single() }
                val updated = adoptFeatureGuides(set, fresh.map { it.mapped(toOrig) })
                if (updated == set) null
                else updated.also { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ConfirmedTraces.save(context, u, it) } }
            }.getOrNull()
        }
    }
}
