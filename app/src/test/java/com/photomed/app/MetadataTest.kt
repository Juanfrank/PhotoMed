package com.photomed.app

import com.photomed.app.data.PhotoInfo
import com.photomed.app.data.PhotoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.rules.TemporaryFolder
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val info = PhotoInfo(
        patientName = "María José Peña & \"Hijos\"", recordNumber = "0012", expedienteId = "0012_Maria_Jose_Pena",
        zoneId = "labios", zoneLabel = "Labios / peribucal", capturedAt = 1_763_400_000_000, consent = true,
    )

    @Test
    fun xmpRoundTripKeepsUtf8AndEscaping() {
        val xmp = PhotoMetadata.xmp(info)
        assertTrue(xmp.contains("<dc:title>"))
        assertEquals(info, PhotoMetadata.parseXmp(xmp))
    }

    @Test
    fun writesReadsAndStripsJpegMetadata() {
        val f = File(tmp.root, "foto.jpg")
        f.writeBytes(javaClass.getResourceAsStream("/blank.jpg")!!.readBytes())
        PhotoMetadata.write(f, info)
        assertEquals(info, f.inputStream().use { PhotoMetadata.read(it) })
        PhotoMetadata.strip(f)
        assertNull(f.inputStream().use { PhotoMetadata.read(it) })
    }
}
