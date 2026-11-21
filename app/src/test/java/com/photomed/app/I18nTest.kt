package com.photomed.app

import androidx.test.core.app.ApplicationProvider
import com.photomed.app.i18n.I18n
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Internacionalización: claves iguales a las del extractor, inglés traducido y español intacto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class I18nTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun reset() = I18n.init(ctx)

    @Test
    fun keysMatchTheExtractor() {
        // calculadas con tools/i18n/extract.py (fnv_key)
        assertEquals("t_4acd99878e19", I18n.key("Expedientes"))
        assertEquals("t_814b9f1d0815", I18n.key("Foto %1\$d de %2\$d"))
        assertEquals("t_815ee15e8d98", I18n.key("Según el sistema"))
    }

    @Test
    @Config(qualifiers = "en")
    fun englishInterface() {
        I18n.init(ctx)
        assertEquals("Patient records", tr("Expedientes"))
        assertEquals("System default", tr("Según el sistema"))
        // datos guardados en español: se traducen al mostrarlos, por fragmentos
        assertEquals("Crow's feet right 2", trData("Pata de gallo derecha 2"))
        // sin traducción: el texto de origen
        assertEquals("Texto sin catálogo", tr("Texto sin catálogo"))
    }

    @Test
    fun spanishIsUnchanged() {
        I18n.init(ctx)
        assertEquals("Expedientes", tr("Expedientes"))
        assertEquals("Foto 2 de 3", tr("Foto %1\$d de %2\$d", 2, 3))
        assertEquals("Pata de gallo derecha 2", trData("Pata de gallo derecha 2"))
    }
}
