package com.photomed.app.ui.camera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Contrast
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
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Indicación como icono: [icon] (qué: cabeza, barbilla, mirada, luz, cámara, distancia…), [badge]
 * (hacia dónde: flecha, giro, más/menos) y, si hace falta, un valor corto ([value], p. ej. "8°").
 * [description] es el texto completo, solo para TalkBack.
 */
data class HintCue(val icon: ImageVector, val badge: ImageVector?, val value: String?, val description: String)

object HintCues {
    private val deg = Regex("(\\d+)°")
    private fun degrees(h: String) = deg.find(h)?.groupValues?.get(1)?.let { "$it°" }

    /** Icono para una indicación de texto; null si no tiene equivalente (se muestra el texto). */
    fun of(h: String): HintCue? {
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
            // cámara: perspectiva vertical
            t.startsWith("cámara: inclinar hacia abajo") -> HintCue(Icons.Default.PhotoCamera, Icons.Default.ArrowDownward, null, h)
            t.startsWith("cámara: inclinar hacia arriba") -> HintCue(Icons.Default.PhotoCamera, Icons.Default.ArrowUpward, null, h)
            // distancia y zoom
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
