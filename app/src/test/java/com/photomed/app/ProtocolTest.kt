package com.photomed.app

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.Pt
import com.photomed.app.data.Expediente
import com.photomed.app.data.FacialExpression
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Protocol
import com.photomed.app.data.ProtocolJson
import com.photomed.app.data.ProtocolRef
import com.photomed.app.data.ProtocolResults
import com.photomed.app.data.ProtocolStep
import com.photomed.app.data.Protocols
import com.photomed.app.data.RefLine
import com.photomed.app.data.Zone
import com.photomed.app.data.expression
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.theme.PhotoMedTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile

/** Protocolos de fotos: formato, resultados de ida y vuelta, editor y sección del expediente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProtocolTest {
    @get:Rule(order = 0) val tmp = TemporaryFolder()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var repo: PhotoRepository
    private lateinit var exp: Expediente

    @Before
    fun setUp() {
        coil.Coil.reset()
        com.photomed.app.ui.components.CoachMarks.enabled = false
        repo = repoAt("a")
        exp = repo.createExpediente("Paciente Prueba", "900", "", consent = true)
        Protocols.list(ctx).forEach { Protocols.delete(ctx, it.id) }
    }

    private fun repoAt(name: String) = PhotoRepository(File(tmp.root, "$name/private"), FilePhotoStore(File(tmp.root, "$name/Pictures/PhotoMed")),
        openStream = { uri -> File(uri.path!!).inputStream() })

    private val sample = Protocol(name = "Toxina: control a 15 días", author = "Dra. Prueba", notes = "Sin maquillaje", steps = listOf(
        ProtocolStep(zone = Zone.FACE_FRONTAL, count = 2, threshold = 0.9f, autoCapture = true, minLight = 0.4f, maxLightAsym = 0.15f,
            ref = ProtocolRef(mapOf("eye_l" to Pt(0.6f, 0.4f), "eye_r" to Pt(0.4f, 0.4f)), yaw = 1.5f, pitch = -2f, roll = 0.3f,
                hairline = listOf(Pt(0.3f, 0.2f), Pt(0.7f, 0.2f)),
                lines = listOf(RefLine("FOLDS", "Nasogeniano derecho", listOf(Pt(0.42f, 0.6f), Pt(0.4f, 0.7f)))), distanceCm = 110f, zoom = 2f,
                sourceTakenAt = 1_763_400_000_000L)),
        ProtocolStep(zone = Zone.FOREHEAD, expression = FacialExpression.SURPRISE, instructions = "Cejas arriba al máximo", lens = "back")))

    @Test
    fun jsonRoundTripAndErrors() {
        val back = ProtocolJson.read(ProtocolJson.write(sample))
        assertEquals(sample, back)
        assertEquals(3, back.totalShots)
        // archivo que no es un protocolo, versión futura y zona desconocida: mensajes claros
        fun err(json: String) = runCatching { ProtocolJson.read(json) }.exceptionOrNull()?.message.orEmpty()
        assertTrue(err("{}").contains("no es un protocolo"))
        assertTrue(err("no json").contains("no es un protocolo"))
        assertTrue(err("""{"format":"photomed.protocol","version":99,"steps":[]}""").contains("versión más nueva"))
        assertTrue(err("""{"format":"photomed.protocol","version":1,"steps":[{"zone":"xx"}]}""").contains("zona desconocida"))
    }

    @Test
    fun resultsGoBackWithThePhotos() {
        Protocols.save(ctx, sample)
        val jpeg = File(tmp.root, "c.jpg").apply { writeBytes(this@ProtocolTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        // dos tomas del protocolo (paso 1 foto 1 y paso 2 foto 1) y una foto ajena
        repo.savePhoto(exp, Zone.FACE_FRONTAL, jpeg, takenAt = 1_763_400_000_000L,
            conditions = ProtocolResults.conditions(sample, 0, 1) + ("MatchPercent" to "93") + ("LightFace" to "0.55"))
        repo.savePhoto(exp, Zone.FOREHEAD, jpeg, takenAt = 1_763_400_100_000L, variant = FacialExpression.variantTag(FacialExpression.SURPRISE),
            conditions = ProtocolResults.conditions(sample, 1, 1))
        repo.savePhoto(exp, Zone.LIPS, jpeg, takenAt = 1_763_400_200_000L)
        val zip = ProtocolResults.buildZip(ctx, repo, exp, sample, anonymize = true)
        ZipFile(zip).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue(names.toString(), "protocolo.photomed.json" in names && "resultados.json" in names)
            assertEquals(2, names.count { it.startsWith("fotos/") })
            val res = JSONObject(z.getInputStream(z.getEntry("resultados.json")).readBytes().toString(Charsets.UTF_8))
            assertEquals(3, res.getInt("requested")); assertEquals(2, res.getInt("taken"))
            // sin datos del paciente
            assertTrue(!res.has("patient"))
            assertTrue(names.none { "Paciente" in it })
        assertEquals("Resultados_Toxina_control_a_15_dias.zip", zip.name)
        }
        // quien creó el protocolo importa los resultados en su expediente
        val repoB = repoAt("b")
        val expB = repoB.createExpediente("Paciente B", "1", "", consent = true)
        val r = ProtocolResults.importZip(ctx, repoB, expB, Uri.fromFile(zip))
        assertEquals(2, r.photos)
        assertEquals(sample.id, r.protocol?.id)
        val got = repoB.photos(expB)
        assertEquals(setOf(Zone.FACE_FRONTAL, Zone.FOREHEAD), got.map { it.zone }.toSet())
        assertEquals(FacialExpression.SURPRISE, got.first { it.zone == Zone.FOREHEAD }.expression)
        val info = repoB.readInfo(got.first { it.zone == Zone.FACE_FRONTAL })!!
        assertEquals("93", info.conditions["MatchPercent"]); assertEquals(sample.id, info.conditions[ProtocolResults.KEY_ID])
        assertEquals("Paciente B", info.patientName)
        // volver a importar no duplica
        assertEquals(2, ProtocolResults.importZip(ctx, repoB, expB, Uri.fromFile(zip)).duplicates)
        assertEquals(2, repoB.photos(expB).size)
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalRepository provides repo) { PhotoMedTheme(darkTheme = false) { content() } }
    }

    @Test
    fun editorCreatesAndSectionStarts() {
        var editing by androidx.compose.runtime.mutableStateOf(true)
        var started: Protocol? = null
        setContent {
            if (editing) com.photomed.app.ui.protocol.ProtocolEditorScreen(exp.id, null, onBack = {})
            else com.photomed.app.ui.protocol.ProtocolsSection(exp, androidx.compose.material3.SnackbarHostState(), 0,
                onStart = { started = it }, onEdit = { _, _ -> })
        }
        compose.onNodeWithText("Nombre del protocolo").performTextInput("Control facial")
        compose.onNodeWithText("Añadir toma").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Coincidencia mínima", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Guardar el protocolo").performClick()
        compose.waitForIdle()
        val saved = Protocols.list(ctx).single()
        assertEquals("Control facial", saved.name)
        assertEquals(1, saved.steps.size)
        // en el expediente: aparece y se inicia
        editing = false
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Control facial").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Iniciar el protocolo Control facial").performClick()
        assertEquals(saved.id, started?.id)
    }
}
