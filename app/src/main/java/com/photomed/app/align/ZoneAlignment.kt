package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone

enum class Detector { FACE, POSE, NONE }

/**
 * Estrategia de coincidencia por zona:
 * - [detector] y [landmarks]: puntos anatómicos automáticos que se usan (claves de [Landmarks]).
 * - [manual]: los dos puntos que el usuario marca en el modo manual.
 * - [roiCenter]/[roiRadius]: región que pondera la coincidencia de bordes.
 */
data class ZoneAlignment(
    val detector: Detector,
    val landmarks: Set<String>,
    val manual: List<String>,
    val description: String,
    val roiCenter: Pt = Pt(0.5f, 0.66f),
    val roiRadius: Pt = Pt(0.32f, 0.42f),
) {
    companion object {
        private val EYES = setOf(Landmarks.EYE_L, Landmarks.EYE_R)
        private val FACE_ALL = EYES + setOf(Landmarks.NOSE_BASE, Landmarks.MOUTH_L, Landmarks.MOUTH_R)

        fun of(zone: Zone): ZoneAlignment = when (zone.guide) {
            GuideType.FACE_FRONT -> ZoneAlignment(
                Detector.FACE, FACE_ALL + Landmarks.FACE_OVAL, listOf("Pupila derecha", "Pupila izquierda"),
                "Ojos, nariz, boca y óvalo facial", Pt(0.5f, 0.6f), Pt(0.28f, 0.36f),
            )
            GuideType.FACE_OBLIQUE -> ZoneAlignment(
                Detector.FACE, FACE_ALL, listOf("Pupila derecha", "Pupila izquierda"),
                "Ojos, base nasal y comisuras", Pt(0.5f, 0.6f), Pt(0.28f, 0.36f),
            )
            GuideType.FACE_PROFILE -> ZoneAlignment(
                Detector.FACE, setOf(Landmarks.EAR_L, Landmarks.EAR_R, Landmarks.NOSE_BASE) + EYES +
                    setOf(Landmarks.MOUTH_L, Landmarks.MOUTH_R),
                listOf("Tragus (oreja)", "Punta nasal"), "Oreja (tragus), ojo y base nasal",
                Pt(0.5f, 0.6f), Pt(0.3f, 0.36f),
            )
            GuideType.FOREHEAD -> ZoneAlignment(
                Detector.FACE, EYES, listOf("Cabeza de ceja derecha", "Cabeza de ceja izquierda"),
                "Ojos/cejas y contorno frontal", Pt(0.5f, 0.6f), Pt(0.4f, 0.4f),
            )
            GuideType.PERIOCULAR -> ZoneAlignment(
                Detector.FACE, EYES, listOf("Pupila derecha", "Pupila izquierda"),
                "Pupilas y contornos palpebrales", Pt(0.5f, 0.69f), Pt(0.4f, 0.3f),
            )
            GuideType.LIPS -> ZoneAlignment(
                Detector.FACE, setOf(Landmarks.MOUTH_L, Landmarks.MOUTH_R, Landmarks.NOSE_BASE),
                listOf("Comisura derecha", "Comisura izquierda"), "Comisuras labiales y bermellón",
                Pt(0.5f, 0.69f), Pt(0.36f, 0.3f),
            )
            GuideType.NECK -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.SHOULDER_L, Landmarks.SHOULDER_R, Landmarks.MOUTH_L, Landmarks.MOUTH_R),
                listOf("Mentón", "Escotadura yugular"), "Hombros, mentón y contorno cervical",
            )
            GuideType.TORSO_FRONT, GuideType.BACK -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.SHOULDER_L, Landmarks.SHOULDER_R, Landmarks.HIP_L, Landmarks.HIP_R),
                if (zone.guide == GuideType.BACK) listOf("Vértebra C7", "Inicio del pliegue interglúteo")
                else listOf("Escotadura yugular", "Ombligo"),
                "Hombros, caderas y bordes del tronco",
            )
            GuideType.BREASTS -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.SHOULDER_L, Landmarks.SHOULDER_R, Landmarks.HIP_L, Landmarks.HIP_R),
                listOf("Pezón derecho", "Pezón izquierdo"), "Hombros, caderas y contorno torácico",
                Pt(0.5f, 0.55f), Pt(0.35f, 0.35f),
            )
            GuideType.TORSO_SIDE -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.SHOULDER_L, Landmarks.SHOULDER_R, Landmarks.HIP_L, Landmarks.HIP_R,
                    Landmarks.KNEE_L, Landmarks.KNEE_R),
                listOf("Hombro (acromion)", "Cadera (trocánter)"), "Hombro, cadera y silueta lateral",
            )
            GuideType.GLUTES -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.HIP_L, Landmarks.HIP_R, Landmarks.KNEE_L, Landmarks.KNEE_R),
                listOf("Pliegue glúteo derecho (centro)", "Pliegue glúteo izquierdo (centro)"),
                "Caderas, rodillas y contorno glúteo",
            )
            GuideType.LEGS -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.HIP_L, Landmarks.HIP_R, Landmarks.KNEE_L, Landmarks.KNEE_R,
                    Landmarks.ANKLE_L, Landmarks.ANKLE_R),
                listOf("Rodilla derecha (centro)", "Rodilla izquierda (centro)"), "Caderas, rodillas, tobillos y bordes",
            )
            GuideType.HANDS -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.WRIST_L, Landmarks.WRIST_R, Landmarks.INDEX_L, Landmarks.INDEX_R,
                    Landmarks.PINKY_L, Landmarks.PINKY_R, Landmarks.THUMB_L, Landmarks.THUMB_R),
                listOf("Punta del dedo medio derecho", "Punta del dedo medio izquierdo"),
                "Muñecas, dedos y bordes de las manos", Pt(0.5f, 0.66f), Pt(0.45f, 0.3f),
            )
            GuideType.FEET -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.ANKLE_L, Landmarks.ANKLE_R, Landmarks.HEEL_L, Landmarks.HEEL_R,
                    Landmarks.FOOT_INDEX_L, Landmarks.FOOT_INDEX_R),
                listOf("Punta del primer dedo derecho", "Punta del primer dedo izquierdo"),
                "Tobillos, talones, dedos y bordes de los pies", Pt(0.5f, 0.66f), Pt(0.45f, 0.32f),
            )
            GuideType.ARM -> ZoneAlignment(
                Detector.POSE, setOf(Landmarks.SHOULDER_L, Landmarks.SHOULDER_R, Landmarks.ELBOW_L, Landmarks.ELBOW_R,
                    Landmarks.WRIST_L, Landmarks.WRIST_R),
                listOf("Acromion (hombro)", "Olécranon (codo)"), "Hombro, codo, muñeca y bordes del brazo",
                Pt(0.5f, 0.6f), Pt(0.35f, 0.45f),
            )
        }
    }
}

/** Claves de puntos anatómicos (independientes del detector). */
object Landmarks {
    const val EYE_L = "eye_l"
    const val EYE_R = "eye_r"
    const val EAR_L = "ear_l"
    const val EAR_R = "ear_r"
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

    /** 36 puntos del contorno del óvalo facial (ML Kit FaceContour.FACE). */
    val FACE_OVAL: Set<String> = (0 until 36).map { "face_$it" }.toSet()
}

/**
 * Combina los puntos anatómicos detectados en ambas fotos.
 * Devuelve la semejanza después → antes, o null si hay < 2 puntos comunes.
 * Con ≥ 4 puntos descarta el peor par si su residuo es atípico (detección errónea).
 */
fun landmarkTransform(allowed: Set<String>, before: Map<String, Pt>, after: Map<String, Pt>): Pair<Similarity, List<String>>? {
    var keys = allowed.filter { it in before && it in after }.sorted()
    if (keys.size < 2) return null
    var t = Similarity.estimate(keys.map { after[it]!! }, keys.map { before[it]!! }) ?: return null
    if (keys.size >= 4) {
        val residuals = keys.map { k -> val d = t.apply(after[k]!!) - before[k]!!; k to (d.x * d.x + d.y * d.y) }
        val median = residuals.map { it.second }.sorted()[residuals.size / 2]
        val worst = residuals.maxBy { it.second }
        if (worst.second > 9 * median && worst.second > 1e-4f) {
            keys = keys - worst.first
            t = Similarity.estimate(keys.map { after[it]!! }, keys.map { before[it]!! }) ?: t
        }
    }
    return t to keys
}
