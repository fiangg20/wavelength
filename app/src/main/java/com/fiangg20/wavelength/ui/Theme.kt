package com.fiangg20.wavelength.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fiangg20.wavelength.data.Prefs

val LocalPrefs = staticCompositionLocalOf { Prefs() }

private fun ts(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight,
    spacing: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

fun expressiveTypography(f: FontFamily) = Typography(
    displayLarge = ts(f, 57, 64, FontWeight.Black, -1.0),
    displayMedium = ts(f, 45, 52, FontWeight.Black, -0.5),
    displaySmall = ts(f, 36, 44, FontWeight.ExtraBold, -0.25),
    headlineLarge = ts(f, 32, 40, FontWeight.ExtraBold),
    headlineMedium = ts(f, 28, 36, FontWeight.Bold),
    headlineSmall = ts(f, 24, 32, FontWeight.Bold),
    titleLarge = ts(f, 22, 28, FontWeight.Bold),
    titleMedium = ts(f, 16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = ts(f, 14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = ts(f, 16, 24, FontWeight.Normal, 0.4),
    bodyMedium = ts(f, 14, 20, FontWeight.Normal, 0.25),
    bodySmall = ts(f, 12, 16, FontWeight.Normal, 0.4),
    labelLarge = ts(f, 14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = ts(f, 12, 16, FontWeight.SemiBold, 0.5),
    labelSmall = ts(f, 11, 16, FontWeight.SemiBold, 0.5),
)

fun expressiveShapes(s: Float) = Shapes(
    extraSmall = RoundedCornerShape((6 * s).dp),
    small = RoundedCornerShape((12 * s).dp),
    medium = RoundedCornerShape((18 * s).dp),
    large = RoundedCornerShape((26 * s).dp),
    extraLarge = RoundedCornerShape((38 * s).dp),
)

/** Id of the optional custom font (res/font/app_font.ttf), or 0 when the file is missing. */
fun customFontId(context: android.content.Context): Int =
    context.resources.getIdentifier("app_font", "font", context.packageName)

/** Id of Google Sans Flex (res/font/google_sans_flex.ttf, fetched by the build), or 0 when it is missing. */
fun flexFontId(context: android.content.Context): Int =
    context.resources.getIdentifier("google_sans_flex", "font", context.packageName)

private val flexWeights = listOf(400, 500, 600, 700, 800, 900)

/** Google Sans Flex is a variable font: weight, width and roundness are axes, so one file gives every style. */
@OptIn(ExperimentalTextApi::class)
private fun flexFamily(id: Int, width: Float, round: Float): FontFamily =
    FontFamily(
        flexWeights.map { w ->
            Font(
                resId = id,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.width(width),
                    FontVariation.Setting("ROND", round),
                ),
            )
        }
    )

fun fontFamilyFor(context: android.content.Context, prefs: Prefs): FontFamily = when (prefs.font) {
    1 -> FontFamily.Default
    2 -> FontFamily.Serif
    3 -> FontFamily.Monospace
    4 -> {
        val id = customFontId(context)
        if (id != 0) FontFamily(Font(id)) else FontFamily.Default
    }
    else -> {
        val id = flexFontId(context)
        if (id != 0) flexFamily(id, prefs.fontWidth, prefs.fontRound) else FontFamily.Default
    }
}

@Composable
fun WavelengthTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current

    val scheme: ColorScheme = remember(
        prefs.hue, prefs.vibrancy, prefs.dynamicColor, prefs.pureBlack, dark, context,
    ) {
        var s = if (prefs.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            Oklch.scheme(prefs.hue, 0.15f * prefs.vibrancy, dark)
        }
        if (dark && prefs.pureBlack) s = s.asBlack()
        s
    }

    val typography = remember(prefs.font, prefs.fontWidth, prefs.fontRound, context) {
        expressiveTypography(fontFamilyFor(context, prefs))
    }
    val shapes = remember(prefs.corners) { expressiveShapes(prefs.cornerScale) }

    val activity = context as? ComponentActivity
    DisposableEffect(dark, activity) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    CompositionLocalProvider(LocalPrefs provides prefs) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
