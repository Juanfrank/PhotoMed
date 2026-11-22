package com.photomed.app.ui.camera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SocialDistance
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.graphics.vector.ImageVector
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn

/**
 * Indicación como icono: [icon] (qué: cabeza, barbilla, mirada, luz, cámara, distancia…), [badge]
 * (hacia dónde: flecha, giro, más/menos) y, si hace falta, un valor corto ([value], p. ej. "8°").
 * [description] es el texto completo, solo para TalkBack.
 */
data class HintCue(val icon: ImageVector, val badge: ImageVector?, val value: String?, val description: String)

object HintCues {
    private val deg = Regex("(\\d+)°")
    private fun degrees(h: String) = deg.find(h)?.groupValues?.get(1)?.let { "$it°" }

    /**
     * Prioridad (menor = antes): primero lo que cambia todo el encuadre (detección, distancia,
     * zoom, inclinación de la cámara), luego la pose de la cabeza, después mirada y luz.
     */
    fun priority(h: String): Int {
        val t = h.lowercase()
        return when {
            t.startsWith("no se detecta") || t.startsWith("detector") -> 0
            t.startsWith("≈") || t.startsWith("distancia") -> 1
            t.startsWith("zoom") -> 2
            t.startsWith("cámara") -> 3
            t.startsWith("cara:") || t.startsWith("cabeza: girar") || t.startsWith("cabeza: corregir") -> 4
            t.startsWith("barbilla") -> 5
            t.startsWith("cabeza: enderezar") -> 6
            t.startsWith("mirada") -> 7
            t.startsWith("expresión") -> 7
            else -> 8
        }
    }

    /** Icono para una indicación de texto; null si no tiene equivalente (se muestra el texto). */
    fun of(h: String): HintCue? = cue(h)?.let { it.copy(description = HintText.display(h)) }

    private fun cue(h: String): HintCue? {
        val t = h.lowercase()
        return when {
            // cabeza: giro (guiñada), perfil, lado, enderezar (alabeo)
            t.startsWith("cabeza: girar a la izquierda") -> HintCue(Icons.Default.Face, Icons.AutoMirrored.Filled.ArrowBack, degrees(h), h)
            t.startsWith("cabeza: girar a la derecha") -> HintCue(Icons.Default.Face, Icons.AutoMirrored.Filled.ArrowForward, degrees(h), h)
            t.startsWith("cabeza: girar hasta el perfil") -> HintCue(Icons.Default.Face, Icons.Default.RotateRight, degrees(h)?.let { "+$it" }, h)
            t.startsWith("cabeza: girar más") -> HintCue(Icons.Default.Face, Icons.Default.Add, degrees(h), h)
            t.startsWith("cabeza: girar menos") -> HintCue(Icons.Default.Face, Icons.Default.Remove, degrees(h), h)
            t.startsWith("cabeza: corregir giro") -> HintCue(Icons.Default.Face, Icons.Default.SwapHoriz, degrees(h), h)
            t.startsWith("cara: mirar a la") -> HintCue(Icons.Default.Face,
                if ("derecha" in t) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, null, h)
            // inclinada a la derecha → girar a la izquierda (antihorario), y viceversa
            t.startsWith("cabeza: enderezar") -> HintCue(Icons.Default.Face,
                if ("derecha" in t) Icons.Default.RotateLeft else Icons.Default.RotateRight, degrees(h), h)
            // barbilla (cabeceo, plano de Frankfort)
            t.startsWith("barbilla: bajar") -> HintCue(Icons.Default.Face, Icons.Default.ArrowDownward, degrees(h), h)
            t.startsWith("barbilla: subir") -> HintCue(Icons.Default.Face, Icons.Default.ArrowUpward, degrees(h), h)
            // expresión
            t.startsWith("expresión: abrir los ojos") -> HintCue(Icons.Default.Visibility, Icons.Default.Add, null, h)
            t.startsWith("expresión: entrecerrar") -> HintCue(Icons.Default.Visibility, Icons.Default.Remove, null, h)
            t.startsWith("expresión: relajar la sonrisa") -> HintCue(Icons.Default.SentimentNeutral, null, null, h)
            t.startsWith("expresión: sonreír") -> HintCue(Icons.Default.SentimentSatisfied, null, null, h)
            t.startsWith("expresión: juntar los labios") -> HintCue(Icons.Default.Face, Icons.Default.Compress, null, h)
            t.startsWith("expresión: separar los labios") -> HintCue(Icons.Default.Face, Icons.Default.Expand, null, h)
            t.startsWith("expresión: relajar las cejas") -> HintCue(Icons.Default.Face, Icons.Default.ArrowDownward, null, h)
            t.startsWith("expresión: subir las cejas") -> HintCue(Icons.Default.Face, Icons.Default.ArrowUpward, null, h)
            // mirada
            t.startsWith("mirada") -> HintCue(Icons.Default.Visibility, when {
                "izquierda" in t -> Icons.AutoMirrored.Filled.ArrowBack
                "derecha" in t -> Icons.AutoMirrored.Filled.ArrowForward
                "arriba" in t -> Icons.Default.ArrowUpward
                "abajo" in t -> Icons.Default.ArrowDownward
                else -> Icons.Default.CenterFocusStrong
            }, null, h)
            // luz
            t.startsWith("luz desigual") -> HintCue(Icons.Default.Contrast,
                if ("izquierdo" in t) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward, null, h)
            t.startsWith("poca luz") -> HintCue(Icons.Default.BrightnessLow, Icons.Default.Add, null, h)
            t.startsWith("demasiada luz") -> HintCue(Icons.Default.FlashOff, null, null, h)
            // ángulo de la cámara (vistas del cuero cabelludo)
            t.startsWith("cámara: apuntar más abajo") -> HintCue(Icons.Default.PhotoCamera, Icons.Default.ArrowDownward, degrees(h), h)
            t.startsWith("cámara: apuntar más arriba") -> HintCue(Icons.Default.PhotoCamera, Icons.Default.ArrowUpward, degrees(h), h)
            // cámara: perspectiva vertical
            t.startsWith("cámara: inclinar hacia abajo") -> HintCue(Icons.Default.PhotoCamera, Icons.Default.ArrowDownward, null, h)
            t.startsWith("cámara: inclinar hacia arriba") -> HintCue(Icons.Default.PhotoCamera, Icons.Default.ArrowUpward, null, h)
            // distancia y zoom
            t.startsWith("distancia:") -> HintCue(Icons.Default.SocialDistance,
                if (Regex("(\\d+) cm → (\\d+)").find(h)?.groupValues?.let { it[1].toInt() > it[2].toInt() } == true) Icons.Default.ZoomIn else Icons.Default.ZoomOut,
                Regex("(\\d+) cm → (\\d+) cm").find(h)?.let { "${it.groupValues[1]}→${it.groupValues[2]} cm" }, h)
            t.startsWith("≈") && "aléjese" in t -> HintCue(Icons.Default.SocialDistance, Icons.Default.ZoomIn,
                Regex("≈ ?(\\d+) ?cm").find(h)?.groupValues?.get(1)?.let { "$it cm → 1 m" }, h)
            t.startsWith("≈") -> HintCue(Icons.Default.Straighten, null, Regex("(\\d+) ?cm").find(h)?.groupValues?.get(1)?.let { "$it cm" }, h)
            t.startsWith("zoom") -> HintCue(Icons.Default.ZoomIn, null, Regex("([\\d.,]+) ?×").find(h)?.groupValues?.get(1)?.let { "$it×" }, h)
            // detección
            t.startsWith("no se detecta el rostro") -> HintCue(Icons.Default.FaceRetouchingOff, null, null, h)
            t.startsWith("no se detecta el cuerpo") -> HintCue(Icons.Default.PersonSearch, null, null, h)
            t.startsWith("detector") -> HintCue(Icons.Default.ErrorOutline, null, null, h)
            else -> null
        }
    }
}

/**
 * Traducción de las indicaciones al mostrarlas. Las indicaciones se generan en español (en `align/`)
 * y [HintCues] las analiza por su texto; aquí solo se traducen para pintarlas o anunciarlas, sin
 * cambiar el texto interno. En español devuelve el mismo texto.
 */
object HintText {
    /** Indicaciones fijas (sin datos). */
    private val FIXED = setOf(
        trn("Perfil: alinee la oreja y el plano de Frankfort con la guía"),
        trn("No se detecta el rostro del paciente"),
        trn("No se detecta el cuerpo del paciente"),
        trn("Mantenga el teléfono quieto"),
        trn("Encuadre la zona dentro de la guía"),
        trn("Mirada: al frente"),
        trn("Luz desigual: lado izquierdo más oscuro"),
        trn("Luz desigual: lado derecho más oscuro"),
        trn("Poca luz en el rostro"),
        trn("Demasiada luz: sin flash"),
        trn("Buscando referencias anatómicas…"),
        trn("← Mueva la cámara a la izquierda"),
        trn("Mueva la cámara a la derecha →"),
        trn("↑ Suba la cámara"),
        trn("↓ Baje la cámara"),
        trn("Acérquese"),
        trn("Aléjese"),
        trn("Gire la cámara ↺"),
        trn("Gire la cámara ↻"),
        trn("Cámara: inclinar hacia abajo"),
        trn("Cámara: inclinar hacia arriba"),
        trn("Cara: mirar a la derecha de la imagen"),
        trn("Cara: mirar a la izquierda de la imagen"),
        trn("Expresión: abrir los ojos"),
        trn("Expresión: entrecerrar los ojos como en la referencia"),
        trn("Expresión: relajar la sonrisa"),
        trn("Expresión: sonreír como en la referencia"),
        trn("Expresión: juntar los labios"),
        trn("Expresión: separar los labios como en la referencia"),
        trn("Expresión: relajar las cejas"),
        trn("Expresión: subir las cejas como en la referencia"),
        trn("Expresión: subir más las cejas"),
        trn("Expresión: sonreír al máximo"),
        trn("Expresión: apretar los ojos"),
        trn("Expresión: fruncir más los labios"),
        trn("Expresión: fruncir más el ceño (bajar las cejas)"),
        trn("Expresión: bajar las comisuras de la boca"),
    )

    /** Partes de "Mirada: … y …". */
    private val GAZE_PARTS = setOf(trn("más a la izquierda"), trn("más a la derecha"), trn("más arriba"), trn("más abajo"))

    private const val N = "(-?[\\d.,]+)"
    private val R_AIM_DOWN = Regex("^Cámara: apuntar más abajo $N°$")
    private val R_AIM_UP = Regex("^Cámara: apuntar más arriba $N°$")
    private val R_PROFILE = Regex("^Cabeza: girar hasta el perfil \\(faltan $N°\\)$")
    private val R_TURN_LEFT = Regex("^Cabeza: girar a la izquierda $N°$")
    private val R_TURN_RIGHT = Regex("^Cabeza: girar a la derecha $N°$")
    private val R_TURN_MORE = Regex("^Cabeza: girar más \\($N° de 45°\\)$")
    private val R_TURN_LESS = Regex("^Cabeza: girar menos \\($N° de 45°\\)$")
    private val R_YAW = Regex("^Cabeza: corregir giro $N°$")
    private val R_CHIN_DOWN_F = Regex("^Barbilla: bajar $N° \\(Frankfort\\)$")
    private val R_CHIN_UP_F = Regex("^Barbilla: subir $N° \\(Frankfort\\)$")
    private val R_CHIN_DOWN = Regex("^Barbilla: bajar $N°$")
    private val R_CHIN_UP = Regex("^Barbilla: subir $N°$")
    private val R_ROLL_RIGHT = Regex("^Cabeza: enderezar $N° \\(inclinada a la derecha\\)$")
    private val R_ROLL_LEFT = Regex("^Cabeza: enderezar $N° \\(inclinada a la izquierda\\)$")
    private val R_DISTANCE = Regex("^Distancia: $N cm → $N cm$")
    private val R_TOO_CLOSE = Regex("^≈ $N cm: aléjese ≥ 1 m y use zoom$")
    private val R_ABOUT = Regex("^≈ $N cm$")
    private val R_ZOOM = Regex("^zoom asistido $N×$")

    fun display(h: String): String {
        if (h in FIXED) return tr(h)
        fun m(r: Regex) = r.find(h)?.groupValues
        m(R_AIM_DOWN)?.let { return tr("Cámara: apuntar más abajo %1\$s°", it[1]) }
        m(R_AIM_UP)?.let { return tr("Cámara: apuntar más arriba %1\$s°", it[1]) }
        m(R_PROFILE)?.let { return tr("Cabeza: girar hasta el perfil (faltan %1\$s°)", it[1]) }
        m(R_TURN_LEFT)?.let { return tr("Cabeza: girar a la izquierda %1\$s°", it[1]) }
        m(R_TURN_RIGHT)?.let { return tr("Cabeza: girar a la derecha %1\$s°", it[1]) }
        m(R_TURN_MORE)?.let { return tr("Cabeza: girar más (%1\$s° de 45°)", it[1]) }
        m(R_TURN_LESS)?.let { return tr("Cabeza: girar menos (%1\$s° de 45°)", it[1]) }
        m(R_YAW)?.let { return tr("Cabeza: corregir giro %1\$s°", it[1]) }
        m(R_CHIN_DOWN_F)?.let { return tr("Barbilla: bajar %1\$s° (Frankfort)", it[1]) }
        m(R_CHIN_UP_F)?.let { return tr("Barbilla: subir %1\$s° (Frankfort)", it[1]) }
        m(R_CHIN_DOWN)?.let { return tr("Barbilla: bajar %1\$s°", it[1]) }
        m(R_CHIN_UP)?.let { return tr("Barbilla: subir %1\$s°", it[1]) }
        m(R_ROLL_RIGHT)?.let { return tr("Cabeza: enderezar %1\$s° (inclinada a la derecha)", it[1]) }
        m(R_ROLL_LEFT)?.let { return tr("Cabeza: enderezar %1\$s° (inclinada a la izquierda)", it[1]) }
        m(R_DISTANCE)?.let { return tr("Distancia: %1\$s cm → %2\$s cm", it[1], it[2]) }
        m(R_TOO_CLOSE)?.let { return tr("≈ %1\$s cm: aléjese ≥ 1 m y use zoom", it[1]) }
        m(R_ABOUT)?.let { return h }
        m(R_ZOOM)?.let { return tr("zoom asistido %1\$s×", it[1]) }
        if (h.startsWith("Mirada: ")) {
            val parts = h.removePrefix("Mirada: ").split(" y ")
            if (parts.all { it in GAZE_PARTS }) return when (parts.size) {
                1 -> tr("Mirada: %1\$s", tr(parts[0]))
                else -> tr("Mirada: %1\$s y %2\$s", tr(parts[0]), tr(parts[1]))
            }
        }
        // "Expresión: <indicación de la expresión pedida>" (ya traducida en FacialExpression o en español)
        if (h.startsWith("Expresión: ")) return tr("Expresión: %1\$s", tr(h.removePrefix("Expresión: ")))
        if (h.startsWith("Detector (pelo y piel): ")) {
            val rest = h.removePrefix("Detector (pelo y piel): ")
            return tr("Detector (pelo y piel): %1\$s", if (rest == "no disponible") tr("no disponible") else rest)
        }
        return h
    }
}
