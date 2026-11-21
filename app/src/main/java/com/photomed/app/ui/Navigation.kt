package com.photomed.app.ui

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.CameraScreen
import com.photomed.app.ui.compare.CompareScreen
import com.photomed.app.ui.expediente.ExpedienteScreen
import com.photomed.app.ui.expedientes.ExpedientesScreen
import com.photomed.app.ui.gallery.PhotoViewerScreen
import com.photomed.app.ui.gallery.ZoneGalleryScreen
import com.photomed.app.ui.legal.AboutScreen
import com.photomed.app.ui.legal.LegalDoc
import com.photomed.app.ui.legal.LegalDocScreen
import com.photomed.app.ui.legal.TermsGate
import com.photomed.app.ui.theme.Md3Motion

private const val PREFS = "photomed"
private const val KEY_TERMS = "terms_accepted_v1"

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var accepted by remember { mutableStateOf(prefs.getBoolean(KEY_TERMS, false)) }
    val nav = rememberNavController()

    if (!accepted) {
        TermsGate(
            onAccept = { prefs.edit().putBoolean(KEY_TERMS, true).apply(); accepted = true },
        )
        return
    }

    // Transiciones de eje X compartido (M3 motion: emphasized, medium4/short4).
    val enter = tween<androidx.compose.ui.unit.IntOffset>(Md3Motion.durationMedium4, easing = Md3Motion.easingEmphasizedDecelerate)
    val exit = tween<androidx.compose.ui.unit.IntOffset>(Md3Motion.durationShort4, easing = Md3Motion.easingEmphasizedAccelerate)
    // Regresar/navegar solo desde la pantalla visible (RESUMED): un doble toque o una pulsación
    // durante la animación de salida repetía popBackStack, vaciaba la pila y cerraba la app.
    fun NavBackStackEntry.back() {
        if (lifecycle.currentState == Lifecycle.State.RESUMED && nav.previousBackStackEntry != null) nav.popBackStack()
    }
    fun NavBackStackEntry.go(route: String) {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) nav.navigate(route) { launchSingleTop = true }
    }
    NavHost(
        navController = nav,
        startDestination = "expedientes",
        enterTransition = { slideInHorizontally(enter) { it / 5 } + fadeIn(tween(Md3Motion.durationMedium2)) },
        exitTransition = { slideOutHorizontally(exit) { -it / 5 } + fadeOut(tween(Md3Motion.durationShort4)) },
        popEnterTransition = { slideInHorizontally(enter) { -it / 5 } + fadeIn(tween(Md3Motion.durationMedium2)) },
        popExitTransition = { slideOutHorizontally(exit) { it / 5 } + fadeOut(tween(Md3Motion.durationShort4)) },
    ) {
        composable("expedientes") { entry ->
            HomeScreen(onOpen = { entry.go("expediente/$it") }, onAbout = { entry.go("about") },
                onSettings = { entry.go("settings") },
                // biblioteca de protocolos: editar sin expediente; aplicar = abrir la cámara del paciente elegido
                onEditProtocol = { id -> entry.go("protocol/-/$id") },
                onApplyProtocol = { expId, p -> p.steps.firstOrNull()?.let { s -> entry.go("camera/$expId/${s.zone.id}?protocol=${p.id}&step=0") } })
        }
        composable("expediente/{exp}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            ExpedienteScreen(
                expId = exp,
                onBack = { entry.back() },
                onZone = { zone -> entry.go("zone/$exp/${zone.id}") },
                onCompare = { entry.go("compare/$exp") },
                onSequence = { zones ->
                    if (zones.isNotEmpty()) entry.go("camera/$exp/${zones.first().id}?seq=" + zones.joinToString(",") { it.id })
                },
                // protocolo: la cámara recorre sus pasos en orden
                onProtocol = { p -> p.steps.firstOrNull()?.let { s -> entry.go("camera/$exp/${s.zone.id}?protocol=${p.id}&step=0") } },
                onEditProtocol = { id, from ->
                    entry.go("protocol/$exp/$id" + if (from.isEmpty()) "" else "?from=" + android.net.Uri.encode(from.joinToString("|")))
                },
            )
        }
        composable("zone/{exp}/{zone}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone")) ?: return@composable
            ZoneGalleryScreen(
                expId = exp,
                zone = zone,
                onBack = { entry.back() },
                onCamera = { entry.go("camera/$exp/${zone.id}") },
                onCompare = { names ->
                    val sel = if (names.isEmpty()) "" else "&sel=" + android.net.Uri.encode(names.joinToString("|"))
                    entry.go("compare/$exp?zone=${zone.id}$sel")
                },
                onPhoto = { photo -> entry.go("photo/$exp/${zone.id}/${photo.displayName}") },
            )
        }
        composable(
            "camera/{exp}/{zone}?seq={seq}&protocol={protocol}&step={step}",
            arguments = listOf(navArgument("seq") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("protocol") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("step") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone")) ?: return@composable
            val seqArg = entry.arguments?.getString("seq")
            val seq = seqArg?.split(',')?.mapNotNull { Zone.fromId(it) }.orEmpty()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val pid = entry.arguments?.getString("protocol")
            val protocol = androidx.compose.runtime.remember(pid) { pid?.let { com.photomed.app.data.Protocols.get(ctx, it) } }
            val stepIdx = entry.arguments?.getInt("step") ?: 0
            CameraScreen(expId = exp, zone = zone, onBack = { entry.back() }, sequence = seq,
                protocol = protocol, stepIndex = stepIdx,
                onProtocolNext = { next ->
                    // paso siguiente: su cámara sustituye a la actual; al terminar, vuelve
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        val s = next?.let { protocol?.steps?.getOrNull(it) }
                        if (s == null) entry.back()
                        else nav.navigate("camera/$exp/${s.zone.id}?protocol=$pid&step=$next") { popUpTo(entry.destination.id) { inclusive = true } }
                    }
                },
                onCompare = { names -> entry.go("compare/$exp?zone=${zone.id}&sel=" + android.net.Uri.encode(names.joinToString("|"))) },
                onNext = { next ->
                    // secuencia: la cámara de la zona siguiente sustituye a la actual; al final, vuelve
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        if (next == null) entry.back()
                        else nav.navigate("camera/$exp/${next.id}?seq=$seqArg") { popUpTo(entry.destination.id) { inclusive = true } }
                    }
                })
        }
        composable(
            "compare/{exp}?zone={zone}&sel={sel}",
            arguments = listOf(
                navArgument("zone") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("sel") { type = NavType.StringType; nullable = true; defaultValue = null },
            )
        ) { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone"))
            val sel = entry.arguments?.getString("sel")?.split('|')?.filter { it.isNotEmpty() }.orEmpty()
            CompareScreen(expId = exp, initialZone = zone, onBack = { entry.back() }, initialSelection = sel)
        }
        composable("photo/{exp}/{zone}/{name}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone")) ?: return@composable
            val name = entry.arguments?.getString("name").orEmpty()
            PhotoViewerScreen(expId = exp, zone = zone, initialName = name, onBack = { entry.back() },
                onCompare = { names -> entry.go("compare/$exp?zone=${zone.id}&sel=" + android.net.Uri.encode(names.joinToString("|"))) })
        }
        composable(
            "protocol/{exp}/{id}?from={from}",
            arguments = listOf(navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val id = entry.arguments?.getString("id").orEmpty()
            val from = entry.arguments?.getString("from")?.split('|')?.filter { it.isNotEmpty() }.orEmpty()
            com.photomed.app.ui.protocol.ProtocolEditorScreen(expId = exp, protocolId = id.takeIf { it != "new" }, fromPhotos = from,
                onBack = { entry.back() })
        }
        composable("settings") { entry -> com.photomed.app.ui.settings.SettingsScreen(onBack = { entry.back() }) }
        composable("about") { entry ->
            AboutScreen(onBack = { entry.back() }, onDoc = { entry.go("legal/${it.name}") })
        }
        composable("legal/{doc}") { entry ->
            val doc = LegalDoc.valueOf(entry.arguments?.getString("doc") ?: LegalDoc.PRIVACY.name)
            LegalDocScreen(doc, onBack = { entry.back() })
        }
    }
}
