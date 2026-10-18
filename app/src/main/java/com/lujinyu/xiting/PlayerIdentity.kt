package com.lujinyu.xiting

import android.content.Context
import android.graphics.drawable.Drawable

/** Normalize a PackageManager label without exposing an internal package identifier. */
internal fun playerDisplayName(packageName: String, label: CharSequence?, fallback: String): String =
    label?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != packageName } ?: fallback

/** All player surfaces share the same application-name lookup and localized fallback. */
internal fun Context.playerLabel(packageName: String): String {
    val label = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }
    return playerDisplayName(packageName, label, getString(R.string.player_unknown_name))
}

/** A missing icon leaves the caller's neutral player icon in place. */
internal fun Context.playerIcon(packageName: String): Drawable? = try {
    packageManager.getApplicationIcon(packageName)
} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
    null
} catch (_: SecurityException) {
    null
}
