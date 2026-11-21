package com.photomed.app.data

import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn

enum class ZoneCategory(private val labelEs: String) {
    FACIAL(trn("Facial")), CAPILAR(trn("Capilar")), CORPORAL(trn("Corporal")), FREE(trn("Libre"));

    /** Etiqueta en el idioma de la interfaz. */
    val label: String get() = tr(labelEs)
}

/** Tipo de guía (contorno) que se dibuja sobre la cámara para cada zona. */
enum class GuideType {
    FACE_FRONT, FACE_OBLIQUE, FACE_PROFILE, FOREHEAD, PERIOCULAR, LIPS, NECK,
    /** Cuero cabelludo, vista cenital (vértice), para tricología. */
    SCALP,
    /** Cuero cabelludo: corona (coronilla), cabeza flexionada, vista posterosuperior. */
    SCALP_CROWN,
    /** Cuero cabelludo: región parietal de un lado, vista lateral superior. */
    SCALP_PARIETAL,
    /** Primer plano de un ojo (párpados, cola de la ceja y patas de gallo). */
    EYE,
    TORSO_FRONT, BREASTS, TORSO_SIDE, BACK, GLUTES, LEGS, ARM, HANDS, FEET,
    /** Modo libre: la guía es el contorno de lo visible (referencia o cuadro actual). */
    FREE
}

/**
 * Zona anatómica estandarizada. [mirrored] invierte horizontalmente la guía
 * (p. ej. perfil derecho vs. izquierdo).
 */
enum class Zone(
    val id: String,
    /** Etiqueta en español: es la que se guarda en los metadatos de la foto (pm:ZoneLabel). */
    val labelEs: String,
    val category: ZoneCategory,
    val guide: GuideType,
    val mirrored: Boolean = false,
    private val hintEs: String,
) {
    FACE_FRONTAL("rostro_frontal", trn("Rostro frontal"), ZoneCategory.FACIAL, GuideType.FACE_FRONT,
        hintEs = trn("Mirada al frente, plano de Frankfort horizontal, cabello recogido.")),
    FACE_OBLIQUE_R("rostro_oblicuo_der", trn("Oblicuo 45° derecho"), ZoneCategory.FACIAL, GuideType.FACE_OBLIQUE,
        hintEs = trn("Girar 45° mostrando el lado derecho; punta nasal alineada con la mejilla.")),
    FACE_OBLIQUE_L("rostro_oblicuo_izq", trn("Oblicuo 45° izquierdo"), ZoneCategory.FACIAL, GuideType.FACE_OBLIQUE, mirrored = true,
        hintEs = trn("Girar 45° mostrando el lado izquierdo; punta nasal alineada con la mejilla.")),
    FACE_PROFILE_R("rostro_perfil_der", trn("Perfil derecho"), ZoneCategory.FACIAL, GuideType.FACE_PROFILE,
        hintEs = trn("Perfil a 90°, línea de Frankfort sobre la guía horizontal.")),
    FACE_PROFILE_L("rostro_perfil_izq", trn("Perfil izquierdo"), ZoneCategory.FACIAL, GuideType.FACE_PROFILE, mirrored = true,
        hintEs = trn("Perfil a 90°, línea de Frankfort sobre la guía horizontal.")),
    FOREHEAD("tercio_superior", trn("Frente y glabela"), ZoneCategory.FACIAL, GuideType.FOREHEAD,
        hintEs = trn("Encuadrar de la línea de implantación a las cejas; tomar en reposo y en contracción.")),
    PERIOCULAR("periocular", trn("Periocular"), ZoneCategory.FACIAL, GuideType.PERIOCULAR,
        hintEs = trn("Pupilas sobre la línea horizontal; tomar en reposo y sonriendo.")),
    EYE_R("ojo_der", trn("Ojo derecho"), ZoneCategory.FACIAL, GuideType.EYE,
        hintEs = trn("Primer plano del ojo derecho, mirada al frente; pupila en la cruz, cola de la ceja y patas de gallo dentro del encuadre (a la izquierda); en reposo y sonriendo.")),
    EYE_L("ojo_izq", trn("Ojo izquierdo"), ZoneCategory.FACIAL, GuideType.EYE, mirrored = true,
        hintEs = trn("Primer plano del ojo izquierdo, mirada al frente; pupila en la cruz, cola de la ceja y patas de gallo dentro del encuadre (a la derecha); en reposo y sonriendo.")),
    LIPS("labios", trn("Labios / peribucal"), ZoneCategory.FACIAL, GuideType.LIPS,
        hintEs = trn("Labios relajados en contacto, comisuras sobre la línea horizontal.")),
    NECK("cuello", trn("Cuello y escote"), ZoneCategory.FACIAL, GuideType.NECK,
        hintEs = trn("Mentón al frente, hombros relajados.")),
    SCALP_VERTEX("cuero_cabelludo_vertice", trn("Vértice"), ZoneCategory.CAPILAR, GuideType.SCALP,
        hintEs = trn("Vista cenital: cámara paralela al suelo sobre la cabeza, frente hacia arriba del encuadre, raya en la línea media y el punto más alto del cráneo en la cruz; cabello seco y peinado igual en cada toma.")),
    SCALP_CROWN("cuero_cabelludo_corona", trn("Corona (coronilla)"), ZoneCategory.CAPILAR, GuideType.SCALP_CROWN,
        hintEs = trn("Vista posterior: paciente sentado de espaldas; cámara detrás y algo por encima (≈ 35° hacia abajo); remolino de la coronilla en la marca, nuca y hombros abajo.")),
    SCALP_PARIETAL_R("cuero_cabelludo_parietal_der", trn("Parietal derecho"), ZoneCategory.CAPILAR, GuideType.SCALP_PARIETAL,
        hintEs = trn("Cámara por encima del lado derecho (≈ 45° hacia abajo); oreja derecha abajo en la marca, raya arriba siguiendo la curva, frente hacia la derecha del encuadre.")),
    SCALP_PARIETAL_L("cuero_cabelludo_parietal_izq", trn("Parietal izquierdo"), ZoneCategory.CAPILAR, GuideType.SCALP_PARIETAL, mirrored = true,
        hintEs = trn("Cámara por encima del lado izquierdo (≈ 45° hacia abajo); oreja izquierda abajo en la marca, raya arriba siguiendo la curva, frente hacia la izquierda del encuadre.")),
    TORSO_FRONT("abdomen_frontal", trn("Abdomen frontal"), ZoneCategory.CORPORAL, GuideType.TORSO_FRONT,
        hintEs = trn("Brazos separados del cuerpo; ombligo sobre la cruz de referencia.")),
    TORSO_SIDE("abdomen_lateral", trn("Abdomen lateral derecho"), ZoneCategory.CORPORAL, GuideType.TORSO_SIDE,
        hintEs = trn("Perfil derecho a 90°, brazos elevados por encima de la cabeza (axila visible).")),
    TORSO_SIDE_L("abdomen_lateral_izq", trn("Abdomen lateral izquierdo"), ZoneCategory.CORPORAL, GuideType.TORSO_SIDE,
        mirrored = true, hintEs = trn("Perfil izquierdo a 90°, brazos elevados por encima de la cabeza (axila visible).")),
    BREASTS("torax_mamas", trn("Tórax / mamas"), ZoneCategory.CORPORAL, GuideType.BREASTS,
        hintEs = trn("Brazos a los lados; complejos areola-pezón sobre las marcas.")),
    BACK("espalda", trn("Espalda"), ZoneCategory.CORPORAL, GuideType.BACK,
        hintEs = trn("Columna sobre la línea media, brazos relajados.")),
    GLUTES("gluteos", trn("Glúteos"), ZoneCategory.CORPORAL, GuideType.GLUTES,
        hintEs = trn("Pies ligeramente separados, peso distribuido; pliegue glúteo sobre la línea inferior.")),
    LEGS("muslos", trn("Muslos / piernas"), ZoneCategory.CORPORAL, GuideType.LEGS,
        hintEs = trn("Vista frontal, pies ligeramente separados; rótulas sobre las marcas.")),
    ARM_R("brazo_der", trn("Brazo derecho"), ZoneCategory.CORPORAL, GuideType.ARM,
        hintEs = trn("Vista posterior, brazo relajado junto al cuerpo y palma hacia el muslo; codo sobre la línea.")),
    ARM_L("brazo_izq", trn("Brazo izquierdo"), ZoneCategory.CORPORAL, GuideType.ARM, mirrored = true,
        hintEs = trn("Vista posterior, brazo relajado junto al cuerpo y palma hacia el muslo; codo sobre la línea.")),
    HANDS_DORSAL("manos_dorso", trn("Manos (dorso)"), ZoneCategory.CORPORAL, GuideType.HANDS,
        hintEs = trn("Ambas manos sobre fondo liso, dedos ligeramente separados, cámara paralela a la superficie.")),
    HANDS_PALMAR("manos_palma", trn("Manos (palma)"), ZoneCategory.CORPORAL, GuideType.HANDS,
        hintEs = trn("Palmas hacia la cámara, dedos ligeramente separados, muñecas en el borde superior.")),
    FEET_DORSAL("pies_dorso", trn("Pies (dorso)"), ZoneCategory.CORPORAL, GuideType.FEET,
        hintEs = trn("Paciente de pie, pies paralelos separados; cámara cenital sobre el dorso.")),
    FEET_PLANTAR("pies_planta", trn("Pies (planta)"), ZoneCategory.CORPORAL, GuideType.FEET,
        hintEs = trn("Paciente en decúbito prono, plantas hacia la cámara, talones arriba.")),
    FREE("libre", trn("Modo libre"), ZoneCategory.FREE, GuideType.FREE,
        hintEs = trn("Cualquier región: la guía se diseña con el contorno de lo visible (fondo liso o persona); en las siguientes tomas, con el de la foto de referencia."));

    /** Etiqueta en el idioma de la interfaz. */
    val label: String get() = tr(labelEs)
    /** Indicación en el idioma de la interfaz. */
    val hint: String get() = tr(hintEs)

    companion object {
        fun fromId(id: String?): Zone? = entries.firstOrNull { it.id == id }
    }
}
