package com.vega.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * @param speech spoken reply, or null for silent actions (pause, volume)
 * @param toast short on-screen confirmation shown when nothing is spoken
 * @param followUp Vega asked something and should open the mic again
 */
data class ActionResult(
    val ok: Boolean,
    val speech: String? = null,
    val toast: String? = null,
    val followUp: Boolean = false,
)

object Packages {
    const val YOUTUBE = "com.google.android.youtube"
    const val SPOTIFY = "com.spotify.music"
    const val MAPS = "com.google.android.apps.maps"
    const val WHATSAPP = "com.whatsapp"
}

/** Starting activities from the service is allowed because Vega holds the overlay permission. */
internal fun Context.launch(intent: Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun Context.isInstalled(packageName: String): Boolean = try {
    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

internal fun Context.has(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
