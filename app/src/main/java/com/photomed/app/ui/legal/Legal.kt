package com.photomed.app.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.photomed.app.BuildConfig
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn
import com.photomed.app.ui.components.Avatar
import com.photomed.app.ui.theme.Md3Spacing

const val AUTHOR = "Juan Medina"
const val AUTHOR_EMAIL = "juanfrank11@gmail.com"

enum class LegalDoc(private val titleEs: String, val asset: String) {
    PRIVACY(trn("Política de privacidad"), "legal/PRIVACY.md"),
    TERMS(trn("Términos y condiciones"), "legal/TERMS.md"),
    LICENSE(trn("Licencia comercial (de pago)"), "legal/LICENSE.md"),
    CHANGELOG(trn("Novedades (registro de cambios)"), "legal/CHANGELOG.md");

    /** Título en el idioma de la interfaz. */
    val title: String get() = tr(titleEs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onDoc: (LegalDoc) -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Acerca de y legal")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Atrás")) } },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(Md3Spacing.s6), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.photomed.app.R.drawable.logo_app), tr("Logo de PhotoMed"), Modifier.size(72.dp))
                Spacer(Modifier.size(Md3Spacing.s4))
                Text("PhotoMed", style = MaterialTheme.typography.headlineSmall)
                Text(tr("Versión %1\$s", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(Md3Spacing.s2))
                Text(
                    tr("Fotografía clínica estandarizada para estética médica."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(AUTHOR) },
                supportingContent = { Text(AUTHOR_EMAIL) },
                overlineContent = { Text(tr("Autor")) },
                leadingContent = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.clickableItem {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$AUTHOR_EMAIL")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "PhotoMed")
                    })
                },
            )
            LegalDoc.entries.forEach { doc ->
                ListItem(
                    headlineContent = { Text(doc.title) },
                    leadingContent = {
                        Icon(when (doc) {
                            LegalDoc.PRIVACY -> Icons.Outlined.PrivacyTip
                            LegalDoc.TERMS -> Icons.Outlined.Gavel
                            LegalDoc.LICENSE -> Icons.Outlined.Description
                            LegalDoc.CHANGELOG -> Icons.Outlined.NewReleases
                        }, null)
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickableItem { onDoc(doc) },
                )
            }
            ListItem(
                overlineContent = { Text(tr("Diseño")) },
                headlineContent = { Text("Material Design 3") },
                supportingContent = { Text(tr("Tokens de color, tipografía, forma, elevación y movimiento de Juanfrank/MD (@material/web v0_192).")) },
                leadingContent = { Icon(Icons.Outlined.Palette, null) },
            )
            ListItem(
                overlineContent = { Text(tr("Ilustraciones anatómicas")) },
                headlineContent = { Text("Open Pain Illustration — Digital Health Zurich (CC BY 4.0)") },
                supportingContent = {
                    Text(tr("Universidad de Zúrich; JMIR Med Inform 2026;e75737. Adaptadas: recorte por zona, " +
                        "extracción y recoloreado de trazos; vistas oblicua 45° y lateral derivadas."))
                },
                leadingContent = { Icon(Icons.Outlined.Palette, null) },
                modifier = Modifier.clickableItem {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by/4.0/")))
                },
            )
            ListItem(
                overlineContent = { Text(tr("Componentes de terceros")) },
                headlineContent = { Text(tr("AndroidX, Jetpack Compose, CameraX, ExifInterface (Apache 2.0), Coil (Apache 2.0), MediaPipe Tasks + modelos Face Landmarker y Selfie Multiclass (Apache 2.0), Google ML Kit (Términos de ML Kit)")) },
            )
        }
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit) = this.clickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocScreen(doc: LegalDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    // traducción del documento en el idioma de la interfaz (legal/<idioma>/…) si existe; si no, el original en español
    val blocks = remember(doc) {
        val lang = com.photomed.app.i18n.I18n.locale.language
        val translated = "legal/$lang/" + doc.asset.removePrefix("legal/")
        val text = (if (lang != "es") runCatching { context.assets.open(translated) }.getOrNull() else null)
            ?: context.assets.open(doc.asset)
        parseMarkdown(text.bufferedReader().use { it.readText() })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Atrás")) } },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Md3Spacing.marginCompact, Md3Spacing.s2, Md3Spacing.marginCompact, Md3Spacing.s8),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
        ) {
            items(blocks) { MarkdownBlockView(it) }
        }
    }
}

/**
 * Aceptación inicial (primer uso): resumen, enlaces a los documentos y casilla de aceptación.
 */
@Composable
fun TermsGate(onAccept: () -> Unit) {
    var open by remember { mutableStateOf<LegalDoc?>(null) }
    var checked by rememberSaveable { mutableStateOf(false) }
    open?.let { doc ->
        LegalDocScreen(doc, onBack = { open = null })
        return
    }
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(Md3Spacing.s6),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s4),
        ) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.photomed.app.R.drawable.logo_app), tr("Logo de PhotoMed"), Modifier.size(64.dp))
            Text(tr("Bienvenido a PhotoMed"), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            Text(
                tr("Antes de empezar, revise cómo se tratan las fotografías y los datos de sus pacientes:"),
                style = MaterialTheme.typography.bodyLarge,
            )
            listOf(
                tr("Todo se guarda en este teléfono; no hay servidores ni cuentas."),
                tr("Las fotos aparecen en la galería, en un álbum por paciente, con sus datos incrustados como metadatos."),
                tr("Usted es responsable de obtener el consentimiento informado y de cumplir la normativa de datos de salud."),
                tr("Las guías y la alineación automática son ayudas visuales; no es un dispositivo médico."),
            ).forEach { line ->
                Row { Text("•  ", style = MaterialTheme.typography.bodyMedium); Text(line, style = MaterialTheme.typography.bodyMedium) }
            }
            Column {
                LegalDoc.entries.forEach { doc ->
                    TextButton(onClick = { open = doc }) { Text(tr("Leer: %1\$s", doc.title)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { checked = it }.padding(vertical = Md3Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(Md3Spacing.s3))
                Text(tr("He leído y acepto los Términos y condiciones y la Política de privacidad."),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text(tr("Aceptar y continuar")) }
            Text("$AUTHOR · $AUTHOR_EMAIL", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Markdown mínimo (títulos, listas, tablas, negrita, código) ----------

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String) : MdBlock
    data class TableRow(val cells: List<String>, val header: Boolean) : MdBlock
}

fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim()); para.clear() }
    var tableRows = 0
    for (raw in src.lines()) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> { flush(); tableRows = 0 }
            line.startsWith("#") -> { flush(); out += MdBlock.Heading(line.takeWhile { it == '#' }.length, line.trimStart('#').trim()) }
            line.startsWith("|") -> {
                flush()
                val cells = line.trim('|').split("|").map { it.trim() }
                if (cells.all { it.matches(Regex("-{3,}:?|:?-{3,}")) }) continue
                out += MdBlock.TableRow(cells, header = tableRows == 0)
                tableRows++
            }
            Regex("^\\s*([-*]|\\d+\\.)\\s+.*").matches(line) -> {
                flush()
                val m = Regex("^\\s*([-*]|\\d+\\.)\\s+(.*)").find(line)!!
                out += MdBlock.Bullet(if (m.groupValues[1].first().isDigit()) m.groupValues[1] else "•", m.groupValues[2])
            }
            else -> {
                if (out.lastOrNull() is MdBlock.Bullet && raw.startsWith("  ") && para.isEmpty()) {
                    val b = out.removeAt(out.lastIndex) as MdBlock.Bullet
                    out += b.copy(text = b.text + " " + line.trim())
                } else para.append(line.trim()).append(' ')
            }
        }
    }
    flush()
    return out
}

@Composable
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*|`(.+?)`|<(https?://[^>]+)>")
    var last = 0
    for (m in regex.findAll(text)) {
        append(text.substring(last, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
            else -> withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(m.groupValues[3]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun MarkdownBlockView(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            block.text,
            style = when (block.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium },
            modifier = Modifier.padding(top = if (block.level == 1) 0.dp else Md3Spacing.s3).semantics { heading() },
        )
        is MdBlock.Paragraph -> Text(inline(block.text), style = MaterialTheme.typography.bodyMedium)
        is MdBlock.Bullet -> Row(Modifier.padding(start = Md3Spacing.s2)) {
            Text(block.marker + "  ", style = MaterialTheme.typography.bodyMedium)
            Text(inline(block.text), style = MaterialTheme.typography.bodyMedium)
        }
        is MdBlock.TableRow -> Column {
            Row(Modifier.fillMaxWidth().padding(vertical = Md3Spacing.s1)) {
                block.cells.forEach { c ->
                    Text(inline(c), style = if (block.header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(end = Md3Spacing.s2))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
