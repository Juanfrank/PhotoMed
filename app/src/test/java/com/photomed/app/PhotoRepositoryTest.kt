package com.photomed.app

import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun sanitizeRemovesAccentsAndSymbols() {
        assertEquals("0012_Maria_Jose_Pena", PhotoRepository.sanitize(" 0012 María José Peña "))
        assertEquals("a_b", PhotoRepository.sanitize("a/../b"))
    }

    @Test
    fun createsNamedFolderAndAvoidsCollisions() {
        val repo = PhotoRepository(tmp.root)
        val a = repo.createExpediente("Ana Pérez", "001", "Toxina")
        val b = repo.createExpediente("Ana Pérez", "001", "")
        assertEquals("001_Ana_Perez", a.id)
        assertEquals("001_Ana_Perez_2", b.id)
        assertEquals("Ana Pérez", repo.getExpediente(a.id)!!.patientName)
        assertEquals(2, repo.listExpedientes().size)

        repo.updateExpediente(a, "Ana P. Gómez", "001", "Relleno")
        assertEquals("Relleno", repo.getExpediente(a.id)!!.notes)

        repo.deleteExpediente(b)
        assertFalse(b.dir.exists())
        assertEquals(1, repo.listExpedientes().size)
    }

    @Test
    fun photosAreFiledByZoneAndSortedByDate() {
        val repo = PhotoRepository(tmp.root)
        val exp = repo.createExpediente("Luis", "", "")
        val f1 = repo.newPhotoFile(exp, Zone.LIPS)
        f1.writeBytes(byteArrayOf(1))
        Thread.sleep(5)
        val f2 = repo.newPhotoFile(exp, Zone.LIPS)
        f2.writeBytes(byteArrayOf(2))
        repo.newPhotoFile(exp, Zone.FACE_FRONTAL).writeBytes(byteArrayOf(3))

        assertTrue(f1.parentFile!!.name == "labios")
        val lips = repo.photos(exp, Zone.LIPS)
        assertEquals(listOf(f1, f2), lips.map { it.file })
        assertTrue(lips[0].takenAt < lips[1].takenAt)
        assertEquals(3, repo.photos(exp).size)
        assertEquals(3, repo.getExpediente(exp.id)!!.photoCount)
    }
}
