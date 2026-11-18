package com.photomed.app.ui

import androidx.compose.runtime.Composable
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

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "expedientes") {
        composable("expedientes") {
            ExpedientesScreen(onOpen = { nav.navigate("expediente/$it") })
        }
        composable("expediente/{exp}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            ExpedienteScreen(
                expId = exp,
                onBack = { nav.popBackStack() },
                onZone = { zone -> nav.navigate("zone/$exp/${zone.id}") },
                onCompare = { nav.navigate("compare/$exp") },
            )
        }
        composable("zone/{exp}/{zone}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone")) ?: return@composable
            ZoneGalleryScreen(
                expId = exp,
                zone = zone,
                onBack = { nav.popBackStack() },
                onCamera = { nav.navigate("camera/$exp/${zone.id}") },
                onCompare = { nav.navigate("compare/$exp?zone=${zone.id}") },
                onPhoto = { photo -> nav.navigate("photo/$exp/${zone.id}/${photo.file.name}") },
            )
        }
        composable("camera/{exp}/{zone}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone")) ?: return@composable
            CameraScreen(expId = exp, zone = zone, onBack = { nav.popBackStack() })
        }
        composable(
            "compare/{exp}?zone={zone}",
            arguments = listOf(navArgument("zone") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone"))
            CompareScreen(expId = exp, initialZone = zone, onBack = { nav.popBackStack() })
        }
        composable("photo/{exp}/{zone}/{file}") { entry ->
            val exp = entry.arguments?.getString("exp").orEmpty()
            val zone = Zone.fromId(entry.arguments?.getString("zone")) ?: return@composable
            val file = entry.arguments?.getString("file").orEmpty()
            PhotoViewerScreen(expId = exp, zone = zone, fileName = file, onBack = { nav.popBackStack() })
        }
    }
}
