package com.photomed.app

import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.OrphanSweep
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** Tanda 4 de la revisión integral: escalabilidad y recursos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScaleBatchTest {
    @get:Rule val tmp = TemporaryFolder()
    private val gallery get() = File(tmp.root, "Pictures/PhotoMed")
    private fun repo() = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(gallery), openStream = { uri -> File(uri.path!!).inputStream() })
    private fun jpeg(name: String = "c.jpg"): File = File(tmp.root, name).also { it.writeBytes(javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
    private fun sha(u: String) = MessageDigest.getInstance("SHA-1").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Listado de expedientes con una sola consulta: mismos números que expediente a expediente. */
    @Test fun listingUsesOneQueryWithSameCounts() {
        val r = repo()
        val a = r.createExpediente("Ana", "1", "", true); val b = r.createExpediente("Luis", "2", "", true)
        r.savePhoto(a, Zone.LIPS, jpeg(), takenAt = 1_000L); r.savePhoto(a, Zone.FOREHEAD, jpeg(), takenAt = 2_000L)
        r.savePhoto(b, Zone.LIPS, jpeg(), takenAt = 3_000L)
        val list = r.listExpedientes().associateBy { it.id }
        assertEquals(2, list.getValue(a.id).photoCount); assertEquals(2_000L, list.getValue(a.id).lastPhotoAt)
        assertEquals(1, list.getValue(b.id).photoCount)
        assertEquals(r.photos(a).map { it.key }, r.photosByExpediente().getValue(a.id).map { it.key })
    }

    /** Borrar un expediente borra también sus exportaciones (antes quedaban en la galería). */
    @Test fun deletingAnExpedienteRemovesItsExports() {
        val r = repo()
        val a = r.createExpediente("Ana", "1", "", true)
        r.savePhoto(a, Zone.LIPS, jpeg(), takenAt = 1_000L)
        r.saveExport(a, jpeg("exp.jpg"), "comparacion.jpg")
        assertTrue(File(gallery, "${a.id}/${PhotoRepository.EXPORT_DIR}/comparacion.jpg").exists())
        r.deleteExpediente(a)
        assertFalse(File(gallery, "${a.id}/${PhotoRepository.EXPORT_DIR}/comparacion.jpg").exists())
    }

    /** Datos de fotos que ya no existen: se apartan, vuelven si la foto reaparece y se borran a los 30 días. */
    @Test fun orphanDataIsSetAsideThenDeleted() {
        val r = repo()
        val a = r.createExpediente("Ana", "1", "", true)
        val p = r.savePhoto(a, Zone.LIPS, jpeg(), takenAt = 1_000L)
        val files = File(tmp.root, "files").apply { mkdirs() }
        File(files, "expedientes").mkdirs(); File(files, "expedientes/x").mkdirs()
        val live = File(files, "trazos/${sha(p.uri.toString())}.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val gone = File(files, "relieve/${sha("content://borrada")}.png").apply { parentFile!!.mkdirs(); writeText("x") }
        val now = 10_000_000_000L
        assertEquals(1, OrphanSweep.sweep(files, r.photosByExpediente(), now))
        assertTrue(live.exists()); assertFalse(gone.exists())
        val aside = File(files, "huerfanos/relieve/${gone.name}")
        assertTrue(aside.exists())
        // a los 31 días, borrado
        OrphanSweep.sweep(files, r.photosByExpediente(), now + 31L * 24 * 3600_000L)
        assertFalse(aside.exists())
        // galería vacía con expedientes: consulta sospechosa, no se aparta nada
        assertEquals(0, OrphanSweep.sweep(files, emptyMap(), now))
        assertTrue(live.exists())
    }
}
