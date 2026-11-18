package com.photomed.app

import com.photomed.app.ui.camera.HintCues
import com.photomed.app.ui.camera.isCameraMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada indicación de la cámara tiene icono (o es un movimiento que indican las flechas). */
class HintCuesTest {
    private val hints = listOf(
        "Cabeza: girar a la izquierda 9°", "Cabeza: girar a la derecha 7°", "Cabeza: girar hasta el perfil (faltan 20°)",
        "Cabeza: girar más (30° de 45°)", "Cabeza: girar menos (55° de 45°)", "Cara: mirar a la derecha de la imagen",
        "Cabeza: corregir giro 8°", "Barbilla: bajar 8° (Frankfort)", "Barbilla: subir 7°", "Cabeza: enderezar 5° (inclinada a la derecha)",
        "Mirada: al frente", "Mirada: más a la izquierda", "Luz desigual: lado izquierdo más oscuro", "Poca luz en el rostro",
        "Demasiada luz: sin flash", "Cámara: inclinar hacia abajo", "Cámara: inclinar hacia arriba",
        "≈ 45 cm: aléjese ≥ 1 m y use zoom", "≈ 120 cm", "zoom asistido 1.3×",
        "No se detecta el rostro del paciente", "No se detecta el cuerpo del paciente", "Detector (ML Kit): IllegalStateException",
    )

    @Test
    fun everyHintHasAnIcon() {
        for (h in hints) assertNotNull(h, HintCues.of(h))
        for (m in listOf("← Mueva la cámara a la izquierda", "Mueva la cámara a la derecha →", "↑ Suba la cámara", "↓ Baje la cámara",
            "Acérquese", "Aléjese", "Gire la cámara ↺")) assertTrue(m, isCameraMove(m))
    }

    @Test
    fun valuesAreShort() {
        assertEquals("8°", HintCues.of("Barbilla: bajar 8° (Frankfort)")!!.value)
        assertEquals("45 cm → 1 m", HintCues.of("≈ 45 cm: aléjese ≥ 1 m y use zoom")!!.value)
        assertEquals("1.3×", HintCues.of("zoom asistido 1.3×")!!.value)
        assertTrue(hints.mapNotNull { HintCues.of(it)?.value }.all { it.length <= 12 })
    }
}
