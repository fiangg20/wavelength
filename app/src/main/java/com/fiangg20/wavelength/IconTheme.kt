package com.fiangg20.wavelength

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Keeps the launcher icon colour in step with the accent. Android cannot recolour an icon on the
 * fly, so the manifest has one alias per hue (and one that follows the wallpaper palette) and
 * exactly one of them is enabled at a time.
 */
object IconTheme {
    private const val DYNAMIC = "IconDynamic"

    private val hueNames: List<String> = (0 until 12).map {
        String.format(Locale.US, "IconH%03d", 15 + it * 30)
    }
    private val allNames: List<String> = listOf(DYNAMIC) + hueNames

    private fun component(context: Context, name: String) =
        ComponentName(context.packageName, "com.fiangg20.wavelength.$name")

    private fun isOn(pm: PackageManager, context: Context, name: String): Boolean =
        when (pm.getComponentEnabledSetting(component(context, name))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> name == DYNAMIC // manifest default
        }

    fun apply(context: Context, hue: Float, dynamic: Boolean, adaptive: Boolean) {
        try {
            val pm = context.packageManager
            val target = when {
                !adaptive -> DYNAMIC
                dynamic && Build.VERSION.SDK_INT >= 31 -> DYNAMIC
                else -> {
                    val idx = Math.floorMod(((hue - 15f) / 30f).roundToInt(), 12)
                    hueNames[idx]
                }
            }
            val current = allNames.firstOrNull { isOn(pm, context, it) }
            if (current == target) return

            // Enable the new icon first so the app never has no launcher entry.
            pm.setComponentEnabledSetting(
                component(context, target),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (name in allNames) {
                if (name != target && isOn(pm, context, name)) {
                    pm.setComponentEnabledSetting(
                        component(context, name),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        } catch (e: Exception) {
            // Some launchers refuse the change; the current icon simply stays.
        }
    }
}
