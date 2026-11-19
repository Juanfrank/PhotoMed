package com.photomed.app.data

enum class ZoneCategory(val label: String) { FACIAL("Facial"), CAPILAR("Capilar"), CORPORAL("Corporal"), FREE("Libre") }

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
    val label: String,
    val category: ZoneCategory,
    val guide: GuideType,
    val mirrored: Boolean = false,
    val hint: String,
) {
    FACE_FRONTAL("rostro_frontal", "Rostro frontal", ZoneCategory.FACIAL, GuideType.FACE_FRONT,
        hint = "Mirada al frente, plano de Frankfort horizontal, cabello recogido."),
    FACE_OBLIQUE_R("rostro_oblicuo_der", "Oblicuo 45° derecho", ZoneCategory.FACIAL, GuideType.FACE_OBLIQUE,
        hint = "Girar 45° mostrando el lado derecho; punta nasal alineada con la mejilla."),
    FACE_OBLIQUE_L("rostro_oblicuo_izq", "Oblicuo 45° izquierdo", ZoneCategory.FACIAL, GuideType.FACE_OBLIQUE, mirrored = true,
        hint = "Girar 45° mostrando el lado izquierdo; punta nasal alineada con la mejilla."),
    FACE_PROFILE_R("rostro_perfil_der", "Perfil derecho", ZoneCategory.FACIAL, GuideType.FACE_PROFILE,
        hint = "Perfil a 90°, línea de Frankfort sobre la guía horizontal."),
    FACE_PROFILE_L("rostro_perfil_izq", "Perfil izquierdo", ZoneCategory.FACIAL, GuideType.FACE_PROFILE, mirrored = true,
        hint = "Perfil a 90°, línea de Frankfort sobre la guía horizontal."),
    FOREHEAD("tercio_superior", "Frente y glabela", ZoneCategory.FACIAL, GuideType.FOREHEAD,
        hint = "Encuadrar de la línea de implantación a las cejas; tomar en reposo y en contracción."),
    PERIOCULAR("periocular", "Periocular", ZoneCategory.FACIAL, GuideType.PERIOCULAR,
        hint = "Pupilas sobre la línea horizontal; tomar en reposo y sonriendo."),
    EYE_R("ojo_der", "Ojo derecho", ZoneCategory.FACIAL, GuideType.EYE,
        hint = "Primer plano del ojo derecho, mirada al frente; pupila en la cruz, cola de la ceja y patas de gallo dentro del encuadre (a la izquierda); en reposo y sonriendo."),
    EYE_L("ojo_izq", "Ojo izquierdo", ZoneCategory.FACIAL, GuideType.EYE, mirrored = true,
        hint = "Primer plano del ojo izquierdo, mirada al frente; pupila en la cruz, cola de la ceja y patas de gallo dentro del encuadre (a la derecha); en reposo y sonriendo."),
    LIPS("labios", "Labios / peribucal", ZoneCategory.FACIAL, GuideType.LIPS,
        hint = "Labios relajados en contacto, comisuras sobre la línea horizontal."),
    NECK("cuello", "Cuello y escote", ZoneCategory.FACIAL, GuideType.NECK,
        hint = "Mentón al frente, hombros relajados."),
    SCALP_VERTEX("cuero_cabelludo_vertice", "Vértice", ZoneCategory.CAPILAR, GuideType.SCALP,
        hint = "Vista cenital: cámara paralela al suelo sobre la cabeza, frente hacia arriba del encuadre, raya en la línea media y el punto más alto del cráneo en la cruz; cabello seco y peinado igual en cada toma."),
    SCALP_CROWN("cuero_cabelludo_corona", "Corona (coronilla)", ZoneCategory.CAPILAR, GuideType.SCALP_CROWN,
        hint = "Paciente sentado con la cabeza flexionada; cámara por detrás y encima (≈ 75° hacia abajo); remolino de la coronilla en la cruz, nuca abajo."),
    SCALP_PARIETAL_R("cuero_cabelludo_parietal_der", "Parietal derecho", ZoneCategory.CAPILAR, GuideType.SCALP_PARIETAL,
        hint = "Cámara por encima del lado derecho (≈ 45° hacia abajo); oreja derecha abajo en la marca, raya arriba siguiendo la curva, frente hacia la derecha del encuadre."),
    SCALP_PARIETAL_L("cuero_cabelludo_parietal_izq", "Parietal izquierdo", ZoneCategory.CAPILAR, GuideType.SCALP_PARIETAL, mirrored = true,
        hint = "Cámara por encima del lado izquierdo (≈ 45° hacia abajo); oreja izquierda abajo en la marca, raya arriba siguiendo la curva, frente hacia la izquierda del encuadre."),
    TORSO_FRONT("abdomen_frontal", "Abdomen frontal", ZoneCategory.CORPORAL, GuideType.TORSO_FRONT,
        hint = "Brazos separados del cuerpo; ombligo sobre la cruz de referencia."),
    TORSO_SIDE("abdomen_lateral", "Abdomen lateral derecho", ZoneCategory.CORPORAL, GuideType.TORSO_SIDE,
        hint = "Perfil derecho a 90°, brazos elevados por encima de la cabeza (axila visible)."),
    TORSO_SIDE_L("abdomen_lateral_izq", "Abdomen lateral izquierdo", ZoneCategory.CORPORAL, GuideType.TORSO_SIDE,
        mirrored = true, hint = "Perfil izquierdo a 90°, brazos elevados por encima de la cabeza (axila visible)."),
    BREASTS("torax_mamas", "Tórax / mamas", ZoneCategory.CORPORAL, GuideType.BREASTS,
        hint = "Brazos a los lados; complejos areola-pezón sobre las marcas."),
    BACK("espalda", "Espalda", ZoneCategory.CORPORAL, GuideType.BACK,
        hint = "Columna sobre la línea media, brazos relajados."),
    GLUTES("gluteos", "Glúteos", ZoneCategory.CORPORAL, GuideType.GLUTES,
        hint = "Pies ligeramente separados, peso distribuido; pliegue glúteo sobre la línea inferior."),
    LEGS("muslos", "Muslos / piernas", ZoneCategory.CORPORAL, GuideType.LEGS,
        hint = "Vista frontal, pies ligeramente separados; rótulas sobre las marcas."),
    ARM_R("brazo_der", "Brazo derecho", ZoneCategory.CORPORAL, GuideType.ARM,
        hint = "Vista posterior, brazo relajado junto al cuerpo y palma hacia el muslo; codo sobre la línea."),
    ARM_L("brazo_izq", "Brazo izquierdo", ZoneCategory.CORPORAL, GuideType.ARM, mirrored = true,
        hint = "Vista posterior, brazo relajado junto al cuerpo y palma hacia el muslo; codo sobre la línea."),
    HANDS_DORSAL("manos_dorso", "Manos (dorso)", ZoneCategory.CORPORAL, GuideType.HANDS,
        hint = "Ambas manos sobre fondo liso, dedos ligeramente separados, cámara paralela a la superficie."),
    HANDS_PALMAR("manos_palma", "Manos (palma)", ZoneCategory.CORPORAL, GuideType.HANDS,
        hint = "Palmas hacia la cámara, dedos ligeramente separados, muñecas en el borde superior."),
    FEET_DORSAL("pies_dorso", "Pies (dorso)", ZoneCategory.CORPORAL, GuideType.FEET,
        hint = "Paciente de pie, pies paralelos separados; cámara cenital sobre el dorso."),
    FEET_PLANTAR("pies_planta", "Pies (planta)", ZoneCategory.CORPORAL, GuideType.FEET,
        hint = "Paciente en decúbito prono, plantas hacia la cámara, talones arriba."),
    FREE("libre", "Modo libre", ZoneCategory.FREE, GuideType.FREE,
        hint = "Cualquier región: la guía se diseña con el contorno de lo visible (fondo liso o persona); en las siguientes tomas, con el de la foto de referencia.");

    companion object {
        fun fromId(id: String?): Zone? = entries.firstOrNull { it.id == id }
    }
}
