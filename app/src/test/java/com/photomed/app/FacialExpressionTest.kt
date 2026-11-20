package com.photomed.app

import com.photomed.app.align.Expression
import com.photomed.app.data.FacialExpression
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import com.photomed.app.data.expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FacialExpressionTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun expressionsPerZone() {
        val face = FacialExpression.forZone(Zone.FACE_FRONTAL)
        assertTrue(face.containsAll(listOf(FacialExpression.NEUTRAL, FacialExpression.SURPRISE, FacialExpression.FROWN, FacialExpression.SMILE,
            FacialExpression.SQUINT, FacialExpression.NOSE, FacialExpression.PUCKER, FacialExpression.SAD, FacialExpression.CHIN)))
        assertEquals(listOf(FacialExpression.NEUTRAL, FacialExpression.SURPRISE, FacialExpression.FROWN), FacialExpression.forZone(Zone.FOREHEAD))
        assertEquals(listOf(FacialExpression.NEUTRAL, FacialExpression.NECK), FacialExpression.forZone(Zone.NECK))
        assertEquals(listOf(FacialExpression.NEUTRAL), FacialExpression.forZone(Zone.HANDS_DORSAL))
    }

    @Test fun savedNameKeepsZoneDateAndExpression() {
        val repo = PhotoRepository(File(tmp.root, "p"), FilePhotoStore(File(tmp.root, "g")), openStream = { File(it.path!!).inputStream() })
        val exp = repo.createExpediente("Paciente", "1", "", consent = true)
        val jpeg = File(tmp.root, "c.jpg").apply { writeBytes(this@FacialExpressionTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        val t = 1_763_400_000_000L
        val a = repo.savePhoto(exp, Zone.FACE_FRONTAL, jpeg, takenAt = t, variant = FacialExpression.variantTag(FacialExpression.SQUINT))
        val b = repo.savePhoto(exp, Zone.FACE_FRONTAL, jpeg, takenAt = t + 1000, variant = FacialExpression.variantTag(FacialExpression.NEUTRAL))
        assertTrue(a.displayName, a.displayName.startsWith("rostro_frontal_exp-ojos_apretados_"))
        val list = repo.photos(exp, Zone.FACE_FRONTAL)
        assertEquals(2, list.size)
        assertEquals(FacialExpression.SQUINT, list.first { it.takenAt == t }.expression)
        assertEquals(FacialExpression.NEUTRAL, list.first { it.takenAt == t + 1000 }.expression)
        assertEquals(b.displayName, list.last().displayName)
    }

    /** Con una expresión pedida, se pide el gesto (y no relajarlo, como en reposo). */
    @Test fun liveHintsFollowTheChosenExpression() {
        val rest = Expression(eyes = 0.28f, mouthOpen = 0.02f, mouthWidth = 0.85f, smileLift = 0.0f, brows = 0.20f)
        val raised = rest.copy(brows = 0.31f)
        val smiling = rest.copy(mouthWidth = 1.05f, smileLift = 0.14f)
        val squeezed = rest.copy(eyes = 0.05f)
        assertTrue(Expression.target(FacialExpression.SURPRISE, rest).any { "cejas" in it })
        assertTrue(Expression.target(FacialExpression.SURPRISE, raised).isEmpty())
        assertTrue(Expression.target(FacialExpression.SMILE, rest).any { "sonreír" in it })
        assertTrue(Expression.target(FacialExpression.SMILE, smiling).isEmpty())
        assertTrue(Expression.target(FacialExpression.SQUINT, rest).any { "apretar" in it })
        assertTrue(Expression.target(FacialExpression.SQUINT, squeezed).isEmpty())
        // en reposo, la sonrisa y las cejas elevadas se piden relajar
        assertTrue(Expression.neutral(Zone.FACE_FRONTAL, smiling).any { "relajar la sonrisa" in it })
        assertTrue(Expression.neutral(Zone.FACE_FRONTAL, raised).any { "relajar las cejas" in it })
    }
}
