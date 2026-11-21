// ARCHIVO GENERADO por tools/md3/generate_theme.py — no editar a mano.
// Fuente: tokens oficiales de Material Design 3, Material Web (@material/web tokens v0_192).
// Paletas de color: las del logo (tools/md3/brand_palette.py).
@file:Suppress("unused", "MagicNumber")

package com.photomed.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** md.ref.palette: paletas tonales de referencia. */
object Md3Palette {
    val error0 = Color(0xFF000000)
    val error10 = Color(0xFF410E0B)
    val error20 = Color(0xFF601410)
    val error30 = Color(0xFF8C1D18)
    val error40 = Color(0xFFB3261E)
    val error50 = Color(0xFFDC362E)
    val error60 = Color(0xFFE46962)
    val error70 = Color(0xFFEC928E)
    val error80 = Color(0xFFF2B8B5)
    val error90 = Color(0xFFF9DEDC)
    val error95 = Color(0xFFFCEEEE)
    val error99 = Color(0xFFFFFBF9)
    val error100 = Color(0xFFFFFFFF)
    val neutral0 = Color(0xFF000000)
    val neutral4 = Color(0xFF04100F)
    val neutral6 = Color(0xFF0B1514)
    val neutral10 = Color(0xFF151D1C)
    val neutral12 = Color(0xFF192120)
    val neutral17 = Color(0xFF232C2A)
    val neutral20 = Color(0xFF293231)
    val neutral22 = Color(0xFF2E3635)
    val neutral24 = Color(0xFF323B3A)
    val neutral30 = Color(0xFF3F4947)
    val neutral40 = Color(0xFF57605F)
    val neutral50 = Color(0xFF6F7978)
    val neutral60 = Color(0xFF899391)
    val neutral70 = Color(0xFFA5ADAC)
    val neutral80 = Color(0xFFC1C8C7)
    val neutral87 = Color(0xFFD5DBDA)
    val neutral90 = Color(0xFFDEE4E3)
    val neutral92 = Color(0xFFE3E9E8)
    val neutral94 = Color(0xFFE9EFEE)
    val neutral95 = Color(0xFFECF2F1)
    val neutral96 = Color(0xFFEFF5F4)
    val neutral98 = Color(0xFFF5FAFA)
    val neutral99 = Color(0xFFF8FDFC)
    val neutral100 = Color(0xFFFFFFFF)
    val neutralVariant0 = Color(0xFF000000)
    val neutralVariant4 = Color(0xFF00110F)
    val neutralVariant6 = Color(0xFF001714)
    val neutralVariant10 = Color(0xFF0D1F1D)
    val neutralVariant12 = Color(0xFF112321)
    val neutralVariant17 = Color(0xFF1B2D2B)
    val neutralVariant20 = Color(0xFF223431)
    val neutralVariant22 = Color(0xFF263836)
    val neutralVariant24 = Color(0xFF2A3D3A)
    val neutralVariant30 = Color(0xFF384A48)
    val neutralVariant40 = Color(0xFF4F625F)
    val neutralVariant50 = Color(0xFF677B78)
    val neutralVariant60 = Color(0xFF829492)
    val neutralVariant70 = Color(0xFF9EAFAC)
    val neutralVariant80 = Color(0xFFBBC9C7)
    val neutralVariant87 = Color(0xFFD0DDDB)
    val neutralVariant90 = Color(0xFFD9E5E3)
    val neutralVariant92 = Color(0xFFDFEBE9)
    val neutralVariant94 = Color(0xFFE5F0EE)
    val neutralVariant95 = Color(0xFFE8F3F1)
    val neutralVariant96 = Color(0xFFEBF6F4)
    val neutralVariant98 = Color(0xFFF1FCFA)
    val neutralVariant99 = Color(0xFFF4FEFD)
    val neutralVariant100 = Color(0xFFFFFFFF)
    val primary0 = Color(0xFF000000)
    val primary10 = Color(0xFF00201D)
    val primary20 = Color(0xFF003733)
    val primary30 = Color(0xFF00504A)
    val primary40 = Color(0xFF006A63)
    val primary50 = Color(0xFF00867C)
    val primary60 = Color(0xFF21A197)
    val primary70 = Color(0xFF5ABBB1)
    val primary80 = Color(0xFF85D5CB)
    val primary90 = Color(0xFFADEFE7)
    val primary95 = Color(0xFFC1FCF4)
    val primary99 = Color(0xFFF2FFFD)
    val primary100 = Color(0xFFFFFFFF)
    val secondary0 = Color(0xFF000000)
    val secondary10 = Color(0xFF001D32)
    val secondary20 = Color(0xFF003352)
    val secondary30 = Color(0xFF004B74)
    val secondary40 = Color(0xFF026399)
    val secondary50 = Color(0xFF357CB3)
    val secondary60 = Color(0xFF5F95C8)
    val secondary70 = Color(0xFF83AFDD)
    val secondary80 = Color(0xFFA6CAF3)
    val secondary90 = Color(0xFFCFE4FF)
    val secondary95 = Color(0xFFE8F2FF)
    val secondary99 = Color(0xFFFAFCFF)
    val secondary100 = Color(0xFFFFFFFF)
    val tertiary0 = Color(0xFF000000)
    val tertiary10 = Color(0xFF001C3A)
    val tertiary20 = Color(0xFF00315E)
    val tertiary30 = Color(0xFF1F477B)
    val tertiary40 = Color(0xFF3D5F95)
    val tertiary50 = Color(0xFF5877B0)
    val tertiary60 = Color(0xFF7891C5)
    val tertiary70 = Color(0xFF97ABDA)
    val tertiary80 = Color(0xFFB6C6F0)
    val tertiary90 = Color(0xFFD8E2FF)
    val tertiary95 = Color(0xFFECF0FF)
    val tertiary99 = Color(0xFFFBFCFF)
    val tertiary100 = Color(0xFFFFFFFF)
}

/** md.sys.color (light). */
val Md3LightColorScheme: ColorScheme = lightColorScheme(
    background = Md3Palette.neutral98,
    error = Md3Palette.error40,
    errorContainer = Md3Palette.error90,
    inverseOnSurface = Md3Palette.neutral95,
    inversePrimary = Md3Palette.primary80,
    inverseSurface = Md3Palette.neutral20,
    onBackground = Md3Palette.neutral10,
    onError = Md3Palette.error100,
    onErrorContainer = Md3Palette.error10,
    onPrimary = Md3Palette.primary100,
    onPrimaryContainer = Md3Palette.primary10,
    onSecondary = Md3Palette.secondary100,
    onSecondaryContainer = Md3Palette.secondary10,
    onSurface = Md3Palette.neutral10,
    onSurfaceVariant = Md3Palette.neutralVariant30,
    onTertiary = Md3Palette.tertiary100,
    onTertiaryContainer = Md3Palette.tertiary10,
    outline = Md3Palette.neutralVariant50,
    outlineVariant = Md3Palette.neutralVariant80,
    primary = Md3Palette.primary40,
    primaryContainer = Md3Palette.primary90,
    scrim = Md3Palette.neutral0,
    secondary = Md3Palette.secondary40,
    secondaryContainer = Md3Palette.secondary90,
    surface = Md3Palette.neutral98,
    surfaceBright = Md3Palette.neutral98,
    surfaceContainer = Md3Palette.neutral94,
    surfaceContainerHigh = Md3Palette.neutral92,
    surfaceContainerHighest = Md3Palette.neutral90,
    surfaceContainerLow = Md3Palette.neutral96,
    surfaceContainerLowest = Md3Palette.neutral100,
    surfaceDim = Md3Palette.neutral87,
    surfaceTint = Md3Palette.primary40,
    surfaceVariant = Md3Palette.neutralVariant90,
    tertiary = Md3Palette.tertiary40,
    tertiaryContainer = Md3Palette.tertiary90,
)

/** md.sys.color (dark). */
val Md3DarkColorScheme: ColorScheme = darkColorScheme(
    background = Md3Palette.neutral6,
    error = Md3Palette.error80,
    errorContainer = Md3Palette.error30,
    inverseOnSurface = Md3Palette.neutral20,
    inversePrimary = Md3Palette.primary40,
    inverseSurface = Md3Palette.neutral90,
    onBackground = Md3Palette.neutral90,
    onError = Md3Palette.error20,
    onErrorContainer = Md3Palette.error90,
    onPrimary = Md3Palette.primary20,
    onPrimaryContainer = Md3Palette.primary90,
    onSecondary = Md3Palette.secondary20,
    onSecondaryContainer = Md3Palette.secondary90,
    onSurface = Md3Palette.neutral90,
    onSurfaceVariant = Md3Palette.neutralVariant80,
    onTertiary = Md3Palette.tertiary20,
    onTertiaryContainer = Md3Palette.tertiary90,
    outline = Md3Palette.neutralVariant60,
    outlineVariant = Md3Palette.neutralVariant30,
    primary = Md3Palette.primary80,
    primaryContainer = Md3Palette.primary30,
    scrim = Md3Palette.neutral0,
    secondary = Md3Palette.secondary80,
    secondaryContainer = Md3Palette.secondary30,
    surface = Md3Palette.neutral6,
    surfaceBright = Md3Palette.neutral24,
    surfaceContainer = Md3Palette.neutral12,
    surfaceContainerHigh = Md3Palette.neutral17,
    surfaceContainerHighest = Md3Palette.neutral22,
    surfaceContainerLow = Md3Palette.neutral10,
    surfaceContainerLowest = Md3Palette.neutral4,
    surfaceDim = Md3Palette.neutral6,
    surfaceTint = Md3Palette.primary80,
    surfaceVariant = Md3Palette.neutralVariant30,
    tertiary = Md3Palette.tertiary80,
    tertiaryContainer = Md3Palette.tertiary30,
)

/** Roles de color de M3 que ColorScheme de Compose aún no expone (fixed, shadow). */
data class Md3ExtendedColors(
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val shadow: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
)

val Md3LightExtendedColors = Md3ExtendedColors(
    onPrimaryFixed = Md3Palette.primary10,
    onPrimaryFixedVariant = Md3Palette.primary30,
    onSecondaryFixed = Md3Palette.secondary10,
    onSecondaryFixedVariant = Md3Palette.secondary30,
    onTertiaryFixed = Md3Palette.tertiary10,
    onTertiaryFixedVariant = Md3Palette.tertiary30,
    primaryFixed = Md3Palette.primary90,
    primaryFixedDim = Md3Palette.primary80,
    secondaryFixed = Md3Palette.secondary90,
    secondaryFixedDim = Md3Palette.secondary80,
    shadow = Md3Palette.neutral0,
    tertiaryFixed = Md3Palette.tertiary90,
    tertiaryFixedDim = Md3Palette.tertiary80,
)

val Md3DarkExtendedColors = Md3ExtendedColors(
    onPrimaryFixed = Md3Palette.primary10,
    onPrimaryFixedVariant = Md3Palette.primary30,
    onSecondaryFixed = Md3Palette.secondary10,
    onSecondaryFixedVariant = Md3Palette.secondary30,
    onTertiaryFixed = Md3Palette.tertiary10,
    onTertiaryFixedVariant = Md3Palette.tertiary30,
    primaryFixed = Md3Palette.primary90,
    primaryFixedDim = Md3Palette.primary80,
    secondaryFixed = Md3Palette.secondary90,
    secondaryFixedDim = Md3Palette.secondary80,
    shadow = Md3Palette.neutral0,
    tertiaryFixed = Md3Palette.tertiary90,
    tertiaryFixedDim = Md3Palette.tertiary80,
)

/** md.sys.typescale (tipografía Roboto del sistema). */
val Md3Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 57.0.sp,
        lineHeight = 64.0.sp,
        letterSpacing = -0.25.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 45.0.sp,
        lineHeight = 52.0.sp,
        letterSpacing = 0.0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 36.0.sp,
        lineHeight = 44.0.sp,
        letterSpacing = 0.0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 32.0.sp,
        lineHeight = 40.0.sp,
        letterSpacing = 0.0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 28.0.sp,
        lineHeight = 36.0.sp,
        letterSpacing = 0.0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 24.0.sp,
        lineHeight = 32.0.sp,
        letterSpacing = 0.0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 22.0.sp,
        lineHeight = 28.0.sp,
        letterSpacing = 0.0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 16.0.sp,
        lineHeight = 24.0.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.0.sp,
        lineHeight = 20.0.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 16.0.sp,
        lineHeight = 24.0.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 14.0.sp,
        lineHeight = 20.0.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 12.0.sp,
        lineHeight = 16.0.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.0.sp,
        lineHeight = 20.0.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 12.0.sp,
        lineHeight = 16.0.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 11.0.sp,
        lineHeight = 16.0.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** md.sys.shape. */
object Md3Corner {
    val none = 0.0.dp
    val extraSmall = 4.0.dp
    val small = 8.0.dp
    val medium = 12.0.dp
    val large = 16.0.dp
    val extraLarge = 28.0.dp
}

val Md3Shapes = Shapes(
    extraSmall = RoundedCornerShape(Md3Corner.extraSmall),
    small = RoundedCornerShape(Md3Corner.small),
    medium = RoundedCornerShape(Md3Corner.medium),
    large = RoundedCornerShape(Md3Corner.large),
    extraLarge = RoundedCornerShape(Md3Corner.extraLarge),
)

/** md.sys.elevation (dp). */
object Md3Elevation {
    val level0 = 0.0.dp
    val level1 = 1.0.dp
    val level2 = 3.0.dp
    val level3 = 6.0.dp
    val level4 = 8.0.dp
    val level5 = 12.0.dp
}

/** md.sys.motion: duraciones (ms) y curvas de easing. */
object Md3Motion {
    const val durationExtraLong1 = 700
    const val durationExtraLong2 = 800
    const val durationExtraLong3 = 900
    const val durationExtraLong4 = 1000
    const val durationLong1 = 450
    const val durationLong2 = 500
    const val durationLong3 = 550
    const val durationLong4 = 600
    const val durationMedium1 = 250
    const val durationMedium2 = 300
    const val durationMedium3 = 350
    const val durationMedium4 = 400
    const val durationShort1 = 50
    const val durationShort2 = 100
    const val durationShort3 = 150
    const val durationShort4 = 200
    val easingEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val easingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val easingLegacy = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val easingLegacyAccelerate = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val easingLegacyDecelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val easingLinear = CubicBezierEasing(0f, 0f, 1f, 1f)
    val easingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easingStandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val easingStandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
}

/** md.sys.state: opacidades de las capas de estado. */
object Md3State {
    const val dragged = 0.16f
    const val focus = 0.12f
    const val hover = 0.08f
    const val pressed = 0.12f
}

/** Retícula base de 4/8 dp, objetivo táctil (48 dp) y separación mínima (8 dp). */
object Md3Spacing {
    val s0 = 0.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s7 = 28.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s14 = 56.dp
    val s16 = 64.dp
    val touchTarget = 48.dp
    val touchTargetGap = 8.dp
    /** Margen de ventana compacta (< 600 dp) y media/expandida. */
    val marginCompact = 16.dp
    val marginMedium = 24.dp
}
