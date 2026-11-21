package com.photomed.app.data

import android.content.Context
import android.net.Uri
import com.photomed.app.align.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Protocolos guardados en la app (`files/protocolos/<id>.json`, el mismo formato que se comparte). */
object Protocols {
    private fun dir(context: Context) = File(context.filesDir, "protocolos").apply { mkdirs() }
    private fun file(context: Context, id: String) = File(dir(context), id.filter { it.isLetterOrDigit() || it == '-' } + ".json")

    fun list(context: Context): List<Protocol> = dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { f -> runCatching { ProtocolJson.read(f.readText()) }.getOrNull() }.sortedBy { it.name.lowercase() }

    fun get(context: Context, id: String): Protocol? = file(context, id).takeIf { it.exists() }?.let { runCatching { ProtocolJson.read(it.readText()) }.getOrNull() }

    fun save(context: Context, p: Protocol) = AtomicFile.write(file(context, p.id), ProtocolJson.write(p))

    fun delete(context: Context, id: String) { file(context, id).delete() }

    /** Importa un protocolo (archivo elegido o recibido); si ya existía uno con el mismo id, se reemplaza. */
    fun import(context: Context, uri: Uri): Protocol {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalArgumentException("No se pudo leer el archivo.")
        return ProtocolJson.read(text).also { save(context, it) }
    }

    /** Archivo para compartir (caché; nombre legible). */
    fun shareFile(context: Context, p: Protocol): File {
        val d = File(context.cacheDir, "share").apply { mkdirs() }
        return File(d, safeName(p.name) + ".photomed.json").apply { writeText(ProtocolJson.write(p)) }
    }

    /** Nombre de archivo seguro (ASCII, sin tildes: algunas apps no aceptan otros caracteres). */
    fun safeName(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .map { if ((it in 'a'..'z') || (it in 'A'..'Z') || (it in '0'..'9') || it == '-' || it == '_') it else '_' }
        .joinToString("").replace(Regex("_+"), "_").trim('_').ifEmpty { "protocolo" }.take(60)
}

/**
 * Resultados de un protocolo para devolverlos a quien lo creó: un `.zip` con el protocolo, las fotos
 * tomadas con él y `resultados.json` (paso, toma, zona, gesto, fecha y condiciones de cada foto:
 * coincidencia, luz, distancia…). Al importarlo, las fotos entran en el expediente elegido.
 */
object ProtocolResults {
    const val FORMAT = "photomed.results"
    /** Claves de las condiciones de captura que identifican la toma del protocolo. */
    const val KEY_ID = "ProtocolId"; const val KEY_NAME = "Protocol"; const val KEY_STEP = "ProtocolStep"
    const val KEY_INDEX = "ProtocolStepIndex"; const val KEY_SHOT = "ProtocolShot"

    /** Condiciones que se guardan en la foto al tomarla con el protocolo. */
    fun conditions(p: Protocol, stepIndex: Int, shot: Int): Map<String, String> = mapOf(
        KEY_ID to p.id, KEY_NAME to p.name, KEY_STEP to p.steps[stepIndex].id, KEY_INDEX to "${stepIndex + 1}", KEY_SHOT to "$shot")

    /** Fotos del expediente tomadas con el protocolo [protocolId] (por fecha), con sus datos. */
    fun photosOf(repo: PhotoRepository, exp: Expediente, protocolId: String): List<Pair<Photo, PhotoInfo>> =
        repo.photos(exp).mapNotNull { p -> repo.readInfo(p)?.takeIf { it.conditions[KEY_ID] == protocolId }?.let { p to it } }

    /** Paquete de resultados ([anonymize]: sin nombre ni número de expediente, en el paquete ni en las fotos). */
    fun buildZip(context: Context, repo: PhotoRepository, exp: Expediente, p: Protocol, anonymize: Boolean): File {
        val shots = photosOf(repo, exp, p.id)
        val d = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(d, "Resultados_" + Protocols.safeName(p.name) + ".zip")
        val results = JSONArray()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("protocolo.photomed.json")); zip.write(ProtocolJson.write(p).toByteArray()); zip.closeEntry()
            shots.forEachIndexed { i, (photo, info) ->
                val name = "fotos/%02d_%s".format(i + 1, if (anonymize) com.photomed.app.ui.gallery.anonymizedName(photo) else photo.displayName)
                val tmp = File(d, "res_${System.nanoTime()}.jpg")
                try {
                    context.contentResolver.openInputStream(photo.uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return@forEachIndexed
                    if (anonymize) PhotoMetadata.strip(tmp)
                    zip.putNextEntry(ZipEntry(name)); tmp.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                } finally { tmp.delete() }
                results.put(JSONObject().put("file", name).put("zone", photo.zone.id).put("expression", photo.expression.tag)
                    .put("takenAt", photo.takenAt).put("step", info.conditions[KEY_STEP]).put("stepIndex", info.conditions[KEY_INDEX]?.toIntOrNull())
                    .put("shot", info.conditions[KEY_SHOT]?.toIntOrNull())
                    .put("conditions", JSONObject(info.conditions.filterKeys { it !in setOf(KEY_ID, KEY_NAME) })))
            }
            val summary = JSONObject().put("format", FORMAT).put("version", 1).put("protocolId", p.id).put("protocol", p.name)
                .put("createdAt", System.currentTimeMillis()).put("requested", p.totalShots).put("taken", shots.size)
                .put("steps", JSONArray().apply { p.steps.forEachIndexed { i, s ->
                    put(JSONObject().put("index", i + 1).put("id", s.id).put("title", s.title).put("requested", s.count)
                        .put("taken", shots.count { it.second.conditions[KEY_STEP] == s.id })) } })
                .put("shots", results)
            if (!anonymize) summary.put("patient", JSONObject().put("name", exp.patientName).put("record", exp.recordNumber))
            zip.putNextEntry(ZipEntry("resultados.json")); zip.write(summary.toString(2).toByteArray()); zip.closeEntry()
        }
        return out
    }

    /** Resultado de importar un paquete. */
    data class Imported(val protocol: Protocol?, val photos: Int, val duplicates: Int)

    /**
     * Importa un paquete de resultados en [exp]: guarda el protocolo y añade las fotos (zona, gesto,
     * fecha y condiciones del paquete). Las que ya estaban (misma huella) no se repiten.
     */
    fun importZip(context: Context, repo: PhotoRepository, exp: Expediente, uri: Uri): Imported {
        val work = File(context.cacheDir, "results_${System.nanoTime()}").apply { mkdirs() }
        try {
            var protocolText: String? = null; var resultsText: String? = null
            val files = HashMap<String, File>()
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        val n = e.name
                        // nombres sin rutas fuera del directorio de trabajo
                        if (!e.isDirectory && !n.contains("..")) when {
                            n.endsWith(".photomed.json") -> protocolText = zip.readBytes().toString(Charsets.UTF_8)
                            n == "resultados.json" -> resultsText = zip.readBytes().toString(Charsets.UTF_8)
                            n.startsWith("fotos/") && n.lowercase().endsWith(".jpg") ->
                                files[n] = File(work, "f${files.size}.jpg").also { f -> f.outputStream().use { zip.copyTo(it) } }
                        }
                        e = zip.nextEntry
                    }
                }
            } ?: throw IllegalArgumentException("No se pudo leer el archivo.")
            val res = resultsText?.let { JSONObject(it) }?.takeIf { it.optString("format") == FORMAT }
                ?: throw IllegalArgumentException("El archivo no es un paquete de resultados de PhotoMed.")
            val protocol = protocolText?.let { runCatching { ProtocolJson.read(it) }.getOrNull() }?.also { Protocols.save(context, it) }
            val known = HashSet<String>()
            repo.photos(exp).forEach { p -> repo.readInfo(p)?.conditions?.get("SourceHash")?.let { known += it } }
            val used = repo.photos(exp).map { it.takenAt }.toHashSet()
            val shots = res.optJSONArray("shots") ?: JSONArray()
            var n = 0; var dup = 0
            for (i in 0 until shots.length()) {
                val s = shots.getJSONObject(i)
                val f = files[s.optString("file")] ?: continue
                val zone = Zone.fromId(s.optString("zone")) ?: continue
                // huella de la toma: imagen, fecha, zona y paso (reimportar el mismo paquete no la duplica;
                // dos tomas distintas con la misma imagen, sí se importan)
                val taken = s.optLong("takenAt", System.currentTimeMillis())
                val hash = f.inputStream().use { PhotoImport.sha1(it) } + "-$taken-${zone.id}-${s.optString("step")}-${s.optInt("shot")}"
                if (!known.add(hash)) { dup++; continue }
                var t = taken
                while (!used.add(t)) t++
                val cond = s.optJSONObject("conditions")?.let { c -> c.keys().asSequence().associateWith { c.optString(it) } }.orEmpty()
                val exprTag = s.optString("expression")
                val expression = FacialExpression.ofTag(exprTag).takeIf { zone.guide in it.guides } ?: FacialExpression.NEUTRAL
                repo.savePhoto(exp, zone, f, takenAt = t, variant = FacialExpression.variantTag(expression),
                    conditions = cond + mapOf("Source" to "resultados de protocolo", "SourceHash" to hash, "Expression" to expression.label) +
                        (protocol?.let { mapOf(KEY_ID to it.id, KEY_NAME to it.name) } ?: emptyMap()))
                n++
            }
            return Imported(protocol, n, dup)
        } finally { work.deleteRecursively() }
    }
}
