package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn

enum class Detector { FACE, POSE, NONE }

/**
 * Estrategia de coincidencia por zona:
 * - [detector] y [landmarks]: puntos anatómicos automáticos (claves de [Landmarks]).
 * - [contours]: regiones anatómicas cuyos contornos se hacen coincidir (mandíbula, frente,
 *   orejas, caderas, falanges…), en coordenadas normalizadas del encuadre 3:4 (x ∈ [0,1], y ∈ [0,4/3]).
 */
data class ZoneAlignment(
    val detector: Detector,
    val landmarks: Set<String>,
    val contours: List<ContourRegion>,
    val manual: List<String>,
    val description: String,
) {
    /** Centroide de las regiones (pivote de escala y referencia de las indicaciones en vivo). */
    val pivot: Pt
        get() {
            val ws = contours.sumOf { it.weight.toDouble() }.toFloat()
            return Pt(contours.sumOf { (it.center.x * it.weight).toDouble() }.toFloat() / ws,
                contours.sumOf { (it.center.y * it.weight).toDouble() }.toFloat() / ws)
        }

    companion object {
        private fun r(name: String, x: Float, y: Float, rx: Float, ry: Float, w: Float = 1f) =
            ContourRegion(name, Pt(x, y), Pt(rx, ry), w)

        private fun List<ContourRegion>.mirrored(m: Boolean) =
            if (!m) this else map { it.copy(center = Pt(1f - it.center.x, it.center.y)) }

        /** Con los puntos adicionales de las zonas del cuerpo ([BodyPoints]: manos, pies, bordes de miembros y tronco). */
        fun of(zone: Zone): ZoneAlignment = base(zone).let { a ->
            val extra = BodyPoints.keysFor(zone.guide)
            if (extra.isEmpty()) a else a.copy(landmarks = a.landmarks + extra)
        }

        private fun base(zone: Zone): ZoneAlignment {
            val L = Landmarks
            return when (zone.guide) {
                GuideType.FACE_FRONT -> ZoneAlignment(
                    Detector.FACE, L.FACE_ALL,
                    listOf(r(trn("mandíbula"), 0.5f, 1.12f, 0.32f, 0.12f), r(trn("frente"), 0.5f, 0.27f, 0.36f, 0.12f, 0.8f),
                        r(trn("oreja derecha"), 0.1f, 0.76f, 0.06f, 0.14f, 0.7f), r(trn("oreja izquierda"), 0.9f, 0.76f, 0.06f, 0.14f, 0.7f),
                        r(trn("ojos y cejas"), 0.5f, 0.6f, 0.32f, 0.08f, 0.6f)),
                    listOf("Pupila derecha", "Pupila izquierda"),
                    tr("óvalo facial, cejas, ojos, nariz y labios"),
                )
                GuideType.FACE_OBLIQUE -> ZoneAlignment(
                    Detector.FACE, L.FACE_ALL,
                    listOf(r(trn("mandíbula"), 0.55f, 1.1f, 0.3f, 0.13f), r(trn("frente"), 0.55f, 0.3f, 0.35f, 0.13f, 0.8f),
                        r(trn("oreja"), 0.17f, 0.78f, 0.07f, 0.14f, 0.8f), r(trn("mejilla y nariz"), 0.83f, 0.85f, 0.08f, 0.22f, 0.8f),
                    ).mirrored(zone.mirrored),
                    listOf("Pupila derecha", "Pupila izquierda"),
                    tr("óvalo facial, ojos, nariz y labios"),
                )
                GuideType.FACE_PROFILE -> ZoneAlignment(
                    Detector.FACE, setOf(L.EAR_L, L.EAR_R, L.NOSE_BASE, L.EYE_L, L.EYE_R, L.MOUTH_L, L.MOUTH_R) +
                        L.group("nose_") + L.group("eye_") + L.group("brow_"),
                    listOf(r(trn("frente"), 0.84f, 0.5f, 0.08f, 0.16f), r(trn("nariz y labios"), 0.92f, 0.86f, 0.07f, 0.2f),
                        r(trn("mandíbula"), 0.66f, 1.08f, 0.2f, 0.08f), r(trn("oreja"), 0.43f, 0.8f, 0.07f, 0.12f),
                        r(trn("occipucio"), 0.1f, 0.7f, 0.08f, 0.25f, 0.5f)).mirrored(zone.mirrored),
                    listOf("Tragus (oreja)", "Punta nasal"),
                    tr("oreja, ojo, nariz y perfil"),
                )
                GuideType.FOREHEAD -> ZoneAlignment(
                    Detector.FACE, L.group("brow_") + L.group("eye_") + L.ovalRange(0..6) + L.ovalRange(30..35) + L.mesh(L.MESH_BROWS) + L.mesh(L.MESH_EYES),
                    listOf(r(trn("cejas"), 0.5f, 0.9f, 0.4f, 0.08f), r(trn("frente"), 0.5f, 0.3f, 0.45f, 0.15f, 0.8f)),
                    listOf("Cabeza de ceja derecha", "Cabeza de ceja izquierda"),
                    tr("cejas y contorno frontal"),
                )
                GuideType.PERIOCULAR -> ZoneAlignment(
                    Detector.FACE, L.group("eye_") + L.group("brow_") + setOf(L.EYE_L, L.EYE_R) + L.mesh(L.MESH_EYES) + L.mesh(L.MESH_BROWS),
                    listOf(r(trn("ojos"), 0.5f, 0.69f, 0.4f, 0.08f), r(trn("cejas"), 0.5f, 0.5f, 0.4f, 0.06f, 0.7f)),
                    listOf("Pupila derecha", "Pupila izquierda"),
                    tr("contornos palpebrales y cejas"),
                )
                GuideType.LIPS -> ZoneAlignment(
                    Detector.FACE, L.group("lip_") + L.group("nose_") + setOf(L.MOUTH_L, L.MOUTH_R) + L.mesh(L.MESH_LIPS) + L.mesh(L.MESH_NOSE),
                    listOf(r(trn("labios"), 0.5f, 0.69f, 0.3f, 0.1f), r(trn("base nasal"), 0.5f, 0.2f, 0.25f, 0.08f, 0.6f),
                        r(trn("mentón"), 0.5f, 1.2f, 0.4f, 0.1f, 0.6f)),
                    listOf("Comisura derecha", "Comisura izquierda"),
                    tr("bermellón, comisuras y base nasal"),
                )
                GuideType.NECK -> ZoneAlignment(
                    Detector.POSE, setOf(L.SHOULDER_L, L.SHOULDER_R, L.MOUTH_L, L.MOUTH_R, L.EAR_L, L.EAR_R, L.NOSE),
                    listOf(r(trn("mandíbula"), 0.5f, 0.35f, 0.3f, 0.1f), r(trn("hombros y clavículas"), 0.5f, 0.78f, 0.45f, 0.14f)),
                    listOf("Mentón", "Escotadura yugular"),
                    tr("mandíbula, hombros y contorno cervical"),
                )
                GuideType.SCALP -> ZoneAlignment(
                    Detector.NONE, emptySet(),
                    listOf(r(trn("vértice"), 0.5f, 0.667f, 0.2f, 0.18f), r(trn("contorno craneal"), 0.5f, 0.667f, 0.4f, 0.5f, 0.8f),
                        r(trn("raya y línea frontal"), 0.5f, 0.3f, 0.25f, 0.2f, 0.7f)),
                    listOf("Vértice", "Línea de implantación frontal (centro)"),
                    tr("contorno del cráneo, raya, vértice y patrón del cabello"),
                )
                GuideType.SCALP_CROWN -> ZoneAlignment(
                    Detector.NONE, emptySet(),
                    listOf(r(trn("remolino de la coronilla"), 0.5f, 0.40f, 0.18f, 0.16f), r(trn("contorno craneal y orejas"), 0.5f, 0.55f, 0.45f, 0.4f, 0.8f),
                        r(trn("nuca y hombros"), 0.5f, 1.05f, 0.47f, 0.2f, 0.7f)),
                    listOf("Remolino (coronilla)", "Protuberancia occipital"),
                    tr("contorno del cráneo, remolino, nuca y patrón del cabello"),
                )
                GuideType.SCALP_PARIETAL -> ZoneAlignment(
                    Detector.NONE, emptySet(),
                    listOf(r(trn("región parietal"), 0.48f, 0.63f, 0.3f, 0.25f), r(trn("contorno craneal"), 0.5f, 0.4f, 0.47f, 0.3f, 0.8f),
                        r(trn("oreja y sien"), 0.6f, 1.05f, 0.28f, 0.16f, 0.7f)).mirrored(zone.mirrored),
                    listOf("Hélix (oreja)", "Raya (línea media)"),
                    tr("contorno del cráneo, raya, oreja y patrón del cabello"),
                )
                GuideType.EYE -> ZoneAlignment(
                    Detector.NONE, emptySet(),
                    // más regiones de borde (más puntos de referencia): iris y pupila, párpado inferior y ojera,
                    // canto interno y pliegue del párpado superior
                    listOf(r(trn("párpados"), 0.5f, 0.73f, 0.3f, 0.08f), r(trn("ceja"), 0.5f, 0.45f, 0.35f, 0.08f, 0.8f),
                        r(trn("patas de gallo"), 0.15f, 0.75f, 0.12f, 0.15f, 0.6f),
                        r(trn("iris y pupila"), 0.5f, 0.73f, 0.08f, 0.07f, 0.9f),
                        r(trn("párpado inferior y ojera"), 0.5f, 0.86f, 0.28f, 0.07f, 0.6f),
                        r(trn("canto interno"), 0.83f, 0.75f, 0.07f, 0.07f, 0.6f),
                        r(trn("pliegue del párpado superior"), 0.5f, 0.62f, 0.28f, 0.05f, 0.6f)).mirrored(zone.mirrored),
                    listOf("Pupila", "Canto lateral"),
                    tr("párpados, ceja y pliegues laterales"),
                )
                GuideType.TORSO_FRONT -> ZoneAlignment(
                    Detector.POSE, L.TRUNK,
                    listOf(r(trn("cintura"), 0.5f, 0.72f, 0.45f, 0.14f), r(trn("caderas"), 0.5f, 1.12f, 0.45f, 0.12f),
                        r(trn("ombligo"), 0.5f, 0.78f, 0.08f, 0.08f, 0.5f)),
                    listOf("Escotadura yugular", "Ombligo"),
                    tr("hombros, caderas, cintura y ombligo"),
                )
                GuideType.BREASTS -> ZoneAlignment(
                    Detector.POSE, L.TRUNK,
                    listOf(r(trn("contorno mamario"), 0.5f, 0.62f, 0.35f, 0.12f), r(trn("hombros"), 0.5f, 0.15f, 0.45f, 0.1f, 0.7f)),
                    listOf("Pezón derecho", "Pezón izquierdo"),
                    tr("hombros, caderas y contorno torácico"),
                )
                GuideType.TORSO_SIDE -> ZoneAlignment(
                    Detector.POSE, L.TRUNK + setOf(L.KNEE_L, L.KNEE_R, L.ELBOW_L, L.ELBOW_R),
                    listOf(r(trn("abdomen"), 0.7f, 0.75f, 0.12f, 0.3f), r(trn("glúteo"), 0.27f, 1.05f, 0.12f, 0.2f),
                        r(trn("axila"), 0.45f, 0.25f, 0.2f, 0.1f, 0.7f)).mirrored(zone.mirrored),
                    listOf("Hombro (acromion)", "Cadera (trocánter)"),
                    tr("silueta abdominal, glúteo y axila"),
                )
                GuideType.BACK -> ZoneAlignment(
                    Detector.POSE, L.TRUNK,
                    listOf(r(trn("escápulas"), 0.5f, 0.4f, 0.35f, 0.12f), r(trn("cintura"), 0.5f, 0.95f, 0.42f, 0.15f),
                        r(trn("hombros"), 0.5f, 0.12f, 0.45f, 0.08f, 0.6f)),
                    listOf("Vértebra C7", "Inicio del pliegue interglúteo"),
                    tr("hombros, escápulas, cintura y caderas"),
                )
                GuideType.GLUTES -> ZoneAlignment(
                    Detector.POSE, setOf(L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R),
                    listOf(r(trn("pliegues glúteos"), 0.5f, 0.75f, 0.35f, 0.08f), r(trn("caderas"), 0.5f, 0.4f, 0.45f, 0.15f)),
                    listOf("Pliegue glúteo derecho (centro)", "Pliegue glúteo izquierdo (centro)"),
                    tr("caderas, pliegues glúteos y muslos"),
                )
                GuideType.LEGS -> ZoneAlignment(
                    Detector.POSE, setOf(L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R, L.ANKLE_L, L.ANKLE_R,
                        L.HEEL_L, L.HEEL_R, L.FOOT_INDEX_L, L.FOOT_INDEX_R),
                    listOf(r(trn("rodillas"), 0.5f, 0.66f, 0.35f, 0.08f), r(trn("muslos"), 0.5f, 0.35f, 0.45f, 0.2f)),
                    listOf("Rodilla derecha (centro)", "Rodilla izquierda (centro)"),
                    tr("caderas, rodillas, tobillos y bordes"),
                )
                GuideType.ARM -> ZoneAlignment(
                    Detector.POSE, setOf(L.SHOULDER_L, L.SHOULDER_R, L.ELBOW_L, L.ELBOW_R, L.WRIST_L, L.WRIST_R,
                        L.INDEX_L, L.INDEX_R, L.PINKY_L, L.PINKY_R),
                    listOf(r(trn("codo"), 0.55f, 0.55f, 0.25f, 0.1f), r(trn("brazo"), 0.55f, 0.6f, 0.3f, 0.45f, 0.7f)).mirrored(zone.mirrored),
                    listOf("Acromion (hombro)", "Olécranon (codo)"),
                    tr("hombro, codo, muñeca y bordes del brazo"),
                )
                GuideType.HANDS -> ZoneAlignment(
                    Detector.POSE, setOf(L.WRIST_L, L.WRIST_R, L.INDEX_L, L.INDEX_R, L.PINKY_L, L.PINKY_R, L.THUMB_L, L.THUMB_R),
                    listOf(r(trn("falanges"), 0.5f, 0.86f, 0.45f, 0.12f), r(trn("dorso / palma"), 0.5f, 0.62f, 0.45f, 0.12f, 0.7f)),
                    listOf("Punta del dedo medio derecho", "Punta del dedo medio izquierdo"),
                    tr("falanges, muñecas y bordes de las manos"),
                )
                GuideType.FREE -> ZoneAlignment(
                    Detector.NONE, emptySet(),
                    listOf(r(trn("encuadre"), 0.5f, 0.667f, 0.46f, 0.62f)),
                    emptyList(),
                    tr("contornos de todo lo visible (silueta y bordes)"),
                )
                GuideType.FEET -> ZoneAlignment(
                    Detector.POSE, setOf(L.ANKLE_L, L.ANKLE_R, L.HEEL_L, L.HEEL_R, L.FOOT_INDEX_L, L.FOOT_INDEX_R),
                    listOf(r(trn("falanges"), 0.5f, 0.98f, 0.45f, 0.1f), r(trn("tobillos y talones"), 0.5f, 0.55f, 0.45f, 0.15f, 0.8f)),
                    listOf("Punta del primer dedo derecho", "Punta del primer dedo izquierdo"),
                    tr("dedos, tobillos, talones y bordes de los pies"),
                )
            }
        }
    }
}

/** Claves de puntos anatómicos (independientes del detector). */
object Landmarks {
    const val EYE_L = "eye_l"
    const val EYE_R = "eye_r"
    const val EAR_L = "ear_l"
    const val EAR_R = "ear_r"
    const val NOSE = "nose"
    const val NOSE_BASE = "nose_base"
    const val MOUTH_L = "mouth_l"
    const val MOUTH_R = "mouth_r"
    const val SHOULDER_L = "shoulder_l"
    const val SHOULDER_R = "shoulder_r"
    const val ELBOW_L = "elbow_l"
    const val ELBOW_R = "elbow_r"
    const val WRIST_L = "wrist_l"
    const val WRIST_R = "wrist_r"
    const val HIP_L = "hip_l"
    const val HIP_R = "hip_r"
    const val KNEE_L = "knee_l"
    const val KNEE_R = "knee_r"
    const val ANKLE_L = "ankle_l"
    const val ANKLE_R = "ankle_r"
    const val INDEX_L = "index_l"
    const val INDEX_R = "index_r"
    const val PINKY_L = "pinky_l"
    const val PINKY_R = "pinky_r"
    const val THUMB_L = "thumb_l"
    const val THUMB_R = "thumb_r"
    const val HEEL_L = "heel_l"
    const val HEEL_R = "heel_r"
    const val FOOT_INDEX_L = "foot_index_l"
    const val FOOT_INDEX_R = "foot_index_r"

    /** Contornos faciales de ML Kit: grupo → número de puntos (claves "<grupo><n>"). */
    val FACE_CONTOURS: Map<String, Int> = linkedMapOf(
        "oval_" to 36,
        "brow_l_top_" to 5, "brow_l_bottom_" to 5, "brow_r_top_" to 5, "brow_r_bottom_" to 5,
        "eye_l_" to 16, "eye_r_" to 16,
        "lip_upper_top_" to 11, "lip_upper_bottom_" to 9, "lip_lower_top_" to 9, "lip_lower_bottom_" to 9,
        "nose_bridge_" to 2, "nose_bottom_" to 3,
    )

    fun group(prefix: String): Set<String> =
        FACE_CONTOURS.filterKeys { it.startsWith(prefix) }.flatMap { (g, n) -> (0 until n).map { "$g$it" } }.toSet()

    fun ovalRange(r: IntRange): Set<String> = r.map { "oval_$it" }.toSet()

    // Rasgos de la malla completa de MediaPipe (claves "m<índice>"): ojos, cejas, nariz y labios con su
    // topología real. Solo los aporta el motor MediaPipe; con ML Kit no están y no cuentan.
    val MESH_EYES = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7,
        362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382)
    val MESH_BROWS = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46, 300, 293, 334, 296, 336, 285, 295, 282, 283, 276)
    val MESH_NOSE = intArrayOf(168, 6, 197, 195, 5, 4, 1, 19, 94, 2, 193, 122, 196, 3, 51, 45, 417, 351, 419, 248, 281, 275,
        115, 48, 64, 98, 97, 344, 278, 294, 327, 326, 129, 358)
    val MESH_LIPS = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146,
        78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95)
    fun mesh(idx: IntArray): Set<String> = idx.map { "m$it" }.toSet()
    val MESH_FEATURES: Set<String> = mesh(MESH_EYES) + mesh(MESH_BROWS) + mesh(MESH_NOSE) + mesh(MESH_LIPS)
    /** Región de un punto de la malla (para la validación por rasgo). */
    fun meshRegion(key: String): String? {
        val i = key.removePrefix("m").toIntOrNull() ?: return null
        return when (i) { in MESH_EYES -> trn("ojos"); in MESH_BROWS -> trn("cejas"); in MESH_NOSE -> trn("nariz"); in MESH_LIPS -> trn("labios"); else -> null }
    }

    val FACE_ALL: Set<String> = MESH_FEATURES + FACE_CONTOURS.flatMap { (g, n) -> (0 until n).map { "$g$it" } }.toSet() +
        setOf(EYE_L, EYE_R, NOSE_BASE, MOUTH_L, MOUTH_R, EAR_L, EAR_R)

    val TRUNK = setOf(SHOULDER_L, SHOULDER_R, HIP_L, HIP_R, ELBOW_L, ELBOW_R)

    /** Nombre legible de la región de un punto (para informar qué contornos se usaron). */
    fun regionOf(key: String): String = meshRegion(key) ?: when {
        key.startsWith("oval_") -> key.removePrefix("oval_").toInt().let {
            if (it in 0..6 || it in 30..35) trn("frente") else if (it in 12..24) trn("mandíbula") else trn("mejillas")
        }
        key.startsWith("brow_") -> trn("cejas")
        key.startsWith("eye") -> trn("ojos")
        key.startsWith("lip_") || key.startsWith("mouth") -> trn("labios")
        key.startsWith("nose") -> trn("nariz")
        key.startsWith("ear") -> trn("orejas")
        key.startsWith("shoulder") -> trn("hombros")
        key.startsWith("elbow") -> trn("codos")
        key.startsWith("wrist") || key.startsWith("index") || key.startsWith("pinky") || key.startsWith("thumb") -> trn("manos")
        key.startsWith("hip") -> trn("caderas")
        key.startsWith("knee") -> trn("rodillas")
        else -> trn("pies")
    }
}

/**
 * Combina los puntos anatómicos detectados en ambas fotos.
 * Devuelve la semejanza después → antes, o null si hay < 2 puntos comunes.
 * Rechazo iterativo de atípicos: mientras haya ≥ 4 puntos, se descarta el par con residuo
 * > 9 × la mediana (hasta un 25 % de los puntos).
 */
fun landmarkTransform(allowed: Set<String>, before: Map<String, Pt>, after: Map<String, Pt>): Pair<Similarity, List<String>>? {
    var keys = allowed.filter { it in before && it in after }.sorted()
    if (keys.size < 2) return null
    var t = Similarity.estimate(keys.map { after[it]!! }, keys.map { before[it]!! }) ?: return null
    val maxRemove = keys.size / 4
    var removed = 0
    while (keys.size >= 4 && removed < maxRemove.coerceAtLeast(1)) {
        val residuals = keys.map { k -> val d = t.apply(after[k]!!) - before[k]!!; k to (d.x * d.x + d.y * d.y) }
        val median = residuals.map { it.second }.sorted()[residuals.size / 2]
        val worst = residuals.maxBy { it.second }
        if (worst.second <= 9 * median || worst.second <= 1e-4f) break
        keys = keys - worst.first
        removed++
        t = Similarity.estimate(keys.map { after[it]!! }, keys.map { before[it]!! }) ?: break
    }
    return t to keys
}

/** Zona que se encuadra con el detector de rostro (luz del rostro, mejillas…). */
fun com.photomed.app.data.Zone.isFaceZone() = ZoneAlignment.of(this).detector == Detector.FACE
