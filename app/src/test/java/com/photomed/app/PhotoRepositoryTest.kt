package com.photomed.app

import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PhotoRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val gallery get() = File(tmp.root, "Pictures/PhotoMed")

    private fun repo() = PhotoRepository(
        File(tmp.root, "private"), FilePhotoStore(gallery),
        openStream = { uri -> File(uri.path!!).inputStream() },
    )

    private fun jpeg(name: String = "c.jpg"): File = File(tmp.root, name).also {
        it.writeBytes(javaClass.getResourceAsStream("/blank.jpg")!!.readBytes())
    }

    @Test
    fun sanitizeAndZoneParsing() {
        assertEquals("0012_Maria_Jose_Pena", PhotoRepository.sanitize(" 0012 María José Peña "))
        assertEquals(Zone.FACE_PROFILE_R, PhotoRepository.zoneOf("rostro_perfil_der_20261118_101530_123.jpg"))
        assertEquals(Zone.FACE_FRONTAL, PhotoRepository.zoneOf("rostro_frontal_20261118_101530_123.jpg"))
        assertEquals(Zone.HANDS_PALMAR, PhotoRepository.zoneOf("manos_palma_20261118_101530_123.jpg"))
        assertEquals(Zone.FEET_DORSAL, PhotoRepository.zoneOf("pies_dorso_20261118_101530_123.jpg"))
    }

    @Test
    fun photosGoToPatientAlbumWithMetadata() {
        val r = repo()
        val exp = r.createExpediente("Ana Pérez", "001", "Toxina", consent = true)
        assertEquals("001_Ana_Perez", exp.id)
        val p1 = r.savePhoto(exp, Zone.LIPS, jpeg(), takenAt = 1_000_000L)
        val p2 = r.savePhoto(exp, Zone.LIPS, jpeg(), takenAt = 2_000_000L)
        r.savePhoto(exp, Zone.FACE_FRONTAL, jpeg(), takenAt = 1_500_000L)

        // Álbum por paciente, sin subcarpetas
        val files = File(gallery, "001_Ana_Perez").listFiles()!!.map { it.name }.sorted()
        assertEquals(3, files.size)
        assertTrue(files.all { it.endsWith(".jpg") })

        assertEquals(listOf(p1.key, p2.key), r.photos(exp, Zone.LIPS).map { it.key })
        assertEquals(3, r.getExpediente(exp.id)!!.photoCount)

        val info = r.readInfo(p1)!!
        assertEquals("Ana Pérez", info.patientName)
        assertEquals("001", info.recordNumber)
        assertEquals("labios", info.zoneId)
        assertTrue(info.consent)
    }

    @Test
    fun recoversExpedienteFromGalleryAfterReinstall() {
        val r = repo()
        val exp = r.createExpediente("Luis Gómez", "77", "", consent = false)
        r.savePhoto(exp, Zone.NECK, jpeg())
        File(tmp.root, "private").deleteRecursively() // reinstalación: se pierden los datos privados

        val fresh = repo()
        assertEquals(0, fresh.listExpedientes().size)
        assertEquals(1, fresh.recoverFromGallery())
        val rec = fresh.listExpedientes().single()
        assertEquals("Luis Gómez", rec.patientName)
        assertEquals("77", rec.recordNumber)
        assertEquals(1, rec.photoCount)
    }

    @Test
    fun migratesLegacyPrivatePhotosAndDeletes() {
        val legacy = File(tmp.root, "legacy")
        val old = File(legacy, "001_Ana/labios").apply { mkdirs() }
        File(legacy, "001_Ana/expediente.json").writeText("""{"patientName":"Ana","recordNumber":"001","notes":"","createdAt":1}""")
        jpeg().copyTo(File(old, "labios_20250101_100000_000.jpg"))
        val r = repo()
        r.migrateLegacy(legacy)
        assertFalse(legacy.resolve("001_Ana").exists())
        val exp = r.getExpediente("001_Ana")!!
        val photos = r.photos(exp)
        assertEquals(1, photos.size)
        assertEquals("Ana", File(photos[0].uri.path!!).inputStream().use { PhotoMetadata.read(it) }!!.patientName)

        r.deleteExpediente(exp)
        assertEquals(0, r.listExpedientes().size)
        assertEquals(0, File(gallery, "001_Ana").listFiles()!!.size)
    }
}
