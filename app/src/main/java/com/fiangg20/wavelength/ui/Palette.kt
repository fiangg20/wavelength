package com.fiangg20.wavelength.ui

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tonal palettes built in Oklch. Tone follows CIE L* so the usual Material tone numbers
 * (primary 40/80, containers 90/30, ...) keep their contrast guarantees.
 */
object Oklch {

    private fun toneToL(tone: Double): Double {
        val y = if (tone > 8.0) ((tone + 16.0) / 116.0).pow(3.0) else tone / 903.3
        return y.pow(1.0 / 3.0)
    }

    private fun linear(l: Double, a: Double, b: Double): DoubleArray {
        val l1 = l + 0.3963377774 * a + 0.2158037573 * b
        val m1 = l - 0.1055613458 * a - 0.0638541728 * b
        val s1 = l - 0.0894841775 * a - 1.2914855480 * b
        val lc = l1 * l1 * l1
        val mc = m1 * m1 * m1
        val sc = s1 * s1 * s1
        return doubleArrayOf(
            4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
            -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
            -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
        )
    }

    private fun inGamut(v: DoubleArray): Boolean =
        v[0] >= -0.0005 && v[0] <= 1.0005 &&
            v[1] >= -0.0005 && v[1] <= 1.0005 &&
            v[2] >= -0.0005 && v[2] <= 1.0005

    private fun encode(x: Double): Float {
        val c = x.coerceIn(0.0, 1.0)
        val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return v.toFloat().coerceIn(0f, 1f)
    }

    fun color(tone: Double, chroma: Double, hueDeg: Double): Color {
        val l = toneToL(tone)
        val h = hueDeg * Math.PI / 180.0
        val cosH = cos(h)
        val sinH = sin(h)
        var c = chroma
        var rgb = linear(l, c * cosH, c * sinH)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = c
            repeat(16) {
                val mid = (lo + hi) / 2.0
                if (inGamut(linear(l, mid * cosH, mid * sinH))) lo = mid else hi = mid
            }
            c = lo
            rgb = linear(l, c * cosH, c * sinH)
        }
        return Color(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]))
    }

    fun scheme(hue: Float, chroma: Float, dark: Boolean): ColorScheme {
        val h = hue.toDouble()
        val c = chroma.toDouble()
        val tertiaryHue = (h + 62.0) % 360.0
        val pc = c
        val sc = c * 0.32
        val tc = c * 0.55
        val nc = 0.006 + c * 0.035
        val nvc = 0.012 + c * 0.07

        fun p(t: Int) = color(t.toDouble(), pc, h)
        fun s(t: Int) = color(t.toDouble(), sc, h)
        fun ter(t: Int) = color(t.toDouble(), tc, tertiaryHue)
        fun n(t: Int) = color(t.toDouble(), nc, h)
        fun nv(t: Int) = color(t.toDouble(), nvc, h)

        return if (!dark) {
            lightColorScheme(
                primary = p(40),
                onPrimary = p(100),
                primaryContainer = p(90),
                onPrimaryContainer = p(10),
                inversePrimary = p(80),
                secondary = s(40),
                onSecondary = s(100),
                secondaryContainer = s(90),
                onSecondaryContainer = s(10),
                tertiary = ter(40),
                onTertiary = ter(100),
                tertiaryContainer = ter(90),
                onTertiaryContainer = ter(10),
                background = n(98),
                onBackground = n(10),
                surface = n(98),
                onSurface = n(10),
                surfaceVariant = nv(90),
                onSurfaceVariant = nv(30),
                surfaceTint = p(40),
                inverseSurface = n(20),
                inverseOnSurface = n(95),
                error = Color(0xFFB3261E),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFF9DEDC),
                onErrorContainer = Color(0xFF410E0B),
                outline = nv(50),
                outlineVariant = nv(80),
                scrim = Color(0xFF000000),
                surfaceBright = n(98),
                surfaceDim = n(87),
                surfaceContainerLowest = n(100),
                surfaceContainerLow = n(96),
                surfaceContainer = n(94),
                surfaceContainerHigh = n(92),
                surfaceContainerHighest = n(90),
            )
        } else {
            darkColorScheme(
                primary = p(80),
                onPrimary = p(20),
                primaryContainer = p(30),
                onPrimaryContainer = p(90),
                inversePrimary = p(40),
                secondary = s(80),
                onSecondary = s(20),
                secondaryContainer = s(30),
                onSecondaryContainer = s(90),
                tertiary = ter(80),
                onTertiary = ter(20),
                tertiaryContainer = ter(30),
                onTertiaryContainer = ter(90),
                background = n(6),
                onBackground = n(90),
                surface = n(6),
                onSurface = n(90),
                surfaceVariant = nv(30),
                onSurfaceVariant = nv(80),
                surfaceTint = p(80),
                inverseSurface = n(90),
                inverseOnSurface = n(20),
                error = Color(0xFFF2B8B5),
                onError = Color(0xFF601410),
                errorContainer = Color(0xFF8C1D18),
                onErrorContainer = Color(0xFFF9DEDC),
                outline = nv(60),
                outlineVariant = nv(30),
                scrim = Color(0xFF000000),
                surfaceBright = n(24),
                surfaceDim = n(6),
                surfaceContainerLowest = n(4),
                surfaceContainerLow = n(10),
                surfaceContainer = n(12),
                surfaceContainerHigh = n(17),
                surfaceContainerHighest = n(22),
            )
        }
    }
}

fun ColorScheme.asBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.45f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.55f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.65f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.75f),
)

data class Seed(val hue: Float, val chroma: Float)

private fun srgbToLinear(v: Double): Double =
    if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

/** Finds the dominant vivid hue of an image, weighted toward colourful pixels. */
fun dominantSeed(bmp: Bitmap): Seed? {
    return try {
        val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        var sumA = 0.0
        var sumB = 0.0
        var sumW = 0.0
        var sumC = 0.0
        for (p in px) {
            val r = srgbToLinear(((p shr 16) and 0xFF) / 255.0)
            val g = srgbToLinear(((p shr 8) and 0xFF) / 255.0)
            val b = srgbToLinear((p and 0xFF) / 255.0)
            val l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b).pow(1.0 / 3.0)
            val m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b).pow(1.0 / 3.0)
            val s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b).pow(1.0 / 3.0)
            val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
            val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
            val c = hypot(a, bb)
            val w = c * c
            sumA += a * w
            sumB += bb * w
            sumW += w
            sumC += c * w
        }
        if (sumW < 1e-4) {
            null
        } else {
            var hue = atan2(sumB, sumA) * 180.0 / Math.PI
            if (hue < 0) hue += 360.0
            Seed(hue.toFloat(), (sumC / sumW).toFloat())
        }
    } catch (e: Exception) {
        null
    }
}
